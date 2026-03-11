import json
import uuid
import base64
import datetime
from botocore.config import Config
import boto3

from lambdas.shared.config import BUCKET_NAME, BEDROCK_REGION, NOVA_MODEL_ID, TABLE_NAME
from lambdas.shared.models.requests import UploadRequest
from lambdas.shared.models.responses import UploadResponse
from lambdas.shared.models.fhir import Observation, DiagnosticReport
from lambdas.shared.repositories.s3_repository import S3Repository
from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.loinc_mapping import find_loinc_code

s3_repo = S3Repository(BUCKET_NAME)
dynamo_repo = DynamoRepository(TABLE_NAME)

retry_config = Config(retries={"max_attempts": 3, "mode": "standard"})
bedrock = boto3.client('bedrock-runtime', region_name=BEDROCK_REGION, config=retry_config)

EXTRACTION_PROMPT = """Extract all lab test results from this medical report image.
Return ONLY valid JSON in this exact format:
{
  "labName": "name of the lab",
  "date": "YYYY-MM-DD",
  "tests": [
    {"name": "...", "value": 0.0, "unit": "...", "refLow": 0.0, "refHigh": 0.0}
  ]
}
If a value is not a number (e.g., Negative/Positive), extract it as a string instead, but structure must match.
Rules:
- Only extract values that are visibly printed. Never guess or infer.
- Include the lab's own reference range, not standard ranges.
- If date is not visible, use null.
"""

def process_upload(raw_payload: dict) -> UploadResponse:
    # 1. Validate incoming JSON payload
    req = UploadRequest(**raw_payload)
    report_id = str(uuid.uuid4())
    
    # 2. Upload raw image to S3
    s3_key = f"{req.patientId}/report-{report_id}.jpg"
    image_bytes = base64.b64decode(req.imageBase64)
    s3_repo.upload_image(s3_key, image_bytes)
    
    # 3. Invoke Amazon Nova for Multimodal Extraction
    extracted_json = _invoke_nova_extraction(req.imageBase64)
    
    # 4. Map JSON to FHIR models & determine lab abnormality
    observations = []
    abnormal_count = 0
    extract_date = extracted_json.get("date") or datetime.date.today().isoformat()
    
    for test in extracted_json.get("tests", []):
        obs = _build_observation(test, extract_date, report_id)
        if obs.isAbnormal:
            abnormal_count += 1
        observations.append(obs)
        
    # 5. Save to DynamoDB
    report = DiagnosticReport(
        reportId=report_id,
        patientId=req.patientId,
        reportType=req.reportType,
        date=extract_date,
        labName=extracted_json.get("labName", "Unknown"),
        totalObservations=len(observations),
        abnormalCount=abnormal_count,
        s3Key=s3_key
    )
    dynamo_repo.put_report_and_observations(report, observations)
    
    # 6. Return response
    return UploadResponse(
        reportId=report_id,
        date=extract_date,
        labName=report.labName,
        observations=observations,
        extractionConfidence=0.95  # Mocked, Nova Lite doesn't inherently give an outer extraction score natively.
    )


def _invoke_nova_extraction(image_base64: str) -> dict:
    """Invokes Amazon Nova Lite passing the base64 image and system prompt."""
    payload = {
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "image": {
                            "format": "jpeg",
                            "source": {"bytes": image_base64}
                        }
                    },
                    {"text": EXTRACTION_PROMPT}
                ]
            }
        ]
    }
    
    response = bedrock.invoke_model(
        modelId=NOVA_MODEL_ID,
        body=json.dumps(payload),
        contentType="application/json",
        accept="application/json"
    )
    
    response_body = json.loads(response['body'].read().decode('utf-8'))
    # Extract only the text answer from the bedrock response. Could contain markdown ```json ``` blocks
    content_text = response_body.get('output', {}).get('message', {}).get('content', [{}])[0].get('text', '{}')
    
    # Clean up markdown tags if Nova added them
    if content_text.startswith("```json"):
        content_text = content_text.strip("```json").strip("```")
        
    return json.loads(content_text.strip())


def _build_observation(test: dict, date: str, report_id: str) -> Observation:
    """Maps extracted test dict from Nova to our strongly typed FHIR model."""
    test_name = test.get("name", "Unknown")
    loinc_data = find_loinc_code(test_name)
    
    val = float(test.get("value", 0)) if str(test.get("value")).replace('.', '', 1).isdigit() else 0.0
    ref_low = float(test.get("refLow", 0)) if str(test.get("refLow")).replace('.', '', 1).isdigit() else None
    ref_high = float(test.get("refHigh", 0)) if str(test.get("refHigh")).replace('.', '', 1).isdigit() else None
    
    is_abnormal = False
    interp = "N"
    
    if ref_low is not None and ref_high is not None:
        if val > ref_high * 1.5:  # Arbitrary critical high logic
            interp = "HH"
            is_abnormal = True
        elif val > ref_high:
            interp = "H"
            is_abnormal = True
        elif val < ref_low * 0.5: # Arbitrary critical low logic
            interp = "LL"
            is_abnormal = True
        elif val < ref_low:
            interp = "L"
            is_abnormal = True

    return Observation(
        id=str(uuid.uuid4()),
        name=test_name,
        loincCode=loinc_data.get("code", "unknown"),
        value=val,
        unit=test.get("unit") or loinc_data.get("unit", "unknown"),
        normalLow=ref_low,
        normalHigh=ref_high,
        isAbnormal=is_abnormal,
        interpretation=interp,
        date=date,
        reportId=report_id
    )
