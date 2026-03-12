"""
Unit tests for the 3 Bedrock Agent Action Group Lambda handlers.
All DynamoDB calls are mocked — no real AWS connection required.

Run from the repo root:
    python -m pytest tests/test_action_groups.py -v
"""
import sys
import os
import json
import types
import unittest
from unittest.mock import MagicMock, patch
from decimal import Decimal

# ---------------------------------------------------------------------------
# Path patching: make `lambdas.shared.*` importable from the test runner.
# ---------------------------------------------------------------------------
_backend_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend"))
if _backend_dir not in sys.path:
    sys.path.insert(0, _backend_dir)

# Stub out _bootstrap so the action handlers don't re-add paths
bootstrap_stub = types.ModuleType("_bootstrap")
sys.modules["_bootstrap"] = bootstrap_stub

# Stub out aws_lambda_powertools so tests don't require that package
powertools_stub = types.ModuleType("aws_lambda_powertools")

class _FakeLogger:
    def __init__(self, **kwargs): pass
    def inject_lambda_context(self, log_event=False):
        def decorator(fn): return fn
        return decorator
    def info(self, *a, **kw): pass
    def warning(self, *a, **kw): pass
    def exception(self, *a, **kw): pass

powertools_stub.Logger = _FakeLogger
sys.modules["aws_lambda_powertools"] = powertools_stub

utilities_stub = types.ModuleType("aws_lambda_powertools.utilities")
typing_stub = types.ModuleType("aws_lambda_powertools.utilities.typing")
typing_stub.LambdaContext = object
sys.modules["aws_lambda_powertools.utilities"] = utilities_stub
sys.modules["aws_lambda_powertools.utilities.typing"] = typing_stub

# Now safe to import action handlers
from lambdas.agent_chat.action_groups import health_data_actions, family_actions, trend_actions  # noqa: E402


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_event(action_group: str, function: str, **params) -> dict:
    return {
        "actionGroup": action_group,
        "function": function,
        "parameters": [{"name": k, "value": v} for k, v in params.items()],
    }


def _parse_body(response: dict) -> dict:
    body_str = response["response"]["functionResponse"]["responseBody"]["TEXT"]["body"]
    return json.loads(body_str)


def _make_obs(loinc, value, is_abnormal, date="2024-01-15", member_id="m1", name="Test"):
    return {
        "id": "obs-1", "loincCode": loinc, "name": name,
        "value": Decimal(str(value)), "unit": "mg/dL",
        "isAbnormal": is_abnormal, "interpretation": "H" if is_abnormal else "N",
        "date": date, "reportId": "rep-1", "memberId": member_id,
        "normalLow": Decimal("70"), "normalHigh": Decimal("100"),
    }


def _make_report(report_id, member_id="m1", date="2024-01-15", abnormal=1, total=5):
    return {
        "reportId": report_id, "familyId": "fam-1", "memberId": member_id,
        "reportType": "CBC", "date": date,
        "totalObservations": Decimal(str(total)), "abnormalCount": Decimal(str(abnormal)),
        "status": "complete",
    }


# ---------------------------------------------------------------------------
# Tests: health_data_actions
# ---------------------------------------------------------------------------

class TestHealthDataActions(unittest.TestCase):

    def setUp(self):
        self.mock_repo = MagicMock()
        health_data_actions.repo = self.mock_repo

    def test_get_reports_with_member_id(self):
        self.mock_repo.get_reports.return_value = [_make_report("rep-1")]
        event = _make_event("chetana-action-health-data", "getReports", familyId="fam-1", memberId="m1")
        result = health_data_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertEqual(body["total"], 1)
        self.assertIn("summary", body["reports"][0])
        self.mock_repo.get_reports.assert_called_once_with("fam-1", "m1")

    def test_get_reports_without_member_id(self):
        self.mock_repo.get_reports.return_value = []
        event = _make_event("chetana-action-health-data", "getReports", familyId="fam-1")
        result = health_data_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertEqual(body["total"], 0)

    def test_get_observations_with_date_range(self):
        obs = [_make_obs("2339-0", 130, True, "2024-02-01")]
        self.mock_repo.get_observations.return_value = obs
        event = _make_event(
            "chetana-action-health-data", "getObservations",
            familyId="fam-1", memberId="m1", loincCode="2339-0",
            fromDate="2024-01-01", toDate="2024-03-01"
        )
        result = health_data_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertEqual(body["total"], 1)
        self.assertEqual(body["abnormalCount"], 1)
        self.mock_repo.get_observations.assert_called_once_with("fam-1", "m1", "2339-0", "2024-01-01", "2024-03-01")

    def test_get_report_detail_found(self):
        rep = _make_report("rep-1")
        obs = [_make_obs("2339-0", 130, True)]
        self.mock_repo.get_report_detail.return_value = {"report": rep, "observations": obs}
        event = _make_event("chetana-action-health-data", "getReportDetail",
                            familyId="fam-1", memberId="m1", reportId="rep-1")
        result = health_data_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertEqual(body["totalObservations"], 1)
        self.assertEqual(body["abnormalCount"], 1)

    def test_get_report_detail_not_found(self):
        self.mock_repo.get_report_detail.return_value = None
        event = _make_event("chetana-action-health-data", "getReportDetail",
                            familyId="fam-1", memberId="m1", reportId="missing")
        result = health_data_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertIn("error", body)

    def test_unknown_function(self):
        event = _make_event("chetana-action-health-data", "nonExistentFn", familyId="fam-1")
        result = health_data_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertIn("error", body)


# ---------------------------------------------------------------------------
# Tests: family_actions
# ---------------------------------------------------------------------------

