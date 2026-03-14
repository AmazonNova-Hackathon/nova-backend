"""
Lambda 3: Insights Engine
Handler: app.lambda_handler

Routes handled:
  EventBridge (cron)          → run_all_families() daily proactive insights
  POST /insights/generate     → on-demand generation for a specific family
  GET  /insights              → list insights for a family (+ optional memberId filter)
  GET  /followups             → list follow-ups for a family (+ optional memberId filter)
  PATCH /insights/{insightId} → mark insight as read
  PATCH /followups/{followUpId} → accept or dismiss a follow-up
"""
import _bootstrap  # noqa: F401 — must be first

import json
import traceback
from decimal import Decimal
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext

from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.config import TABLE_NAME
from lambdas.insights_engine.insight_service import run_all_families, run_for_family, generate_insights_for_member

logger = Logger(service="insights-engine")
repo = DynamoRepository(TABLE_NAME)


def _decimal_default(obj):
    if isinstance(obj, Decimal):
        return int(obj) if obj % 1 == 0 else float(obj)
    raise TypeError


def _ok(body: dict) -> dict:
    return {
        "statusCode": 200,
        "headers": {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"},
        "body": json.dumps(body, default=_decimal_default),
    }


def _err(status: int, code: str, msg: str) -> dict:
    return {
        "statusCode": status,
        "headers": {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"},
        "body": json.dumps({"error": {"code": code, "message": msg}}),
    }


def _qs(event: dict, key: str):
    return (event.get("queryStringParameters") or {}).get(key)


@logger.inject_lambda_context(log_event=False)
def lambda_handler(event: dict, context: LambdaContext) -> dict:
    # ------------------------------------------------------------------ Cron
    if event.get("source") == "aws.events" or "detail-type" in event:
        logger.info("EventBridge cron triggered — running for all families")
        try:
            result = run_all_families()
            logger.info("Cron run complete", extra=result)
            return {"statusCode": 200, "body": json.dumps(result)}
        except Exception:
            logger.exception("Cron run failed")
            return {"statusCode": 500, "body": json.dumps({"error": "cron failed"})}

    # ------------------------------------------------------------------ HTTP
    method = event.get("httpMethod", "")
    path = event.get("path", "")
    path_params = event.get("pathParameters") or {}

    try:
        # POST /insights/generate — on-demand generation for one familyId
        if method == "POST" and path == "/insights/generate":
            body = json.loads(event.get("body") or "{}")
            family_id = body.get("familyId")
            member_id = body.get("memberId")
            if not family_id:
                return _err(400, "MISSING_FAMILY_ID", "familyId is required in the request body")

            if member_id:
                member = repo.get_member(family_id, member_id)
                if not member:
                    return _err(404, "MEMBER_NOT_FOUND", f"Member {member_id} not found")
                ins, fus = generate_insights_for_member(family_id, member)
                result = {"insightsGenerated": len(ins), "followupsGenerated": len(fus)}
            else:
                result = run_for_family(family_id)

            return _ok(result)

        # GET /insights — list insights for a family
        elif method == "GET" and path == "/insights":
            family_id = _qs(event, "familyId")
            member_id = _qs(event, "memberId")
            if not family_id:
                return _err(400, "MISSING_FAMILY_ID", "familyId query parameter is required")
            insights = repo.get_insights(family_id, member_id)
            # Sort: unread first, then by generatedAt desc
            insights.sort(key=lambda i: (i.get("read", False), i.get("generatedAt", "")), reverse=False)
            return _ok({"insights": insights, "total": len(insights)})

        # GET /followups — list follow-ups for a family
        elif method == "GET" and path == "/followups":
            family_id = _qs(event, "familyId")
            member_id = _qs(event, "memberId")
            if not family_id:
                return _err(400, "MISSING_FAMILY_ID", "familyId query parameter is required")
            followups = repo.get_followups(family_id, member_id)
            # Filter pending only by default unless ?status=all is passed
            status_filter = _qs(event, "status")
            if status_filter != "all":
                followups = [f for f in followups if f.get("status") == "pending"]
            return _ok({"followups": followups, "total": len(followups)})

        # PATCH /insights/{insightId} — mark as read or update
        elif method == "PATCH" and "/insights/" in path and path_params.get("insightId"):
            insight_id = path_params["insightId"]
            body = json.loads(event.get("body") or "{}")
            family_id = body.get("familyId")
            member_id = body.get("memberId")
            generated_at = body.get("generatedAt")
            updates = {k: v for k, v in body.items()
                       if k not in ("familyId", "memberId", "generatedAt", "id")}
            if not all([family_id, member_id, generated_at]):
                return _err(400, "MISSING_FIELDS", "familyId, memberId and generatedAt are required")
            if not updates:
                return _err(400, "NO_UPDATES", "No fields to update were provided")
            try:
                repo.update_insight(family_id, member_id, generated_at, insight_id, updates)
            except ValueError as e:
                return _err(404, "NOT_FOUND", str(e))
            return _ok({"updated": True, "insightId": insight_id})

        # PATCH /followups/{followUpId} — accept or dismiss
        elif method == "PATCH" and "/followups/" in path and path_params.get("followUpId"):
            followup_id = path_params["followUpId"]
            body = json.loads(event.get("body") or "{}")
            family_id = body.get("familyId")
            member_id = body.get("memberId")
            suggested_date = body.get("suggestedDate")
            updates = {k: v for k, v in body.items()
                       if k not in ("familyId", "memberId", "suggestedDate", "id")}
            if not all([family_id, member_id, suggested_date]):
                return _err(400, "MISSING_FIELDS", "familyId, memberId and suggestedDate are required")
            if not updates:
                return _err(400, "NO_UPDATES", "No fields to update were provided")
            repo.update_followup(family_id, member_id, suggested_date, followup_id, updates)
            return _ok({"updated": True, "followUpId": followup_id})

        else:
            return _err(404, "NOT_FOUND", f"Route not found: {method} {path}")

    except Exception:
        logger.exception("Unhandled error in insights handler", extra={"path": path, "method": method})
        return _err(500, "INTERNAL_ERROR", "An unexpected error occurred")
