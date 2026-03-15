import _bootstrap  # noqa: F401
import json
import traceback
from pydantic import ValidationError
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext

from lambdas.agent_chat.agent_service import process_chat
from lambdas.shared.models.responses import ErrorResponse
from lambdas.shared.utils import json_dumps, get_event_body

logger = Logger(service="agent_chat")


@logger.inject_lambda_context(log_event=False)  # Avoid logging PII from chat messages
def lambda_handler(event: dict, context: LambdaContext):
    method = event.get("httpMethod")
    path = event.get("path") or ""

    logger.info("Received request", extra={"method": method, "path": path})

    try:
        path_parameters = event.get('pathParameters') or {}
        family_id = path_parameters.get('familyId')
        member_id = path_parameters.get('memberId')

        if method == "POST" and path.endswith("/chat"):
            body = get_event_body(event)
            # Ensure context from path is used
            body['familyId'] = family_id
            body['memberId'] = member_id
            response = process_chat(body)
            return _build_response(200, response.model_dump())
        else:
            return _build_error(404, "NOT_FOUND", "Route not found")

    except ValidationError as e:
        logger.warning(f"Validation error: {str(e)}")
        return _build_error(400, "VALIDATION_ERROR", str(e))
    except Exception as e:
        logger.error(f"Unhandled error: {str(e)}\n{traceback.format_exc()}")
        return _build_error(500, "INTERNAL_ERROR", "An unexpected error occurred processing the chat.")


from lambdas.shared.utils import json_dumps

def _build_response(status_code: int, body: dict) -> dict:
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
        },
        "body": json_dumps(body),
    }


def _build_error(status_code: int, code: str, message: str) -> dict:
    error_resp = ErrorResponse.create(code=code, message=message)
    return _build_response(status_code, error_resp.model_dump())
