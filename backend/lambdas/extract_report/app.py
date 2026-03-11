import json
import traceback
from pydantic import ValidationError
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext

from extraction_service import process_upload, generate_upload_url
from report_service import get_reports, get_observations
from lambdas.shared.models.responses import ErrorResponse

logger = Logger(service="extract_report")

@logger.inject_lambda_context(log_event=False) # Ensure we don't log the raw event containing base64/PII image bytes
def lambda_handler(event: dict, context: LambdaContext):
    method = event.get('httpMethod')
    path = event.get('path')
    
    logger.info("Received request", extra={"method": method, "path": path})

    try:
        if method == 'GET' and path == '/reports/upload-url':
            params = event.get('queryStringParameters') or {}
            patient_id = params.get('patientId')
            if not patient_id:
                return _build_error(400, "VALIDATION_ERROR", "patientId query parameter is required")
                
            response = generate_upload_url(patient_id)
            return _build_response(200, response.model_dump())
            
        elif method == 'POST' and path == '/reports/upload':
            body = json.loads(event.get('body', '{}'))
            response = process_upload(body)
            return _build_response(200, response.model_dump())
            
        elif method == 'GET' and path == '/reports':
            params = event.get('queryStringParameters') or {}
            patient_id = params.get('patientId')
            if not patient_id:
                return _build_error(400, "VALIDATION_ERROR", "patientId query parameter is required")
            
            response = get_reports(patient_id)
            return _build_response(200, response)
            
        elif method == 'GET' and path == '/observations':
            params = event.get('queryStringParameters') or {}
            patient_id = params.get('patientId')
            if not patient_id:
                return _build_error(400, "VALIDATION_ERROR", "patientId query parameter is required")
                
            response = get_observations(
                patient_id=patient_id,
                loinc_code=params.get('loincCode'),
                from_date=params.get('fromDate'),
                to_date=params.get('toDate')
            )
            return _build_response(200, response)
            
        else:
            return _build_error(404, "NOT_FOUND", "Route not found")
            
    except ValidationError as e:
        logger.warning(f"Validation error: {str(e)}")
        return _build_error(400, "VALIDATION_ERROR", str(e))
    except Exception as e:
        logger.error(f"Unhandled error: {str(e)}\n{traceback.format_exc()}")
        return _build_error(500, "INTERNAL_ERROR", "An unexpected error occurred processing the report.")


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
