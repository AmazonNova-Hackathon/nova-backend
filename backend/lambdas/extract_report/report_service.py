from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.config import TABLE_NAME, DEMO_PATIENT_ID

repo = DynamoRepository(TABLE_NAME)

def get_reports(family_id: str, member_id: str = None) -> dict:
    reports = repo.get_reports(family_id, member_id)
    return {
        "familyId": family_id,
        "memberId": member_id,
        "reports": reports,
        "total": len(reports)
    }

def get_observations(family_id: str, member_id: str = None, loinc_code: str = None, 
                     from_date: str = None, to_date: str = None) -> list:
    obs = repo.get_observations(family_id, member_id, loinc_code, from_date, to_date)
    return obs

def get_report_status(family_id: str, member_id: str, report_id: str) -> dict:
    detail = repo.get_report_detail(family_id, member_id, report_id)
    if not detail or not detail.get("report"):
        return None
    
    report = detail["report"]
    status = report.get("status", "processing")
    
    return {
        "reportId": report_id,
        "status": status,
        "memberId": member_id,
        "date": report.get("date"),
        "labName": report.get("labName"),
        "observations": detail.get("observations", []),
        "disclaimer": "This is an informational summary only. Please consult your doctor." if status == "completed" else None,
        "error": report.get("error")
    }
