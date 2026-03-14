import json
from decimal import Decimal

class DecimalEncoder(json.JSONEncoder):
    """
    Standard JSON encoder for DynamoDB responses containing Decimal types.
    Converts Decimal to int or float.
    """
    def default(self, obj):
        if isinstance(obj, Decimal):
            # Convert to int if it has no fractional part, else float
            if obj % 1 == 0:
                return int(obj)
            return float(obj)
        return super(DecimalEncoder, self).default(obj)

def json_dumps(body: dict) -> str:
    """Helper to dump JSON with Decimal support."""
    return json.dumps(body, cls=DecimalEncoder)
