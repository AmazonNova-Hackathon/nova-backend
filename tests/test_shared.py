import pytest
from backend.lambdas.shared.models.requests import UploadRequest, ChatRequest
from backend.lambdas.shared.loinc_mapping import find_loinc_code

def test_upload_request_validation():
    """Ensure Pydantic models validate input accurately."""
    req = UploadRequest(
        patientId="demo-001",
        imageBase64="VGhpcyBpcyBhIG1vY2sgYmFzZTY0IHN0cmluZw==",
        reportType="lab_report"
    )
    assert req.patientId == "demo-001"
    
def test_loinc_mapping_fuzzy_search():
    """Ensure fuzzy search correctly identifies variations of test names."""
    # Direct match
    res = find_loinc_code("glucose")
    assert res["code"] == "2339-0"
    
    # Substring match
    res_fuzzy = find_loinc_code("fasting blood glucose")
    assert res_fuzzy["code"] == "2339-0"
    
def test_loinc_mapping_unknown():
    """Ensure unknown tests fall back gracefully without exceptions."""
    res = find_loinc_code("unknown futuristic test")
    assert res["code"] == "unknown"
