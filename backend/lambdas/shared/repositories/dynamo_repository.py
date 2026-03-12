from botocore.config import Config
import boto3
from boto3.dynamodb.conditions import Key
import copy

from lambdas.shared.models.fhir import DiagnosticReport, Observation, Family, Member, InsightCard, FollowUp

class DynamoRepository:
    def __init__(self, table_name: str):
        retry_config = Config(retries={"max_attempts": 5, "mode": "standard"})
        # We use resource vs client due to auto-serialization of Dict -> Dynamo Types
        self.dynamodb = boto3.resource('dynamodb', config=retry_config)
        self.table = self.dynamodb.Table(table_name)

    def _get_pk(self, family_id: str) -> str:
        return f"FAMILY#{family_id}"

    # --------------------------------------------------------------------------
    # FAMILY & MEMBERS
    # --------------------------------------------------------------------------
    def put_family(self, family: Family) -> None:
        item = family.model_dump(exclude_none=True)
        item['pk'] = self._get_pk(family.id)
        item['sk'] = "META"
        self.table.put_item(Item=item)

    def put_member(self, member: Member) -> None:
        item = member.model_dump(exclude_none=True)
        item['pk'] = self._get_pk(member.familyId)
        item['sk'] = f"MEMBER#{member.id}"
        self.table.put_item(Item=item)

    def get_family_members(self, family_id: str) -> list[dict]:
        response = self.table.query(
            KeyConditionExpression=Key('pk').eq(self._get_pk(family_id)) & Key('sk').begins_with('MEMBER#')
        )
        return response.get('Items', [])

    def get_member(self, family_id: str, member_id: str) -> dict:
        response = self.table.get_item(
            Key={
                'pk': self._get_pk(family_id),
                'sk': f"MEMBER#{member_id}"
            }
        )
        return response.get('Item')

    # --------------------------------------------------------------------------
    # REPORTS & OBSERVATIONS
    # --------------------------------------------------------------------------
    def put_report_and_observations(self, report: DiagnosticReport, observations: list[Observation]) -> None:
        """Uses a DynamoDB batch_writer to save the report and observations together"""
        with self.table.batch_writer() as batch:
            # Write Report Item
            report_item = report.model_dump(exclude_none=True)
            report_item['pk'] = self._get_pk(report.familyId)
            report_item['sk'] = f"REPORT#{report.memberId}#{report.date or 'UNKNOWN'}#{report.reportId}"
            batch.put_item(Item=report_item)

            # Write Observation Items
            for obs in observations:
                obs_item = obs.model_dump(exclude_none=True)
                obs_item['pk'] = self._get_pk(report.familyId)
                obs_item['sk'] = f"OBS#{report.memberId}#{obs.loincCode}#{obs.date or 'UNKNOWN'}#{obs.id}"
                batch.put_item(Item=obs_item)

    def update_report_status(self, family_id: str, member_id: str, date: str, report_id: str, status: str, updates: dict = None) -> None:
        sk = f"REPORT#{member_id}#{date or 'UNKNOWN'}#{report_id}"
        
        update_expr = "SET #status = :s"
        expr_names = {"#status": "status"}
        expr_values = {":s": status}
        
        if updates:
            for k, v in updates.items():
                update_expr += f", #{k} = :{k}"
                expr_names[f"#{k}"] = k
                expr_values[f":{k}"] = v

        self.table.update_item(
            Key={'pk': self._get_pk(family_id), 'sk': sk},
            UpdateExpression=update_expr,
            ExpressionAttributeNames=expr_names,
            ExpressionAttributeValues=expr_values
        )

    def get_reports(self, family_id: str, member_id: str = None) -> list[dict]:
        sk_prefix = f"REPORT#{member_id}#" if member_id else "REPORT#"
        response = self.table.query(
            KeyConditionExpression=Key('pk').eq(self._get_pk(family_id)) & Key('sk').begins_with(sk_prefix)
        )
        return response.get('Items', [])

    def get_observations(self, family_id: str, member_id: str = None, loinc_code: str = None, 
                         from_date: str = None, to_date: str = None) -> list[dict]:
        if member_id and loinc_code:
            sk_prefix = f"OBS#{member_id}#{loinc_code}#"
        elif member_id:
            sk_prefix = f"OBS#{member_id}#"
        else:
            sk_prefix = "OBS#"
            
        response = self.table.query(
            KeyConditionExpression=Key('pk').eq(self._get_pk(family_id)) & Key('sk').begins_with(sk_prefix)
        )
        items = response.get('Items', [])
        
        # In-memory filter for dates since they are further down the SK
        if from_date or to_date:
            filtered = []
            for item in items:
                item_date = item.get('date', "")
                if from_date and item_date < from_date: continue
                if to_date and item_date > to_date: continue
                filtered.append(item)
            return filtered
            
        return items

    def get_report_detail(self, family_id: str, member_id: str, report_id: str) -> dict:
        """Finds the report with the given ID and all observations associated with it."""
        reports = self.get_reports(family_id, member_id)
        report = next((r for r in reports if r.get('reportId') == report_id), None)
        
        if not report:
            return None
            
        all_obs = self.get_observations(family_id, member_id)
        report_obs = [obs for obs in all_obs if obs.get('reportId') == report_id]
        
        return {
            "report": report,
            "observations": report_obs
        }

    # --------------------------------------------------------------------------
    # INSIGHTS & FOLLOW-UPS
    # --------------------------------------------------------------------------
    def put_insight(self, insight: InsightCard, family_id: str) -> None:
        item = insight.model_dump(exclude_none=True)
        item['pk'] = self._get_pk(family_id)
        item['sk'] = f"INSIGHT#{insight.memberId}#{insight.generatedAt}#{insight.id}"
        self.table.put_item(Item=item)
        
    def put_followup(self, followup: FollowUp, family_id: str) -> None:
        item = followup.model_dump(exclude_none=True)
        item['pk'] = self._get_pk(family_id)
        item['sk'] = f"FOLLOWUP#{followup.memberId}#{followup.suggestedDate}#{followup.id}"
        self.table.put_item(Item=item)
