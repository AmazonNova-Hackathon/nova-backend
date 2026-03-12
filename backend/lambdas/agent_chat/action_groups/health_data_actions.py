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
        member_id = get_param('memberId')
        
        result = {}
        
        if function == 'getReports':
            result = repo.get_reports(family_id, member_id)
        elif function == 'getObservations':
            loinc_code = get_param('loincCode')
            from_date = get_param('fromDate')
            to_date = get_param('toDate')
            result = repo.get_observations(family_id, member_id, loinc_code, from_date, to_date)
        elif function == 'getReportDetail':
            report_id = get_param('reportId')
            result = repo.get_report_detail(family_id, member_id, report_id)
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