class TestFamilyActions(unittest.TestCase):

    def setUp(self):
        self.mock_repo = MagicMock()
        family_actions.repo = self.mock_repo

    def test_get_family_dashboard(self):
        self.mock_repo.get_family_members.return_value = [
            {"id": "m1", "name": "Alice"}, {"id": "m2", "name": "Bob"}
        ]
        self.mock_repo.get_observations.return_value = [
            _make_obs("2339-0", 130, True), _make_obs("718-7", 12, False)
        ]
        event = _make_event("chetana-action-family", "getFamilyDashboard", familyId="fam-1")
        result = family_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertEqual(body["memberCount"], 2)
        self.assertEqual(body["members"][0]["abnormalObservationCount"], 1)

    def test_get_member_detail_found(self):
        self.mock_repo.get_member.return_value = {"id": "m1", "name": "Alice", "familyId": "fam-1"}
        self.mock_repo.get_reports.return_value = [
            _make_report("r1", date="2024-01-15"),
            _make_report("r2", date="2024-03-10"),
        ]
        self.mock_repo.get_observations.return_value = [
            _make_obs("2339-0", 130, True), _make_obs("718-7", 12, False)
        ]
        event = _make_event("chetana-action-family", "getMemberDetail", familyId="fam-1", memberId="m1")
        result = family_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertEqual(body["totalReports"], 2)
        self.assertEqual(body["mostRecentReportDate"], "2024-03-10")
        self.assertEqual(body["abnormalObservationCount"], 1)

    def test_get_member_detail_not_found(self):
        self.mock_repo.get_member.return_value = None
        event = _make_event("chetana-action-family", "getMemberDetail", familyId="fam-1", memberId="x")
        result = family_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertIn("error", body)

    def test_get_abnormals_filters_correctly(self):
        self.mock_repo.get_observations.return_value = [
            _make_obs("2339-0", 130, True, "2024-02-01"),
            _make_obs("718-7", 12, False, "2024-01-01"),
        ]
        event = _make_event("chetana-action-family", "getAbnormals", familyId="fam-1", memberId="m1")
        result = family_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertEqual(body["abnormalCount"], 1)
        self.assertTrue(all(o["isAbnormal"] for o in body["abnormalObservations"]))

    def test_get_abnormals_sorted_by_date_desc(self):
        self.mock_repo.get_observations.return_value = [
            _make_obs("2339-0", 130, True, "2024-01-01"),
            _make_obs("718-7", 15, True, "2024-03-01"),
        ]
        event = _make_event("chetana-action-family", "getAbnormals", familyId="fam-1", memberId="m1")
        result = family_actions.lambda_handler(event, None)
        body = _parse_body(result)
        dates = [o["date"] for o in body["abnormalObservations"]]
        self.assertEqual(dates, sorted(dates, reverse=True))


# ---------------------------------------------------------------------------
# Tests: trend_actions
# ---------------------------------------------------------------------------

class TestTrendActions(unittest.TestCase):

    def setUp(self):
        self.mock_repo = MagicMock()
        trend_actions.repo = self.mock_repo

    def test_compute_trend_rising(self):
        obs = [
            _make_obs("2339-0", 90, False, "2024-01-01"),
            _make_obs("2339-0", 100, False, "2024-02-01"),
            _make_obs("2339-0", 130, True, "2024-03-01"),
        ]
        self.mock_repo.get_observations.return_value = obs
        event = _make_event("chetana-action-trends", "computeTrend",
                            familyId="fam-1", memberId="m1", loincCode="2339-0")
        result = trend_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertEqual(body["trendDirection"], "rising")
        self.assertEqual(body["dataPoints"], 3)

    def test_compute_trend_insufficient_data(self):
        self.mock_repo.get_observations.return_value = [_make_obs("2339-0", 100, False)]
        event = _make_event("chetana-action-trends", "computeTrend",
                            familyId="fam-1", memberId="m1", loincCode="2339-0")
        result = trend_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertEqual(body["trendDirection"], "insufficient_data")

    def test_compare_members(self):
        def side_effect(fam_id, m_id, loinc):
            if m_id == "m1":
                return [_make_obs("2339-0", 90, False, "2024-01-10", member_id="m1")]
            return [_make_obs("2339-0", 140, True, "2024-02-10", member_id="m2")]

        self.mock_repo.get_observations.side_effect = side_effect
        event = _make_event("chetana-action-trends", "compareMembers",
                            familyId="fam-1", loincCode="2339-0", memberIds="m1, m2")
        result = trend_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertIn("m1", body["comparison"])
        self.assertIn("m2", body["comparison"])
        self.assertFalse(body["comparison"]["m1"]["isAbnormal"])
        self.assertTrue(body["comparison"]["m2"]["isAbnormal"])

    def test_detect_patterns_groups_by_loinc(self):
        obs_list = [
            _make_obs("2339-0", 90, False, "2024-01-01"),
            _make_obs("2339-0", 130, True, "2024-02-01"),
            _make_obs("718-7", 12, False, "2024-01-01"),
        ]
        self.mock_repo.get_observations.return_value = obs_list
        event = _make_event("chetana-action-trends", "detectPatterns",
                            familyId="fam-1", memberId="m1")
        result = trend_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertEqual(body["totalTests"], 2)
        # Abnormal test should be sorted first
        self.assertTrue(body["patterns"][0]["hasAbnormal"])

    def test_unknown_function(self):
        event = _make_event("chetana-action-trends", "badFn", familyId="fam-1")
        result = trend_actions.lambda_handler(event, None)
        body = _parse_body(result)
        self.assertIn("error", body)


if __name__ == "__main__":
    unittest.main()
