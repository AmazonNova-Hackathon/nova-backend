from botocore.config import Config
import boto3
from boto3.dynamodb.conditions import Key
import copy

from lambdas.shared.models.fhir import DiagnosticReport, Observation

class DynamoRepository:
    def __init__(self, table_name: str):
        retry_config = Config(retries={"max_attempts": 5, "mode": "standard"})
        # We use resource vs client due to auto-serialization of Dict -> Dynamo Types
        self.dynamodb = boto3.resource('dynamodb', config=retry_config)
        self.table = self.dynamodb.Table(table_name)

    def _get_pk(self, patient_id: str) -> str:
        return f"PATIENT#{patient_id}"

    def put_report_and_observations(self, report: DiagnosticReport, observations: list[Observation]) -> None:
        """Uses a DynamoDB batch_writer to save the report and observations together"""
        with self.table.batch_writer() as batch:
            # Write Report Item
            report_item = report.model_dump(exclude_none=True)
            report_item['pk'] = self._get_pk(report.patientId)
            report_item['sk'] = f"REPORT#{report.date}#{report.reportId}"
            batch.put_item(Item=report_item)

            # Write Observation Items
            for obs in observations:
                obs_item = obs.model_dump(exclude_none=True)
                obs_item['pk'] = self._get_pk(report.patientId)
                obs_item['sk'] = f"OBS#{obs.loincCode}#{obs.date}#{obs.id}"
                batch.put_item(Item=obs_item)

    def get_reports(self, patient_id: str) -> list[dict]:
        response = self.table.query(
            KeyConditionExpression=Key('pk').eq(self._get_pk(patient_id)) & Key('sk').begins_with('REPORT#')
        )
        return response.get('Items', [])

    def get_observations(self, patient_id: str, loinc_code: str = None, 
                         from_date: str = None, to_date: str = None) -> list[dict]:
        sk_prefix = f"OBS#{loinc_code}#" if loinc_code else "OBS#"
        
        response = self.table.query(
            KeyConditionExpression=Key('pk').eq(self._get_pk(patient_id)) & Key('sk').begins_with(sk_prefix)
        )
        items = response.get('Items', [])
        
        # In-memory filter for dates if they are provided, since Dynamo query requires leading SK matches
        if from_date or to_date:
            filtered = []
            for item in items:
                item_date = item.get('date', "")
                if from_date && item_date < from_date: continue
                if to_date && item_date > to_date: continue
                filtered.append(item)
            return filtered
            
        return items

    def get_report_detail(self, patient_id: str, report_id: str) -> dict:
        """Finds the report with the given ID and all observations associated with it."""
        # Query 1: Find the actual report (need to find date to match full SK or filter in memory)
        reports = self.get_reports(patient_id)
        report = next((r for r in reports if r.get('reportId') == report_id), None)
        
        if not report:
            return None
            
        # Query 2: Get all observations and filter by reportId
        # (Could be optimized by adding a GSI, but filtering is OK for hackathon scale)
        all_obs = self.get_observations(patient_id)
        report_obs = [obs for obs in all_obs if obs.get('reportId') == report_id]
        
        return {
            "report": report,
            "observations": report_obs
        }
