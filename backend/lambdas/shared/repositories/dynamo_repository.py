from botocore.config import Config
import boto3
from boto3.dynamodb.conditions import Key
import copy
import datetime
from decimal import Decimal
from lambdas.shared.models.fhir import DiagnosticReport, Observation, Family, Member, InsightCard, FollowUp, MetaData

class DynamoRepository:
    def __init__(self, table_name: str):
        retry_config = Config(retries={"max_attempts": 5, "mode": "standard"})
        # We use resource vs client due to auto-serialization of Dict -> Dynamo Types
        self.dynamodb = boto3.resource('dynamodb', config=retry_config)
        self.table = self.dynamodb.Table(table_name)

    def _get_pk(self, family_id: str) -> str:
        return f"FAMILY#{family_id}"

    def _add_meta(self, item: dict, user_id: str = "system") -> dict:
        now = datetime.datetime.utcnow().isoformat() + "Z"
        if not item.get('meta'):
            item['meta'] = {
                'createdAt': now,
                'updatedAt': now,
                'createdBy': user_id,
                'updatedBy': user_id,
                'version': 1,
                'isDeleted': False
            }
        else:
            # Update existing meta
            meta = item['meta']
            meta['updatedAt'] = now
            meta['updatedBy'] = user_id
            meta['version'] = meta.get('version', 1) + 1
            if 'createdAt' not in meta: meta['createdAt'] = now
            if 'isDeleted' not in meta: meta['isDeleted'] = False
        return item

    def _serialize(self, obj):
        """Recursively convert floats to Decimals for DynamoDB"""
        if isinstance(obj, float):
            return Decimal(str(obj))
        if isinstance(obj, dict):
            return {k: self._serialize(v) for k, v in obj.items()}
        if isinstance(obj, list):
            return [self._serialize(v) for v in obj]
        return obj

    # --------------------------------------------------------------------------
    # FAMILY & MEMBERS
    # --------------------------------------------------------------------------
    def put_family(self, family: Family) -> None:
        item = family.model_dump(exclude_none=True)
        self._add_meta(item)
        item['pk'] = self._get_pk(family.id)
        item['sk'] = "META"
        self.table.put_item(Item=self._serialize(item))

    def put_member(self, member: Member) -> None:
        item = member.model_dump(exclude_none=True)
        self._add_meta(item)
        item['pk'] = self._get_pk(member.familyId)
        item['sk'] = f"MEMBER#{member.id}"
        self.table.put_item(Item=self._serialize(item))

    def get_family_members(self, family_id: str) -> list[dict]:
        response = self.table.query(
            KeyConditionExpression=Key('pk').eq(self._get_pk(family_id)) & Key('sk').begins_with('MEMBER#')
        )
        return [i for i in response.get('Items', []) if not i.get('meta', {}).get('isDeleted', False)]

    def get_member(self, family_id: str, member_id: str) -> dict:
        response = self.table.get_item(
            Key={
                'pk': self._get_pk(family_id),
                'sk': f"MEMBER#{member_id}"
            }
        )
        item = response.get('Item')
        if item and item.get('meta', {}).get('isDeleted', False):
            return None
        return item

    # --------------------------------------------------------------------------
    # REPORTS & OBSERVATIONS
    # --------------------------------------------------------------------------
    def put_report_and_observations(self, report: DiagnosticReport, observations: list[Observation]) -> None:
        """Uses a DynamoDB batch_writer to save the report and observations together"""
        with self.table.batch_writer() as batch:
            # Write Report Item
            report_item = report.model_dump(exclude_none=True)
            self._add_meta(report_item)
            report_item['pk'] = self._get_pk(report.familyId)
            report_item['sk'] = f"REPORT#{report.memberId}#{report.date or 'UNKNOWN'}#{report.reportId}"
            batch.put_item(Item=self._serialize(report_item))

            # Write Observation Items
            for obs in observations:
                obs_item = obs.model_dump(exclude_none=True)
                self._add_meta(obs_item)
                obs_item['pk'] = self._get_pk(report.familyId)
                obs_item['sk'] = f"OBS#{report.memberId}#{obs.loincCode}#{obs.date or 'UNKNOWN'}#{obs.id}"
                batch.put_item(Item=self._serialize(obs_item))

    def update_report_status(self, family_id: str, member_id: str, date: str, report_id: str, status: str, updates: dict = None) -> None:
        sk = f"REPORT#{member_id}#{date or 'UNKNOWN'}#{report_id}"
        
        # Fetch current to ensure meta handling is correct
        resp = self.table.get_item(Key={'pk': self._get_pk(family_id), 'sk': sk})
        item = resp.get('Item')
        if not item: return

        self._add_meta(item)
        item['status'] = status
        if updates:
            for k, v in updates.items():
                item[k] = v

        self.table.put_item(Item=self._serialize(item))

    def get_reports(self, family_id: str, member_id: str = None) -> list[dict]:
        sk_prefix = f"REPORT#{member_id}#" if member_id else "REPORT#"
        response = self.table.query(
            KeyConditionExpression=Key('pk').eq(self._get_pk(family_id)) & Key('sk').begins_with(sk_prefix)
        )
        return [i for i in response.get('Items', []) if not i.get('meta', {}).get('isDeleted', False)]

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
        items = [i for i in response.get('Items', []) if not i.get('meta', {}).get('isDeleted', False)]
        
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
        self._add_meta(item)
        item['pk'] = self._get_pk(family_id)
        item['sk'] = f"INSIGHT#{insight.memberId}#{insight.generatedAt}#{insight.id}"
        self.table.put_item(Item=self._serialize(item))
        
    def put_followup(self, followup: FollowUp, family_id: str) -> None:
        item = followup.model_dump(exclude_none=True)
        self._add_meta(item)
        item['pk'] = self._get_pk(family_id)
        item['sk'] = f"FOLLOWUP#{followup.memberId}#{followup.suggestedDate}#{followup.id}"
        self.table.put_item(Item=self._serialize(item))

    def get_insights(self, family_id: str, member_id: str = None) -> list[dict]:
        """Query all InsightCards for a family, optionally filtered to one member."""
        sk_prefix = f"INSIGHT#{member_id}#" if member_id else "INSIGHT#"
        response = self.table.query(
            KeyConditionExpression=Key('pk').eq(self._get_pk(family_id)) & Key('sk').begins_with(sk_prefix)
        )
        return [i for i in response.get('Items', []) if not i.get('meta', {}).get('isDeleted', False)]

    def get_followups(self, family_id: str, member_id: str = None) -> list[dict]:
        """Query all FollowUps for a family, optionally filtered to one member."""
        sk_prefix = f"FOLLOWUP#{member_id}#" if member_id else "FOLLOWUP#"
        response = self.table.query(
            KeyConditionExpression=Key('pk').eq(self._get_pk(family_id)) & Key('sk').begins_with(sk_prefix)
        )
        return [i for i in response.get('Items', []) if not i.get('meta', {}).get('isDeleted', False)]

    def soft_delete_item(self, family_id: str, sk: str, user_id: str = "system") -> None:
        resp = self.table.get_item(Key={'pk': self._get_pk(family_id), 'sk': sk})
        item = resp.get('Item')
        if not item: return

        self._add_meta(item, user_id)
        item['meta']['isDeleted'] = True
        
        self.table.put_item(Item=self._serialize(item))

    def update_insight(self, family_id: str, member_id: str, generated_at: str,
                       insight_id: str, updates: dict) -> None:
        """Partial update of an InsightCard (e.g. mark as read)."""
        sk = f"INSIGHT#{member_id}#{generated_at}#{insight_id}"
        resp = self.table.get_item(Key={'pk': self._get_pk(family_id), 'sk': sk})
        item = resp.get('Item')
        if not item: return

        self._add_meta(item)
        for k, v in updates.items():
            item[k] = v
            
        self.table.put_item(Item=self._serialize(item))

    def update_followup(self, family_id: str, member_id: str, suggested_date: str,
                        followup_id: str, updates: dict) -> None:
        """Partial update of a FollowUp (e.g. accept / dismiss)."""
        sk = f"FOLLOWUP#{member_id}#{suggested_date}#{followup_id}"
        resp = self.table.get_item(Key={'pk': self._get_pk(family_id), 'sk': sk})
        item = resp.get('Item')
        if not item: return

        self._add_meta(item)
        for k, v in updates.items():
            item[k] = v
            
        self.table.put_item(Item=self._serialize(item))
