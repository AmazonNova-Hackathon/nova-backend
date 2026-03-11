import json
import traceback
from pydantic import ValidationError
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext

from agent_service import process_chat
from lambdas.shared.models.responses import ErrorResponse

logger = Logger(service="agent_chat")

@logger.inject_lambda_context(log_event=False) # Ensure we don't log PII from the conversation history
def lambda_handler(event: dict, context: LambdaContext):
    method = event.get('httpMethod')
    path = event.get('path')
    
    logger.info("Received request", extra={"method": method, "path": path})

    try:
        if method == 'POST' and path == '/chat':
            body = json.loads(event.get('body', '{}'))
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


def _build_response(status_code: int, body: dict) -> dict:
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*"
        },
        "body": json.dumps(body)
    }

def _build_error(status_code: int, code: str, message: str) -> dict:
    error_resp = ErrorResponse.create(code=code, message=message)
    return _build_response(status_code, error_resp.model_dump())
