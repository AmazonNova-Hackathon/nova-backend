"""
Lambda 5: Family Management
Handler: app.lambda_handler

Routes:
  POST /families
  POST /families/{familyId}/members
  GET  /families/{familyId}/members
"""
import _bootstrap  # noqa: F401

import json
import uuid
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext
from pydantic import ValidationError

from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.config import TABLE_NAME
from lambdas.shared.models.fhir import Family, Member

logger = Logger(service="family-management")
repo = DynamoRepository(TABLE_NAME)

def _ok(body: dict) -> dict:
    return {
        "statusCode": 200,
        "headers": {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"},
        "body": json.dumps(body),
    }

def _err(status: int, code: str, msg: str) -> dict:
    return {
        "statusCode": status,
        "headers": {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"},
        "body": json.dumps({"error": {"code": code, "message": msg}}),
    }

@logger.inject_lambda_context(log_event=False)
def lambda_handler(event: dict, context: LambdaContext) -> dict:
    method = event.get("httpMethod", "")
    path = event.get("path", "")
    path_params = event.get("pathParameters") or {}

    try:
        # POST /families
        if method == "POST" and path == "/families":
            body = json.loads(event.get("body") or "{}")
            family_id = str(uuid.uuid4())
            family_name = body.get("name") or "My Family"
            
            family = Family(id=family_id, name=family_name)
            repo.put_family(family)
            
            logger.info(f"Created family {family_id}")
            return _ok(family.model_dump())

        # POST /families/{familyId}/members
        elif method == "POST" and path_params.get("familyId") and "members" in path:
            family_id = path_params["familyId"]
            body = json.loads(event.get("body") or "{}")
            
            member_id = str(uuid.uuid4())
            member = Member(
                id=member_id,
                familyId=family_id,
                name=body.get("name", "Unknown"),
                age=body.get("age"),
                gender=body.get("gender"),
                relationship=body.get("relationship", "Other")
            )
            repo.put_member(member)
            
            logger.info(f"Created member {member_id} for family {family_id}")
            return _ok(member.model_dump())

        # GET /families/{familyId}/members
        elif method == "GET" and path_params.get("familyId") and "members" in path:
            family_id = path_params["familyId"]
            members = repo.get_family_members(family_id)
            return _ok({"members": members, "count": len(members)})

        else:
            return _err(404, "NOT_FOUND", f"Route not found: {method} {path}")

    except ValidationError as e:
        return _err(400, "VALIDATION_ERROR", str(e))
    except Exception:
        logger.exception("Unexpected error in family management")
        return _err(500, "INTERNAL_ERROR", "An unexpected error occurred")
