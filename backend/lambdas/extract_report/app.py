import _bootstrap  # noqa: F401
import os
import json
import traceback
import urllib.parse
from pydantic import ValidationError
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext

from lambdas.extract_report.extraction_service import process_s3_upload, generate_upload_url
from lambdas.extract_report.report_service import get_reports, get_observations, get_report_status, delete_report, get_report_download_url
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
        family_id = path_parameters.get('familyId')
        member_id = path_parameters.get('memberId')
        report_id = path_parameters.get('reportId')
        params = event.get('queryStringParameters') or {}

        # GET /families/{familyId}/members/{memberId}/reports/upload-url
        if method == 'GET' and '/reports/upload-url' in path:
            report_type = params.get('reportType', 'lab_report')
            content_type = params.get('contentType', 'image/jpeg')
            
            if not family_id or not member_id:
                return _build_error(400, "VALIDATION_ERROR", "familyId and memberId are required")
                
            response = generate_upload_url(family_id, member_id, report_type, content_type)
            return _build_response(200, response.model_dump())
            
        # GET /families/{familyId}/members/{memberId}/reports
        elif method == 'GET' and path.endswith('/reports'):
            if not family_id or not member_id:
                return _build_error(400, "VALIDATION_ERROR", "familyId and memberId are required")
            
            response = get_reports(family_id, member_id)
            return _build_response(200, response)
            
        # GET /families/{familyId}/members/{memberId}/observations
        elif method == 'GET' and path.endswith('/observations'):
            if not family_id or not member_id:
                return _build_error(400, "VALIDATION_ERROR", "familyId and memberId are required")
                
            response = get_observations(
                family_id=family_id,
                member_id=member_id,
                loinc_code=params.get('loincCode'),
                from_date=params.get('fromDate'),
                to_date=params.get('toDate')
            )
            return _build_response(200, response)
            
        # GET /families/{familyId}/members/{memberId}/reports/{reportId}/status
        elif method == 'GET' and path.endswith('/status'):
            if not family_id or not member_id or not report_id:
                return _build_error(400, "VALIDATION_ERROR", "familyId, memberId, and reportId are required")
                
            response = get_report_status(family_id, member_id, report_id)
            if not response:
                return _build_error(404, "NOT_FOUND", "Report not found")
                
            return _build_response(200, response)
            
        # GET /families/{familyId}/members/{memberId}/reports/{reportId}/download
        elif method == 'GET' and path.endswith('/download'):
            if not family_id or not member_id or not report_id:
                return _build_error(400, "VALIDATION_ERROR", "familyId, memberId, and reportId are required")
                
            url = get_report_download_url(family_id, member_id, report_id)
            if not url:
                return _build_error(404, "NOT_FOUND", "Report not found")
            return _build_response(200, {"url": url})
            
        # DELETE /families/{familyId}/members/{memberId}/reports/{reportId}
        elif method == 'DELETE' and report_id:
            if not family_id or not member_id:
                return _build_error(400, "VALIDATION_ERROR", "familyId and memberId are required")
                
            success = delete_report(family_id, member_id, report_id)
            if not success:
                return _build_error(404, "NOT_FOUND", "Report not found")
            return _build_response(200, {"message": "Report deleted"})

        # POST /families/{familyId}/members/{memberId}/reports/upload (Manual trigger)
        elif method == 'POST' and path.endswith('/reports/upload'):
            body = json.loads(event.get('body') or '{}')
            s3_key = body.get('s3Key')
            
            if not s3_key or not family_id or not member_id:
                return _build_error(400, "VALIDATION_ERROR", "s3Key, familyId, and memberId are required")
            
            # This triggers the processing manually
            process_s3_upload(os.environ.get('S3_BUCKET_NAME'), s3_key)
            return _build_response(200, {"message": "Processing started", "s3Key": s3_key})

        else:
            return _build_error(404, "NOT_FOUND", "Route not found")
            
    except ValidationError as e:
        logger.warning(f"Validation error: {str(e)}")
        return _build_error(400, "VALIDATION_ERROR", str(e))
    except Exception as e:
        logger.error(f"Unhandled error: {str(e)}\n{traceback.format_exc()}")
        return _build_error(500, "INTERNAL_ERROR", "An unexpected error occurred processing the request.")


from lambdas.shared.utils import json_dumps

def _build_response(status_code: int, body: dict) -> dict:
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*"
        },
        "body": json_dumps(body)
    }

def _build_error(status_code: int, code: str, message: str) -> dict:
    error_resp = ErrorResponse.create(code=code, message=message)
    return _build_response(status_code, error_resp.model_dump())
