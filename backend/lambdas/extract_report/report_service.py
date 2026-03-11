from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.config import TABLE_NAME, DEMO_PATIENT_ID

repo = DynamoRepository(TABLE_NAME)

def get_reports(patient_id: str) -> dict:
    reports = repo.get_reports(patient_id)
    return {
        "patientId": patient_id,
        "reports": reports,
        "total": len(reports)
    }

def get_observations(patient_id: str, loinc_code: str = None, 
                     from_date: str = None, to_date: str = None) -> list:
    obs = repo.get_observations(patient_id, loinc_code, from_date, to_date)
    return obs
