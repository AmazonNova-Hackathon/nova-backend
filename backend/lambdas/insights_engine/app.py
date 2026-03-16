"""
Lambda 3: Insights Engine
Handler: app.lambda_handler

Routes handled:
  EventBridge (cron)          → run_all_families() daily proactive insights
  POST /families/{fid}/members/{mid}/insights/generate     → on-demand generation
  GET  /families/{fid}/members/{mid}/insights              → list insights
  GET  /families/{fid}/members/{mid}/followups             → list follow-ups
  PATCH /families/{fid}/members/{mid}/insights/{insightId} → mark as read
  PATCH /families/{fid}/members/{mid}/followups/{followUpId} → accept or dismiss
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


from lambdas.shared.utils import json_dumps

def _ok(body: dict) -> dict:
    return {
        "statusCode": 200,
        "headers": {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"},
        "body": json_dumps(body),
    }

def _err(status: int, code: str, msg: str) -> dict:
    return {
        "statusCode": status,
        "headers": {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"},
        "body": json_dumps({"error": {"code": code, "message": msg}}),
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
        family_id = path_params.get("familyId")
        member_id = path_params.get("memberId")
        insight_id = path_params.get("insightId")
        followup_id = path_params.get("followUpId")

        # POST /families/{fid}/members/{mid}/insights/generate
        if method == "POST" and path.endswith("/insights/generate"):
            if not family_id or not member_id:
                return _err(400, "MISSING_PARAMS", "familyId and memberId are required in the path")

            member = repo.get_member(family_id, member_id)
            if not member:
                return _err(404, "MEMBER_NOT_FOUND", f"Member {member_id} not found")
            ins, fus = generate_insights_for_member(family_id, member)
            result = {"insightsGenerated": len(ins), "followupsGenerated": len(fus)}
            return _ok(result)

        # GET /families/{fid}/members/{mid}/insights
        elif method == "GET" and path.endswith("/insights"):
            if not family_id or not member_id:
                return _err(400, "MISSING_PARAMS", "familyId and memberId are required in the path")
            insights = repo.get_insights(family_id, member_id)
            # Sort: unread first, then by generatedAt desc
            insights.sort(key=lambda i: (not i.get("read", False), i.get("generatedAt", "")), reverse=True)
            return _ok({"insights": insights, "total": len(insights)})

        # GET /families/{fid}/members/{mid}/followups
        elif method == "GET" and path.endswith("/followups"):
            if not family_id or not member_id:
                return _err(400, "MISSING_PARAMS", "familyId and memberId are required in the path")
            followups = repo.get_followups(family_id, member_id)
            # Filter pending only by default unless ?status=all is passed
            status_filter = _qs(event, "status")
            if status_filter != "all":
                followups = [f for f in followups if f.get("status") == "pending"]
            return _ok({"followups": followups, "total": len(followups)})

        # PATCH /families/{fid}/members/{mid}/insights/{insightId}
        elif method == "PATCH" and insight_id:
            body = json.loads(event.get("body") or "{}")
            generated_at = body.get("generatedAt")
            updates = {k: v for k, v in body.items()
                       if k not in ("familyId", "memberId", "generatedAt", "id")}
            if not generated_at:
                return _err(400, "MISSING_FIELDS", "generatedAt is required in body")
            if not updates:
                return _err(400, "NO_UPDATES", "No fields to update were provided")
            try:
                repo.update_insight(family_id, member_id, generated_at, insight_id, updates)
            except ValueError as e:
                return _err(404, "NOT_FOUND", str(e))
            return _ok({"updated": True, "insightId": insight_id})

        # PATCH /families/{fid}/members/{mid}/followups/{followUpId}
        elif method == "PATCH" and followup_id:
            body = json.loads(event.get("body") or "{}")
            suggested_date = body.get("suggestedDate")
            updates = {k: v for k, v in body.items()
                       if k not in ("familyId", "memberId", "suggestedDate", "id")}
            if not suggested_date:
                return _err(400, "MISSING_FIELDS", "suggestedDate is required in body")
            if not updates:
                return _err(400, "NO_UPDATES", "No fields to update were provided")
            repo.update_followup(family_id, member_id, suggested_date, followup_id, updates)
            return _ok({"updated": True, "followUpId": followup_id})

        else:
            return _err(404, "NOT_FOUND", f"Route not found: {method} {path}")

    except Exception:
        logger.exception("Unhandled error in insights handler", extra={"path": path, "method": method})
        return _err(500, "INTERNAL_ERROR", "An unexpected error occurred")
