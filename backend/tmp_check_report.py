import boto3
from boto3.dynamodb.conditions import Key, Attr
import json
from decimal import Decimal

class DecimalEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, Decimal):
            return str(obj)
        return super(DecimalEncoder, self).default(obj)

dynamodb = boto3.resource('dynamodb', region_name='us-east-1')
table = dynamodb.Table('mediagent-MediAgentFhirTable-1E26NGX7H7NDT')

family_id = "cf7eb826-c613-4850-9cc0-b960ebfd7f5b"
report_id = "db147982-229b-45a7-91d6-bd616fb6132c"

response = table.query(
    KeyConditionExpression=Key('pk').eq(f"FAMILY#{family_id}"),
    FilterExpression=Attr('reportId').eq(report_id)
)

print(json.dumps(response.get('Items', []), indent=2, cls=DecimalEncoder))
