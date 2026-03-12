"""
Action Group Lambda: chetana-action-health-data
Functions: getReports, getObservations, getReportDetail

Invoked directly by the Bedrock Agent when it needs to query a patient's
lab reports or individual FHIR observations from DynamoDB.
"""
import _bootstrap  # noqa: F401 — must be first, fixes sys.path for shared imports

import json
from decimal import Decimal
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext

from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.config import TABLE_NAME

logger = Logger(service="action-health-data")
repo = DynamoRepository(TABLE_NAME)


def _decimal_default(obj):
    """JSON serializer for Decimal values returned by DynamoDB boto3 resource."""
    if isinstance(obj, Decimal):
        # Preserve int representation where possible (e.g. age, count fields)
        return int(obj) if obj % 1 == 0 else float(obj)
    raise TypeError(f"Object of type {type(obj)} is not JSON serializable")


def _get_param(parameters: list, name: str):
    for p in parameters:
        if p.get("name") == name:
            return p.get("value")
    return None


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
    parameters = event.get("parameters", [])

    logger.info("Invoked action", extra={"function": function, "actionGroup": action_group})

    family_id = _get_param(parameters, "familyId")
    member_id = _get_param(parameters, "memberId")

    try:
        if function == "getReports":
            reports = repo.get_reports(family_id, member_id)
            # Annotate each report with a human-readable summary for the agent
            for r in reports:
                r["summary"] = (
                    f"{r.get('reportType', 'Report')} on {r.get('date', 'unknown date')} — "
                    f"{r.get('totalObservations', 0)} results, "
                    f"{r.get('abnormalCount', 0)} abnormal"
                )
            result = {"familyId": family_id, "memberId": member_id, "reports": reports, "total": len(reports)}

        elif function == "getObservations":
            loinc_code = _get_param(parameters, "loincCode")
            from_date = _get_param(parameters, "fromDate")
            to_date = _get_param(parameters, "toDate")
            observations = repo.get_observations(family_id, member_id, loinc_code, from_date, to_date)
            abnormals = [o for o in observations if o.get("isAbnormal")]
            result = {
                "familyId": family_id,
                "memberId": member_id,
                "loincCode": loinc_code,
                "observations": observations,
                "total": len(observations),
                "abnormalCount": len(abnormals),
            }

        elif function == "getReportDetail":
            report_id = _get_param(parameters, "reportId")
            detail = repo.get_report_detail(family_id, member_id, report_id)
            if detail is None:
                result = {"error": f"Report {report_id} not found for member {member_id}"}
            else:
                obs = detail.get("observations", [])
                abnormals = [o for o in obs if o.get("isAbnormal")]
                result = {
                    "report": detail.get("report"),
                    "observations": obs,
                    "abnormalObservations": abnormals,
                    "totalObservations": len(obs),
                    "abnormalCount": len(abnormals),
                }

        else:
            logger.warning("Unknown function requested", extra={"function": function})
            result = {"error": f"Unknown function: {function}"}

    except Exception as e:
        logger.exception("Error processing action")
        result = {"error": str(e)}

    return _build_response(action_group, function, result)
