import json
import inspect
from pydantic import BaseModel
from typing import Optional

from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.config import TABLE_NAME
from lambdas.shared.loinc_mapping import find_loinc_code

repo = DynamoRepository(TABLE_NAME)

_referenced_reports = set()
_referenced_observations = set()

def reset_citations():
    global _referenced_reports, _referenced_observations
    _referenced_reports.clear()
    _referenced_observations.clear()

def get_citations() -> tuple[list[str], list[str]]:
    return list(_referenced_reports), list(_referenced_observations)

# ----------------------------------------------------
# Vanilla Python Tool implementations mapped for Bedrock
# ----------------------------------------------------

def get_all_reports(patient_id: str) -> str:
    """Retrieve all available diagnostic reports for a patient."""
    reports = repo.get_reports(patient_id)
    if not reports:
        return "No reports found for this patient."
    
    for r in reports:
        _referenced_reports.add(r.get('reportId', 'unknown'))
        
    return json.dumps([
        {
            "reportId": r.get('reportId'),
            "date": r.get('date'),
            "labName": r.get('labName'),
            "totalObservations": r.get('totalObservations'),
            "abnormalCount": r.get('abnormalCount')
        } for r in reports
    ])

def get_observations(patient_id: str, test_name: Optional[str] = None, 
                     from_date: Optional[str] = None, to_date: Optional[str] = None) -> str:
    """Retrieve specific lab test observations by name (e.g., 'glucose') or date."""
    loinc_code = None
    if test_name:
        loinc_data = find_loinc_code(test_name)
        loinc_code = loinc_data.get("code")
        if loinc_code == "unknown":
            return f"Failed to find match for test name '{test_name}'"

    obs_list = repo.get_observations(patient_id, loinc_code, from_date, to_date)
    
    if not obs_list:
        return "No observations found matching these criteria."

    for obs in obs_list:
        _referenced_observations.add(obs.get('id', 'unknown'))
        _referenced_reports.add(obs.get('reportId', 'unknown'))

    return json.dumps([
        {
            "name": obs.get("name"),
            "value": obs.get("value"),
            "unit": obs.get("unit"),
            "date": obs.get("date"),
            "interpretation": obs.get("interpretation")
        } for obs in obs_list
    ])

def get_report_detail(patient_id: str, report_id: str) -> str:
    """Retrieve all detailed lab values for a single specific report using its report_id."""
    detail = repo.get_report_detail(patient_id, report_id)
    if not detail:
        return f"Report {report_id} not found."
    
    _referenced_reports.add(report_id)
    for obs in detail.get("observations", []):
        _referenced_observations.add(obs.get('id', 'unknown'))
        
    return json.dumps(detail)

def compute_trend(patient_id: str, test_name: str) -> str:
    """Compute the chronological trend over time for a specific test name."""
    loinc_data = find_loinc_code(test_name)
    loinc_code = loinc_data.get("code")
    
    if loinc_code == "unknown":
        return f"Test name '{test_name}' is not recognized."
        
    obs_list = repo.get_observations(patient_id, loinc_code)
    
    if len(obs_list) < 2:
        return "Not enough data to compute a trend. At least 2 measurements are required."
        
    sorted_obs = sorted(obs_list, key=lambda x: x.get('date', ''))
    
    for obs in sorted_obs:
        _referenced_observations.add(obs.get('id', 'unknown'))
        
    history = [f"{o.get('date')}: {o.get('value')} {o.get('unit')}" for o in sorted_obs]
    
    first = sorted_obs[0].get('value', 0)
    last = sorted_obs[-1].get('value', 0)
    
    trend = "stable"
    if last > first: trend = "increasing"
    elif last < first: trend = "decreasing"
    
    return f"Measurements: {', '.join(history)}. Trend: {trend} overall."


# -----------------------------------------------------------------------------------------
# Define Bedrock Converse Tools Spec
# -----------------------------------------------------------------------------------------

BEDROCK_TOOLS = [
    {
        "toolSpec": {
            "name": "get_all_reports",
            "description": "Retrieve all available diagnostic reports and dates for a patient.",
            "inputSchema": {
                "json": {
                    "type": "object",
                    "properties": {},
                    "required": []
                }
            }
        }
    },
    {
        "toolSpec": {
            "name": "get_observations",
            "description": "Retrieve specific lab test observations. Provide test_name (e.g. 'glucose') to filter. Provide from_date to filter earliest allowed string.",
            "inputSchema": {
                "json": {
                    "type": "object",
                    "properties": {
                        "test_name": {"type": "string", "description": "Name of the test, like 'glucose'"},
                        "from_date": {"type": "string", "description": "Earliest date YYYY-MM-DD"},
                        "to_date": {"type": "string", "description": "Latest date YYYY-MM-DD"}
                    },
                    "required": []
                }
            }
        }
    },
    {
        "toolSpec": {
            "name": "get_report_detail",
            "description": "Retrieve all detailed lab values for a single specific report using its report_id.",
            "inputSchema": {
                "json": {
                    "type": "object",
                    "properties": {
                        "report_id": {"type": "string", "description": "The UUID of the report."}
                    },
                    "required": ["report_id"]
                }
            }
        }
    },
    {
        "toolSpec": {
            "name": "compute_trend",
            "description": "Compute chronological trends for a specific test like 'glucose'",
            "inputSchema": {
                "json": {
                    "type": "object",
                    "properties": {
                        "test_name": {"type": "string"}
                    },
                    "required": ["test_name"]
                }
            }
        }
    }
]

def execute_tool(tool_name: str, patient_id: str, kwargs: dict) -> str:
    """Dispatches mapped Bedrock tool spec payloads to real python functions with the contextual patient_id."""
    if tool_name == "get_all_reports":
        return get_all_reports(patient_id)
    elif tool_name == "get_observations":
        return get_observations(patient_id, **kwargs)
    elif tool_name == "get_report_detail":
        return get_report_detail(patient_id, **kwargs)
    elif tool_name == "compute_trend":
        return compute_trend(patient_id, **kwargs)
    
    return f"Tool {tool_name} is undefined."
