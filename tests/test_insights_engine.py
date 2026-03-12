"""
Unit tests for the Insights Engine Lambda (app.py + insight_service.py).
Mocks DynamoDB and Bedrock — no real AWS calls.

Run:
    python -m pytest tests/test_insights_engine.py -v
"""
import sys
import os
import json
import types
import unittest
from unittest.mock import MagicMock, patch

# ---------------------------------------------------------------------------
# Path setup
# ---------------------------------------------------------------------------
_backend_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend"))
if _backend_dir not in sys.path:
    sys.path.insert(0, _backend_dir)

_engine_dir = os.path.join(_backend_dir, "lambdas", "insights_engine")
if _engine_dir not in sys.path:
    sys.path.insert(0, _engine_dir)

# Stub _bootstrap (side-effect only import)
sys.modules["_bootstrap"] = types.ModuleType("_bootstrap")

# Stub aws_lambda_powertools
powertools_stub = types.ModuleType("aws_lambda_powertools")
class _FakeLogger:
    def __init__(self, **kwargs): pass
    def inject_lambda_context(self, **kw):
        def decorator(fn): return fn
        return decorator
    def info(self, *a, **kw): pass
    def warning(self, *a, **kw): pass
    def error(self, *a, **kw): pass
    def exception(self, *a, **kw): pass
powertools_stub.Logger = _FakeLogger
sys.modules["aws_lambda_powertools"] = powertools_stub
utilities_stub = types.ModuleType("aws_lambda_powertools.utilities")
typing_stub = types.ModuleType("aws_lambda_powertools.utilities.typing")
typing_stub.LambdaContext = object
sys.modules["aws_lambda_powertools.utilities"] = utilities_stub
sys.modules["aws_lambda_powertools.utilities.typing"] = typing_stub

# Now import the module under test using a unique name to avoid sys.modules collisions
import importlib.util
_app_path = os.path.join(_engine_dir, "app.py")
_spec = importlib.util.spec_from_file_location("insights_engine_app", _app_path)
insights_app = importlib.util.module_from_spec(_spec)
sys.modules["insights_engine_app"] = insights_app
_spec.loader.exec_module(insights_app)

import insight_service  # noqa: E402


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _api_event(method, path, body=None, qs=None, path_params=None):
    return {
        "httpMethod": method,
        "path": path,
        "queryStringParameters": qs or {},
        "pathParameters": path_params or {},
        "body": json.dumps(body) if body else None,
    }


def _get_body(response):
    return json.loads(response["body"])


SAMPLE_INSIGHT = {
    "id": "ins-1", "memberId": "m1", "severity": "attention",
    "title": "Elevated Glucose", "summary": "Glucose above normal.",
    "details": "...", "read": False, "generatedAt": "2024-03-01T00:00:00+00:00",
    "disclaimer": "...", "language": "en",
}

SAMPLE_FOLLOWUP = {
    "id": "fu-1", "memberId": "m1", "testName": "Glucose",
    "loincCode": "2339-0", "reason": "Recheck", "suggestedDate": "2024-04-01",
    "status": "pending", "createdAt": "2024-03-01T00:00:00+00:00",
}


# ---------------------------------------------------------------------------
# Tests: HTTP handler (app.py)
# ---------------------------------------------------------------------------

class TestInsightsAppHandler(unittest.TestCase):

    def setUp(self):
        self.mock_repo = MagicMock()
        insights_app.repo = self.mock_repo

    # GET /insights
    def test_get_insights_returns_list(self):
        self.mock_repo.get_insights.return_value = [SAMPLE_INSIGHT]
        event = _api_event("GET", "/insights", qs={"familyId": "fam-1"})
        resp = insights_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 200)
        body = _get_body(resp)
        self.assertEqual(body["total"], 1)

    def test_get_insights_missing_family_id(self):
        event = _api_event("GET", "/insights")
        resp = insights_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 400)

    def test_get_insights_member_filter(self):
        self.mock_repo.get_insights.return_value = [SAMPLE_INSIGHT]
        event = _api_event("GET", "/insights", qs={"familyId": "fam-1", "memberId": "m1"})
        insights_app.lambda_handler(event, None)
        self.mock_repo.get_insights.assert_called_with("fam-1", "m1")

    # GET /followups
    def test_get_followups_filters_pending(self):
        followups = [SAMPLE_FOLLOWUP, {**SAMPLE_FOLLOWUP, "id": "fu-2", "status": "dismissed"}]
        self.mock_repo.get_followups.return_value = followups
        event = _api_event("GET", "/followups", qs={"familyId": "fam-1"})
        resp = insights_app.lambda_handler(event, None)
        body = _get_body(resp)
        self.assertEqual(body["total"], 1)  # only pending

    def test_get_followups_all_statuses(self):
        followups = [SAMPLE_FOLLOWUP, {**SAMPLE_FOLLOWUP, "id": "fu-2", "status": "dismissed"}]
        self.mock_repo.get_followups.return_value = followups
        event = _api_event("GET", "/followups", qs={"familyId": "fam-1", "status": "all"})
        resp = insights_app.lambda_handler(event, None)
        body = _get_body(resp)
        self.assertEqual(body["total"], 2)

    # PATCH /insights/{insightId}
    def test_patch_insight_mark_read(self):
        self.mock_repo.update_insight.return_value = None
        event = _api_event(
            "PATCH", "/insights/ins-1",
            body={"familyId": "fam-1", "memberId": "m1",
                  "generatedAt": "2024-03-01T00:00:00+00:00", "read": True},
            path_params={"insightId": "ins-1"},
        )
        resp = insights_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 200)
        self.mock_repo.update_insight.assert_called_once()

    def test_patch_insight_missing_required_fields(self):
        event = _api_event(
            "PATCH", "/insights/ins-1",
            body={"read": True},
            path_params={"insightId": "ins-1"},
        )
        resp = insights_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 400)

    def test_patch_insight_not_found(self):
        self.mock_repo.update_insight.side_effect = ValueError("not found")
        event = _api_event(
            "PATCH", "/insights/ins-1",
            body={"familyId": "fam-1", "memberId": "m1",
                  "generatedAt": "2024-03-01T00:00:00+00:00", "read": True},
            path_params={"insightId": "ins-1"},
        )
        resp = insights_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 404)

    # PATCH /followups/{followUpId}
    def test_patch_followup_accept(self):
        self.mock_repo.update_followup.return_value = None
        event = _api_event(
            "PATCH", "/followups/fu-1",
            body={"familyId": "fam-1", "memberId": "m1",
                  "suggestedDate": "2024-04-01", "status": "accepted"},
            path_params={"followUpId": "fu-1"},
        )
        resp = insights_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 200)

    # EventBridge cron
    def test_cron_trigger_dispatches_to_run_all(self):
        with patch("insights_engine_app.run_all_families") as mock_run:
            mock_run.return_value = {"familiesProcessed": 2, "results": []}
            event = {"source": "aws.events", "detail-type": "Scheduled Event"}
            resp = insights_app.lambda_handler(event, None)
            self.assertEqual(resp["statusCode"], 200)
            mock_run.assert_called_once()

    # POST /insights/generate
    def test_generate_insights_for_family(self):
        with patch("insights_engine_app.run_for_family") as mock_run:
            mock_run.return_value = {"insightsGenerated": 2, "followupsGenerated": 1}
            event = _api_event("POST", "/insights/generate", body={"familyId": "fam-1"})
            resp = insights_app.lambda_handler(event, None)
            self.assertEqual(resp["statusCode"], 200)
            mock_run.assert_called_once_with("fam-1")

    def test_generate_insights_missing_family_id(self):
        event = _api_event("POST", "/insights/generate", body={})
        resp = insights_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 400)

    # 404
    def test_unknown_route_returns_404(self):
        event = _api_event("DELETE", "/insights/ins-1")
        resp = insights_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 404)


