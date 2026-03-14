import _bootstrap  # noqa: F401 — adds backend/ to sys.path in Lambda
import json
import traceback
import urllib.parse
from pydantic import ValidationError
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext

from lambdas.extract_report.extraction_service import process_s3_upload, generate_upload_url
from lambdas.extract_report.report_service import get_reports, get_observations, get_report_status
from lambdas.shared.models.responses import ErrorResponse

logger = Logger(service="extract_report")

@logger.inject_lambda_context(log_event=False)
def lambda_handler(event: dict, context: LambdaContext):
    # 1. Handle S3 ObjectCreated Event (Async Process)
    if 'Records' in event and len(event['Records']) > 0 and 's3' in event['Records'][0]:
        logger.info("Received S3 async upload event")
        for record in event['Records']:
            try:
                bucket = record['s3']['bucket']['name']
                key = urllib.parse.unquote_plus(record['s3']['object']['key'])
                process_s3_upload(bucket, key)
            except Exception as e:
                logger.error(f"Failed to process S3 record: {e}\n{traceback.format_exc()}")
        return {"statusCode": 200, "body": "Processed"}

    # 2. Handle REST API via API Gateway
    method = event.get('httpMethod')
    path = event.get('path')
    path_parameters = event.get('pathParameters') or {}
    
    logger.info("Received API request", extra={"method": method, "path": path})

    try:
        # GET /reports/upload-url
        if method == 'GET' and path == '/reports/upload-url':
            params = event.get('queryStringParameters') or {}
            family_id = params.get('familyId')
            member_id = params.get('memberId')
            report_type = params.get('reportType', 'lab_report')
            
            if not family_id:
                return _build_error(400, "VALIDATION_ERROR", "familyId query parameter is required")
                
            response = generate_upload_url(family_id, member_id, report_type)
            return _build_response(200, response.model_dump())
            
        # GET /reports
        elif method == 'GET' and path == '/reports':
            params = event.get('queryStringParameters') or {}
            family_id = params.get('familyId')
            member_id = params.get('memberId') # Optional filter
            if not family_id:
                return _build_error(400, "VALIDATION_ERROR", "familyId query parameter is required")
            
            response = get_reports(family_id, member_id)
            return _build_response(200, response)
            
        # GET /observations
        elif method == 'GET' and path == '/observations':
            params = event.get('queryStringParameters') or {}
            family_id = params.get('familyId')
            member_id = params.get('memberId') # Optional filter
            if not family_id:
                return _build_error(400, "VALIDATION_ERROR", "familyId query parameter is required")
                
            response = get_observations(
                family_id=family_id,
                member_id=member_id,
                loinc_code=params.get('loincCode'),
                from_date=params.get('fromDate'),
                to_date=params.get('toDate')
            )
            return _build_response(200, response)
            
        # GET /reports/{reportId}/status
        elif method == 'GET' and path and path.startswith('/reports/') and path.endswith('/status'):
            report_id = path_parameters.get('reportId')
            params = event.get('queryStringParameters') or {}
            family_id = params.get('familyId')
            member_id = params.get('memberId')
            
            if not family_id or not member_id or not report_id:
                return _build_error(400, "VALIDATION_ERROR", "familyId and memberId are required to check status")
                
            response = get_report_status(family_id, member_id, report_id)
            if not response:
                return _build_error(404, "NOT_FOUND", "Report not found")
                
            return _build_response(200, response)

        else:
            return _build_error(404, "NOT_FOUND", "Route not found")
            
    except ValidationError as e:
        logger.warning(f"Validation error: {str(e)}")
        return _build_error(400, "VALIDATION_ERROR", str(e))
    except Exception as e:
        logger.error(f"Unhandled error: {str(e)}\n{traceback.format_exc()}")
        return _build_error(500, "INTERNAL_ERROR", "An unexpected error occurred processing the request.")


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
