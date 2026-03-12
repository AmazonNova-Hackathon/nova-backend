import json
import logging
from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.config import TABLE_NAME

logger = logging.getLogger()
logger.setLevel(logging.INFO)
repo = DynamoRepository(TABLE_NAME)

def lambda_handler(event, context):
    logger.info(f"Received event: {json.dumps(event)}")
    action_group = event.get('actionGroup')
    function = event.get('function')
    parameters = event.get('parameters', [])
    
    def get_param(name):
        for p in parameters:
            if p.get('name') == name:
                return p.get('value')
        return None

    try:
        family_id = get_param('familyId')
        result = {}
        
        if function == 'computeTrend':
            member_id = get_param('memberId')
            loinc_code = get_param('loincCode')
            obs = repo.get_observations(family_id, member_id, loinc_code)
            obs.sort(key=lambda x: x.get('date', ''))
            result = {"loincCode": loinc_code, "trend": obs}
        elif function == 'compareMembers':
            loinc_code = get_param('loincCode')
            members = get_param('memberIds') # assume comma separated
            member_ids = [m.strip() for m in members.split(',')] if members else []
            
            comparison = {}
            for m_id in member_ids:
                obs = repo.get_observations(family_id, m_id, loinc_code)
                obs.sort(key=lambda x: x.get('date', ''))
                if obs:
                    comparison[m_id] = obs[-1] # latest
            result = {"comparison": comparison}
        elif function == 'detectPatterns':
            member_id = get_param('memberId')
            all_obs = repo.get_observations(family_id, member_id)
            result = {"allObservations": all_obs}
        else:
            result = {"error": f"Unknown function {function}"}
            
    except Exception as e:
        logger.error(str(e))
        result = {"error": str(e)}

    response_body = {"TEXT": {"body": json.dumps(result, default=str)}}
    
    return {
        "messageVersion": "1.0",
        "response": {
            "actionGroup": action_group,
            "function": function,
            "functionResponse": {
                "responseBody": response_body
            }
        }
    }
