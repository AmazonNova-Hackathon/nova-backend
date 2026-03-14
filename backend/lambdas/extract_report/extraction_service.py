import json
import uuid
import base64
import datetime
from botocore.config import Config
import boto3

from lambdas.shared.config import BUCKET_NAME, BEDROCK_REGION, NOVA_MODEL_ID, TABLE_NAME
from lambdas.shared.models.responses import PreSignedUrlResponse
from lambdas.shared.models.fhir import Observation, DiagnosticReport, Member
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
  "patientName": "Full name of the patient",
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

def generate_upload_url(family_id: str, member_id: str = None, report_type: str = "lab_report", content_type: str = "image/jpeg") -> PreSignedUrlResponse:
    report_id = str(uuid.uuid4())
    member_path = member_id if member_id else "detect"
    
    # Map content type to format/extension
    content_type_map = {
        "image/png": ("png", "png"),
        "image/jpeg": ("jpeg", "jpg"),
        "image/jpg": ("jpeg", "jpg"),
        "image/webp": ("webp", "webp"),
        "application/pdf": ("pdf", "pdf")
    }
    
    fmt, ext = content_type_map.get(content_type, ("jpeg", "jpg"))
    
    # Pre-Signed URL path should match: family_id/member_path/report_id.ext
    s3_key = f"{family_id}/{member_path}/report-{report_id}.{ext}"
    
    url = s3_repo.get_presigned_url(s3_key, content_type=content_type)
    
    # Store initial status skeleton
    dynamo_repo.put_report_and_observations(
        DiagnosticReport(
            reportId=report_id,
            familyId=family_id,
            memberId=member_path,
            reportType=report_type,
            status="uploading",
            s3Key=s3_key
        ),
        []
    )
    
    return PreSignedUrlResponse(
        url=url,
        reportId=report_id,
        s3Key=s3_key
    )

def process_s3_upload(bucket: str, key: str) -> None:
    parts = key.split('/')
    if len(parts) < 3:
        return # invalid key
        
    family_id = parts[0]
    member_id = parts[1]
    
    try:
        filename = parts[2]
        # remove 'report-' and everything after the last dot
        report_id = filename.replace('report-', '')
        if '.' in report_id:
            report_id = report_id.rsplit('.', 1)[0]
    except Exception:
        report_id = str(uuid.uuid4())
        
    # Update status to processing
    dynamo_repo.update_report_status(family_id, member_id, "UNKNOWN", report_id, status="processing")
    
    # Determine format from extension
    ext = key.split('.')[-1].lower() if '.' in key else "jpg"
    format_map = {
        "png": "png",
        "jpg": "jpeg",
        "jpeg": "jpeg",
        "webp": "webp",
        "pdf": "pdf"
    }
    fmt = format_map.get(ext, "jpeg")
    
    image_bytes = s3_repo.get_image_bytes(key)
    # Note: Bedrock Nova supports document block for PDF and image block for images
    
    try:
        extracted_json = _invoke_nova_extraction(image_bytes, ext=ext, fmt=fmt)
    except Exception as e:
        logger.exception(f"Extraction failed for {key}")
        dynamo_repo.update_report_status(family_id, member_id, "UNKNOWN", report_id, status="failed", updates={"error": str(e)})
        return
        
    # Auto-detect member flow
    if member_id == "detect":
        patient_name = extracted_json.get("patientName", "Unknown")
        members = dynamo_repo.get_family_members(family_id)
        matched_member_id = None
        for m in members:
            # simple match
            if 'name' in m and (m['name'].lower() in patient_name.lower() or patient_name.lower() in m['name'].lower()):
                matched_member_id = m.get("id")
                break
                
        if not matched_member_id:
            matched_member_id = str(uuid.uuid4())
            new_member = Member(id=matched_member_id, familyId=family_id, name=patient_name, relationship="Other")
            dynamo_repo.put_member(new_member)
            
        # Optional: Delete the old "detect" report stub from DynamoDB here,
        # but for hackathon time limits we can just let it exist or override where possible.
        member_id = matched_member_id
        
    extract_date = extracted_json.get("date") or datetime.date.today().isoformat()
    
    observations = []
    abnormal_count = 0
    
    for test in extracted_json.get("tests", []):
        obs = _build_observation(test, extract_date, report_id, member_id)
        if obs.isAbnormal:
            abnormal_count += 1
        observations.append(obs)
        
    report = DiagnosticReport(
        reportId=report_id,
        familyId=family_id,
        memberId=member_id,
        reportType="lab_report",
        date=extract_date,
        labName=extracted_json.get("labName", "Unknown"),
        totalObservations=len(observations),
        abnormalCount=abnormal_count,
        s3Key=key,
        status="completed"
    )
    
    # Save final report and observations
    dynamo_repo.put_report_and_observations(report, observations)


def _invoke_nova_extraction(raw_bytes: bytes, ext: str, fmt: str) -> dict:
    data_base64 = base64.b64encode(raw_bytes).decode('utf-8')
    
    content_block = {}
    if ext == 'pdf':
        content_block = {
            "document": {
                "format": "pdf",
                "name": "MedicalReport",
                "source": {"bytes": data_base64}
            }
        }
    else:
        content_block = {
            "image": {
                "format": fmt,
                "source": {"bytes": data_base64}
            }
        }

    payload = {
        "messages": [
            {
                "role": "user",
                "content": [
                    content_block,
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
    content_text = response_body.get('output', {}).get('message', {}).get('content', [{}])[0].get('text', '{}')
    
    if content_text.startswith("```json"):
        content_text = content_text.strip("```json").strip("```")
        
    return json.loads(content_text.strip())

def _build_observation(test: dict, date: str, report_id: str, member_id: str) -> Observation:
    test_name = test.get("name", "Unknown")
    loinc_data = find_loinc_code(test_name)
    
    val = float(test.get("value", 0)) if str(test.get("value")).replace('.', '', 1).isdigit() else 0.0
    ref_low = float(test.get("refLow", 0)) if str(test.get("refLow")).replace('.', '', 1).isdigit() else None
    ref_high = float(test.get("refHigh", 0)) if str(test.get("refHigh")).replace('.', '', 1).isdigit() else None
    
    is_abnormal = False
    interp = "N"
    
    if ref_low is not None and ref_high is not None:
        if val > ref_high * 1.5:
            interp = "HH"
            is_abnormal = True
        elif val > ref_high:
            interp = "H"
            is_abnormal = True
        elif val < ref_low * 0.5:
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
        reportId=report_id,
        memberId=member_id
    )
