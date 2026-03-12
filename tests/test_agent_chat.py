"""
Unit tests for the Chat Agent Wrapper (agent_service.py).
Mocks bedrock-agent-runtime so no real AWS calls are made.

Run:
    python -m pytest tests/test_agent_chat.py -v
"""
import sys
import os
import types
import unittest
from unittest.mock import MagicMock, patch

# ---------------------------------------------------------------------------
# Path setup — make lambdas.* importable from test runner
# ---------------------------------------------------------------------------
_backend_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend"))
if _backend_dir not in sys.path:
    sys.path.insert(0, _backend_dir)

_agent_chat_dir = os.path.join(_backend_dir, "lambdas", "agent_chat")
if _agent_chat_dir not in sys.path:
    sys.path.insert(0, _agent_chat_dir)

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

# Now import the module under test
import agent_service  # noqa: E402


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_event_stream(*text_chunks):
    """
    Build a fake boto3 EventStream (just a list of event dicts).
    Each text chunk becomes one `chunk` event.
    """
    events = []
    for text in text_chunks:
        events.append({"chunk": {"bytes": text.encode("utf-8")}})
    return events


def _make_payload(message="What are my recent results?", family_id="fam-1", session_id=""):
    return {"message": message, "familyId": family_id, "sessionId": session_id}


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestAgentService(unittest.TestCase):

    def setUp(self):
        """Replace the boto3 client on the module with a MagicMock."""
        self.mock_client = MagicMock()
        agent_service.agent_runtime = self.mock_client

    def test_assembles_single_chunk(self):
        self.mock_client.invoke_agent.return_value = {
            "completion": _make_event_stream("Your glucose is 110 mg/dL.")
        }
        result = agent_service.process_chat(_make_payload())
        self.assertEqual(result.reply, "Your glucose is 110 mg/dL.")

    def test_assembles_multiple_chunks(self):
        self.mock_client.invoke_agent.return_value = {
            "completion": _make_event_stream("Your glucose ", "is ", "110 mg/dL.")
        }
        result = agent_service.process_chat(_make_payload())
        self.assertEqual(result.reply, "Your glucose is 110 mg/dL.")

    def test_mints_new_session_id_when_empty(self):
        self.mock_client.invoke_agent.return_value = {
            "completion": _make_event_stream("Hello!")
        }
        result = agent_service.process_chat(_make_payload(session_id=""))
        self.assertNotEqual(result.sessionId, "")
        # Should be a UUID-like string (36 chars with dashes)
        self.assertEqual(len(result.sessionId), 36)

    def test_preserves_existing_session_id(self):
        self.mock_client.invoke_agent.return_value = {
            "completion": _make_event_stream("Continuing session.")
        }
        result = agent_service.process_chat(_make_payload(session_id="my-existing-session-abc"))
        self.assertEqual(result.sessionId, "my-existing-session-abc")

    def test_passes_family_id_as_session_attribute(self):
        self.mock_client.invoke_agent.return_value = {
            "completion": _make_event_stream("OK")
        }
        agent_service.process_chat(_make_payload(family_id="fam-xyz"))
        call_kwargs = self.mock_client.invoke_agent.call_args[1]
        self.assertEqual(
            call_kwargs["sessionState"]["sessionAttributes"]["familyId"],
            "fam-xyz"
        )

    def test_fallback_message_on_empty_stream(self):
        self.mock_client.invoke_agent.return_value = {"completion": []}
        result = agent_service.process_chat(_make_payload())
        self.assertIn("wasn't able to retrieve", result.reply)

    def test_fallback_message_on_access_denied(self):
        from botocore.exceptions import ClientError
        err_resp = {"Error": {"Code": "AccessDeniedException", "Message": "AccessDenied"}}
        self.mock_client.invoke_agent.side_effect = ClientError(err_resp, "InvokeAgent")
        result = agent_service.process_chat(_make_payload())
        self.assertIn("unable to access", result.reply.lower())

    def test_fallback_message_on_generic_exception(self):
        self.mock_client.invoke_agent.side_effect = RuntimeError("connection reset")
        result = agent_service.process_chat(_make_payload())
        self.assertIn("trouble", result.reply.lower())

    def test_response_includes_disclaimer(self):
        self.mock_client.invoke_agent.return_value = {
            "completion": _make_event_stream("Your HbA1c is 5.6%.")
        }
        result = agent_service.process_chat(_make_payload())
        self.assertIn("MediAgent does not provide medical advice", result.disclaimer)

    def test_validation_error_on_missing_family_id(self):
        from pydantic import ValidationError
        with self.assertRaises(ValidationError):
            agent_service.process_chat({"message": "Hello"})  # familyId missing


if __name__ == "__main__":
    unittest.main()
