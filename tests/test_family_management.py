"""
Unit tests for Family Management Lambda (app.py).
Mocks DynamoDB — no real AWS calls.

Run:
    python -m pytest tests/test_family_management.py -v
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

_family_dir = os.path.join(_backend_dir, "lambdas", "family_management")
if _family_dir not in sys.path:
    sys.path.insert(0, _family_dir)

# Stub _bootstrap
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
_app_path = os.path.join(_family_dir, "app.py")
_spec = importlib.util.spec_from_file_location("family_management_app", _app_path)
family_app = importlib.util.module_from_spec(_spec)
sys.modules["family_management_app"] = family_app
_spec.loader.exec_module(family_app)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _api_event(method, path, body=None, path_params=None):
    return {
        "httpMethod": method,
        "path": path,
        "pathParameters": path_params or {},
        "body": json.dumps(body) if body else None,
    }

def _get_body(response):
    return json.loads(response["body"])

# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestFamilyManagement(unittest.TestCase):

    def setUp(self):
        self.mock_repo = MagicMock()
        family_app.repo = self.mock_repo

    def test_create_family_with_name(self):
        event = _api_event("POST", "/families", body={"name": "The Smiths"})
        resp = family_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 200)
        
        body = _get_body(resp)
        self.assertEqual(body["name"], "The Smiths")
        self.assertIn("id", body)
        self.mock_repo.put_family.assert_called_once()

    def test_create_family_without_name_defaults(self):
        event = _api_event("POST", "/families", body={})
        resp = family_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 200)
        
        body = _get_body(resp)
        self.assertEqual(body["name"], "My Family")
        self.mock_repo.put_family.assert_called_once()

    def test_create_member(self):
        event = _api_event(
            "POST", 
            "/families/fam-1/members", 
            body={"name": "Alice", "age": 35, "relationship": "Self"},
            path_params={"familyId": "fam-1"}
        )
        resp = family_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 200)
        
        body = _get_body(resp)
        self.assertEqual(body["name"], "Alice")
        self.assertEqual(body["familyId"], "fam-1")
        self.mock_repo.put_member.assert_called_once()

    def test_list_members(self):
        self.mock_repo.get_family_members.return_value = [
            {"id": "m1", "name": "Alice"},
            {"id": "m2", "name": "Bob"}
        ]
        event = _api_event(
            "GET", 
            "/families/fam-1/members", 
            path_params={"familyId": "fam-1"}
        )
        resp = family_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 200)
        
        body = _get_body(resp)
        self.assertEqual(body["count"], 2)
        self.assertEqual(body["members"][0]["id"], "m1")

    def test_unknown_route(self):
        event = _api_event("GET", "/unknown")
        resp = family_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 404)

if __name__ == "__main__":
    unittest.main()
