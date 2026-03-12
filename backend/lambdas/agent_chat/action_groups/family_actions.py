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
        
        if function == 'getFamilyDashboard':
            members = repo.get_family_members(family_id)
            result = {"familyId": family_id, "members": members}
        elif function == 'getMemberDetail':
            member_id = get_param('memberId')
            member = repo.get_member(family_id, member_id)
            reports = repo.get_reports(family_id, member_id)
            result = {"member": member, "totalReports": len(reports)}
        elif function == 'getAbnormals':
            member_id = get_param('memberId')
            all_obs = repo.get_observations(family_id, member_id)
            abnormals = [o for o in all_obs if o.get('isAbnormal')]
            result = {"abnormalObservations": abnormals}
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
