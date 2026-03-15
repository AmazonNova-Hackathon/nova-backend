"""
Action Group Lambda: chetana-action-trends
Functions: computeTrend, compareMembers, detectPatterns

Invoked directly by the Bedrock Agent when it needs time-series analysis,
cross-member comparison, or pattern detection across a member's observations.
"""
import _bootstrap  # noqa: F401 — must be first, fixes sys.path for shared imports

import json
from decimal import Decimal
from collections import defaultdict
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext

from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.config import TABLE_NAME
from lambdas.shared.utils import extract_param

logger = Logger(service="action-trends")
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


def _compute_trend_direction(values: list) -> str:
    """
    Compute a simple trend direction from the last 3 numeric values.
    Returns 'rising', 'falling', or 'stable'.
    Requires at least 2 values; returns 'insufficient_data' otherwise.
    """
    if len(values) < 2:
        return "insufficient_data"
    recent = list(values)[-3:]  # last 3 readings
    deltas = [recent[i + 1] - recent[i] for i in range(len(recent) - 1)]
    avg_delta = sum(deltas) / len(deltas)
    if avg_delta > 0.5:
        return "rising"
    elif avg_delta < -0.5:
        return "falling"
    return "stable"


@logger.inject_lambda_context(log_event=True)
def lambda_handler(event: dict, context: LambdaContext) -> dict:
    action_group = event.get("actionGroup")
    function = event.get("function")

    logger.info("Invoked action", extra={"function": function, "actionGroup": action_group})

    family_id = extract_param(event, "familyId")

    try:
        if function == "computeTrend":
            member_id = extract_param(event, "memberId")
            loinc_code = extract_param(event, "loincCode")
            obs = repo.get_observations(family_id, member_id, loinc_code)
            obs.sort(key=lambda x: x.get("date", ""))

            numeric_values = []
            for o in obs:
                try:
                    numeric_values.append(float(o["value"]))
                except (KeyError, TypeError, ValueError):
                    pass

            trend_direction = _compute_trend_direction(numeric_values)

            result = {
                "familyId": family_id,
                "memberId": member_id,
                "loincCode": loinc_code,
                "dataPoints": len(obs),
                "trendDirection": trend_direction,
                "trend": obs,
                "firstValue": obs[0].get("value") if obs else None,
                "latestValue": obs[-1].get("value") if obs else None,
                "unit": obs[-1].get("unit") if obs else None,
            }

        elif function == "compareMembers":
            loinc_code = extract_param(event, "loincCode")
            members_raw = extract_param(event, "memberIds")  # comma-separated
            member_ids = [m.strip() for m in members_raw.split(",")] if members_raw else []

            comparison = {}
            for m_id in member_ids:
                obs = repo.get_observations(family_id, m_id, loinc_code)
                obs.sort(key=lambda x: x.get("date", ""))
                if obs:
                    latest = obs[-1]
                    comparison[m_id] = {
                        "latestValue": latest.get("value"),
                        "unit": latest.get("unit"),
                        "date": latest.get("date"),
                        "isAbnormal": latest.get("isAbnormal"),
                        "interpretation": latest.get("interpretation"),
                        "normalLow": latest.get("normalLow"),
                        "normalHigh": latest.get("normalHigh"),
                    }
                else:
                    comparison[m_id] = {"message": "No data available"}

            result = {
                "familyId": family_id,
                "loincCode": loinc_code,
                "memberCount": len(member_ids),
                "comparison": comparison,
            }

        elif function == "detectPatterns":
            member_id = extract_param(event, "memberId")
            all_obs = repo.get_observations(family_id, member_id)

            # Group observations by LOINC code for per-test trend analysis
            by_loinc: dict[str, list] = defaultdict(list)
            for o in all_obs:
                by_loinc[o.get("loincCode", "unknown")].append(o)

            patterns = []
            for code, obs_list in by_loinc.items():
                obs_list.sort(key=lambda x: x.get("date", ""))
                numeric_values = []
                for o in obs_list:
                    try:
                        numeric_values.append(float(o["value"]))
                    except (KeyError, TypeError, ValueError):
                        pass

                has_abnormal = any(o.get("isAbnormal") for o in obs_list)
                trend_direction = _compute_trend_direction(numeric_values)

                patterns.append({
                    "loincCode": code,
                    "testName": obs_list[-1].get("name", code),
                    "dataPoints": len(obs_list),
                    "trendDirection": trend_direction,
                    "hasAbnormal": has_abnormal,
                    "latestValue": obs_list[-1].get("value") if obs_list else None,
                    "unit": obs_list[-1].get("unit") if obs_list else None,
                    "latestDate": obs_list[-1].get("date") if obs_list else None,
                })

            # Surface abnormal + rising/falling tests first
            patterns.sort(key=lambda p: (not p["hasAbnormal"], p["trendDirection"] == "stable"))

            result = {
                "familyId": family_id,
                "memberId": member_id,
                "totalTests": len(patterns),
                "patterns": patterns,
            }

        else:
            logger.warning("Unknown function requested", extra={"function": function})
            result = {"error": f"Unknown function: {function}"}

    except Exception as e:
        logger.exception("Error processing action")
        result = {"error": str(e)}

    return _build_response(action_group, function, result)
