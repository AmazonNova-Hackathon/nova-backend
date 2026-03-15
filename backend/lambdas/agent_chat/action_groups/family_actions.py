"""
Action Group Lambda: chetana-action-family
Functions: getFamilyDashboard, getMemberDetail, getAbnormals

Invoked directly by the Bedrock Agent when it needs a high-level view of the
family unit — member roster, individual profiles, or a filtered list of
abnormal observations for a specific member.
"""
import _bootstrap  # noqa: F401 — must be first, fixes sys.path for shared imports

import json
from decimal import Decimal
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext

from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.config import TABLE_NAME
from lambdas.shared.utils import extract_param

logger = Logger(service="action-family")
repo = DynamoRepository(TABLE_NAME)


def _decimal_default(obj):
    """JSON serializer for Decimal values returned by the DynamoDB boto3 resource."""
    if isinstance(obj, Decimal):
        return int(obj) if obj % 1 == 0 else float(obj)
    raise TypeError(f"Object of type {type(obj)} is not JSON serializable")


def _build_response(action_group: str, function: str, result: dict) -> dict:
    return {
        "messageVersion": "1.0",
        "response": {
            "actionGroup": action_group,
            "function": function,
            "functionResponse": {
                "responseBody": {
                    "TEXT": {"body": json.dumps(result, default=_decimal_default)}
                }
            },
        },
    }


@logger.inject_lambda_context(log_event=True)
def lambda_handler(event: dict, context: LambdaContext) -> dict:
    action_group = event.get("actionGroup")
    function = event.get("function")

    logger.info("Invoked action", extra={"function": function, "actionGroup": action_group})

    family_id = extract_param(event, "familyId")

    try:
        if function == "getFamilyDashboard":
            members = repo.get_family_members(family_id)
            # Enrich each member with their abnormal observation count for the agent
            enriched_members = []
            for member in members:
                member_id = member.get("id")
                obs = repo.get_observations(family_id, member_id)
                abnormal_count = sum(1 for o in obs if o.get("isAbnormal"))
                enriched_members.append({
                    **member,
                    "abnormalObservationCount": abnormal_count,
                    "totalObservationCount": len(obs),
                })
            result = {
                "familyId": family_id,
                "memberCount": len(enriched_members),
                "members": enriched_members,
            }

        elif function == "getMemberDetail":
            member_id = extract_param(event, "memberId")
            member = repo.get_member(family_id, member_id)
            if member is None:
                result = {"error": f"Member {member_id} not found in family {family_id}"}
            else:
                reports = repo.get_reports(family_id, member_id)
                obs = repo.get_observations(family_id, member_id)
                abnormal_obs = [o for o in obs if o.get("isAbnormal")]
                # Sort reports by date to find the most recent one
                sorted_reports = sorted(reports, key=lambda r: r.get("date", ""), reverse=True)
                result = {
                    "member": member,
                    "totalReports": len(reports),
                    "mostRecentReportDate": sorted_reports[0].get("date") if sorted_reports else None,
                    "totalObservations": len(obs),
                    "abnormalObservationCount": len(abnormal_obs),
                }

        elif function == "getAbnormals":
            member_id = extract_param(event, "memberId")
            all_obs = repo.get_observations(family_id, member_id)
            abnormals = [o for o in all_obs if o.get("isAbnormal")]
            # Sort by date descending so agent sees the most recent issues first
            abnormals.sort(key=lambda o: o.get("date", ""), reverse=True)
            result = {
                "familyId": family_id,
                "memberId": member_id,
                "abnormalObservations": abnormals,
                "abnormalCount": len(abnormals),
            }

        else:
            logger.warning("Unknown function requested", extra={"function": function})
            result = {"error": f"Unknown function: {function}"}

    except Exception as e:
        logger.exception("Error processing action")
        result = {"error": str(e)}

    return _build_response(action_group, function, result)
