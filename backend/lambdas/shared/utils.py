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

def extract_param(event: dict, name: str, default=None):
    """
    Extract a parameter from a Bedrock Agent Action Group event.
    Checks in the following order:
    1. Direct 'parameters' list (explicit agent parameters)
    2. 'sessionAttributes' (injected session state)
    3. 'promptSessionAttributes'
    """
    # 1. Check explicit parameters
    params = event.get("parameters", [])
    for p in params:
        if p.get("name") == name:
            return p.get("value")
    
    # 2. Check sessionAttributes
    session_attrs = event.get("sessionAttributes", {})
    if name in session_attrs:
        return session_attrs[name]
    
    # 3. Check promptSessionAttributes
    prompt_attrs = event.get("promptSessionAttributes", {})
    if name in prompt_attrs:
        return prompt_attrs[name]
        
    return default

def get_event_body(event: dict) -> dict:
    """
    Extract and parse the JSON body from an API Gateway event.
    Handles base64 encoding if APIGW has marked it as such.
    """
    body = event.get("body")
    if not body:
        return {}
        
    if event.get("isBase64Encoded"):
        try:
            body = base64.b64decode(body).decode("utf-8")
        except Exception:
            # Fallback if decode fails
            pass
            
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return {}
