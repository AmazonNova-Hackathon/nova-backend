import boto3
from boto3.dynamodb.conditions import Key
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
pk = f"FAMILY#{family_id}"

response = table.query(
    KeyConditionExpression=Key('pk').eq(pk) & Key('sk').begins_with('MEMBER#')
)

items = response.get('Items', [])
print(json.dumps(items, indent=2, cls=DecimalEncoder))