# ---------------------------------------------------------------------------
# Tests: insight_service.py (Bedrock + DynamoDB mock)
# ---------------------------------------------------------------------------

class TestInsightService(unittest.TestCase):

    def setUp(self):
        self.mock_repo = MagicMock()
        insight_service.repo = self.mock_repo
        self.mock_bedrock = MagicMock()
        insight_service.bedrock = self.mock_bedrock

    def _bedrock_response(self, payload: dict) -> dict:
        return {
            "output": {"message": {"content": [{"text": json.dumps(payload)}]}}
        }

    def test_no_abnormals_returns_empty(self):
        self.mock_repo.get_observations.return_value = [
            {"id": "o1", "loincCode": "2339-0", "value": 90, "isAbnormal": False}
        ]
        member = {"id": "m1", "name": "Alice"}
        ins, fus = insight_service.generate_insights_for_member("fam-1", member)
        self.assertEqual(ins, [])
        self.assertEqual(fus, [])
        self.mock_bedrock.converse.assert_not_called()

    def test_generates_insight_from_bedrock(self):
        self.mock_repo.get_observations.return_value = [
            {"id": "o1", "loincCode": "2339-0", "value": 140, "isAbnormal": True,
             "name": "Glucose", "unit": "mg/dL", "reportId": "r1", "memberId": "m1"}
        ]
        payload = {
            "insights": [{
                "title": "Elevated Glucose", "summary": "Your glucose is high.",
                "details": "Glucose of 140 mg/dL is above the normal range.",
                "severity": "attention", "suggestedAction": "Discuss with doctor.",
                "citedObservations": ["o1"], "citedReports": ["r1"]
            }],
            "followups": [{
                "testName": "Glucose", "loincCode": "2339-0",
                "reason": "Recheck", "suggestedDate": "2024-04-01",
                "basedOnObservations": ["o1"]
            }]
        }
        self.mock_bedrock.converse.return_value = self._bedrock_response(payload)
        ins, fus = insight_service.generate_insights_for_member(
            "fam-1", {"id": "m1", "name": "Alice", "age": 35, "gender": "F", "relationship": "Self"})
        self.assertEqual(len(ins), 1)
        self.assertEqual(len(fus), 1)
        self.assertEqual(ins[0].title, "Elevated Glucose")
        self.assertEqual(fus[0].loincCode, "2339-0")
        self.mock_repo.put_insight.assert_called_once()
        self.mock_repo.put_followup.assert_called_once()

    def test_bedrock_json_parse_error_returns_empty(self):
        self.mock_repo.get_observations.return_value = [
            {"id": "o1", "loincCode": "2339-0", "value": 140, "isAbnormal": True,
             "name": "Glucose", "unit": "mg/dL", "reportId": "r1", "memberId": "m1"}
        ]
        self.mock_bedrock.converse.return_value = {
            "output": {"message": {"content": [{"text": "not valid json!!!"}]}}
        }
        ins, fus = insight_service.generate_insights_for_member(
            "fam-1", {"id": "m1", "name": "Alice"})
        self.assertEqual(ins, [])
        self.assertEqual(fus, [])

    def test_run_for_family_iterates_members(self):
        self.mock_repo.get_family_members.return_value = [
            {"id": "m1", "name": "Alice"}, {"id": "m2", "name": "Bob"}
        ]
        self.mock_repo.get_observations.return_value = []  # No abnormals → skip
        result = insight_service.run_for_family("fam-1")
        self.assertEqual(result["memberCount"], 2)
        self.assertEqual(result["insightsGenerated"], 0)


if __name__ == "__main__":
    unittest.main()
