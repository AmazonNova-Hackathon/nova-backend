"""
Unit tests for Voice Gateway Lambda (app.py + voice_service.py).
Mocks Bedrock — no real AWS calls.

Run:
    python -m pytest tests/test_voice_gateway.py -v
"""
import sys
import os
import json
import base64
import types
import unittest
from unittest.mock import MagicMock, patch

# ---------------------------------------------------------------------------
# Path setup
# ---------------------------------------------------------------------------
_backend_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend"))
if _backend_dir not in sys.path:
    sys.path.insert(0, _backend_dir)

_voice_dir = os.path.join(_backend_dir, "lambdas", "voice_gateway")
if _voice_dir not in sys.path:
    sys.path.insert(0, _voice_dir)

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
_app_path = os.path.join(_voice_dir, "app.py")
_spec = importlib.util.spec_from_file_location("voice_gateway_app", _app_path)
voice_app = importlib.util.module_from_spec(_spec)
sys.modules["voice_gateway_app"] = voice_app
_spec.loader.exec_module(voice_app)

import voice_service  # noqa: E402


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _post_event(body: dict) -> dict:
    return {
        "httpMethod": "POST",
        "path": "/voice/session",
        "body": json.dumps(body),
    }


def _get_body(response: dict) -> dict:
    return json.loads(response["body"])


def _make_stream_event(text: str = None, audio_b64: str = None) -> list:
    """Build a fake streaming event sequence from Bedrock."""
    events = []
    if text:
        events.append({"chunk": {"bytes": json.dumps({
            "type": "contentBlockDelta",
            "delta": {"text": text}
        }).encode()}})
    if audio_b64:
        events.append({"chunk": {"bytes": json.dumps({
            "type": "contentBlockDelta",
            "delta": {"audio": {"data": audio_b64}}
        }).encode()}})
    events.append({"chunk": {"bytes": json.dumps({"type": "messageStop"}).encode()}})
    return events


SAMPLE_AUDIO_B64 = base64.b64encode(b"\x00\x01\x02\x03" * 100).decode()
SAMPLE_RESP_AUDIO = base64.b64encode(b"\xff\xfe" * 50).decode()


# ---------------------------------------------------------------------------
# Tests: app.py handler
# ---------------------------------------------------------------------------

@patch("voice_gateway_app.process_voice_turn")
class TestVoiceGatewayHandler(unittest.TestCase):
    """
    Uses @patch("app.process_voice_turn") at the class level so __init__ of each
    test method receives the mock as the first arg after self.
    This correctly intercepts the `from voice_service import process_voice_turn`
    binding inside app.py's lambda_handler.
    """

    def test_text_input_success(self, mock_process):
        mock_process.return_value = {
            "sessionId": "sess-1", "audioData": SAMPLE_RESP_AUDIO,
            "transcript": "Your glucose is 90.", "mimeType": "audio/mp3"
        }
        event = _post_event({"familyId": "fam-1", "text": "What is my glucose?"})
        resp = voice_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 200)
        body = _get_body(resp)
        self.assertEqual(body["transcript"], "Your glucose is 90.")
        self.assertIn("sessionId", body)

    def test_audio_input_success(self, mock_process):
        mock_process.return_value = {
            "sessionId": "sess-2", "audioData": SAMPLE_RESP_AUDIO,
            "transcript": "Hello!", "mimeType": "audio/mp3"
        }
        event = _post_event({"familyId": "fam-1", "audioData": SAMPLE_AUDIO_B64})
        resp = voice_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 200)

    def test_missing_family_id_returns_400(self, mock_process):
        event = _post_event({"text": "Hello"})
        resp = voice_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 400)
        self.assertIn("MISSING_FAMILY_ID", resp["body"])

    def test_missing_audio_and_text_returns_400(self, mock_process):
        event = _post_event({"familyId": "fam-1"})
        resp = voice_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 400)
        self.assertIn("MISSING_INPUT", resp["body"])

    def test_invalid_json_body_returns_400(self, mock_process):
        event = {"httpMethod": "POST", "path": "/voice/session", "body": "not json {{"}
        resp = voice_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 400)

    def test_unknown_route_returns_404(self, mock_process):
        event = {"httpMethod": "GET", "path": "/voice/session", "body": None}
        resp = voice_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 404)

    def test_new_session_id_minted_when_empty(self, mock_process):
        mock_process.return_value = {
            "sessionId": "new-id", "audioData": "", "transcript": "Hi", "mimeType": "audio/mp3"
        }
        event = _post_event({"familyId": "fam-1", "text": "Hi", "sessionId": ""})
        voice_app.lambda_handler(event, None)
        call_kwargs = mock_process.call_args[1]
        self.assertTrue(len(call_kwargs.get("session_id", "")) > 0)

    def test_existing_session_id_preserved(self, mock_process):
        mock_process.return_value = {
            "sessionId": "my-existing-sess", "audioData": "",
            "transcript": "Continuing.", "mimeType": "audio/mp3"
        }
        event = _post_event({"familyId": "fam-1", "text": "Hi",
                             "sessionId": "my-existing-sess"})
        voice_app.lambda_handler(event, None)
        call_kwargs = mock_process.call_args[1]
        self.assertEqual(call_kwargs["session_id"], "my-existing-sess")

    def test_throttling_returns_429(self, mock_process):
        from botocore.exceptions import ClientError
        err = ClientError({"Error": {"Code": "ThrottlingException", "Message": "slow down"}},
                          "InvokeModelWithResponseStream")
        mock_process.side_effect = err
        event = _post_event({"familyId": "fam-1", "text": "Hi"})
        resp = voice_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 429)

    def test_access_denied_returns_503(self, mock_process):
        from botocore.exceptions import ClientError
        err = ClientError({"Error": {"Code": "AccessDeniedException", "Message": "denied"}},
                          "InvokeModelWithResponseStream")
        mock_process.side_effect = err
        event = _post_event({"familyId": "fam-1", "text": "Hi"})
        resp = voice_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 503)

    def test_generic_exception_returns_500(self, mock_process):
        mock_process.side_effect = RuntimeError("unexpected")
        event = _post_event({"familyId": "fam-1", "text": "Hi"})
        resp = voice_app.lambda_handler(event, None)
        self.assertEqual(resp["statusCode"], 500)


# ---------------------------------------------------------------------------
# Tests: voice_service.py
# ---------------------------------------------------------------------------

class TestVoiceService(unittest.TestCase):

    def setUp(self):
        self.mock_bedrock = MagicMock()
        voice_service.bedrock = self.mock_bedrock

    def test_text_only_input_assembles_transcript(self):
        self.mock_bedrock.invoke_model_with_response_stream.return_value = {
            "body": _make_stream_event(text="Your glucose is 90 mg/dL.")
        }
        result = voice_service.process_voice_turn(text="What is my glucose?",
                                                   family_id="fam-1", session_id="s1")
        self.assertEqual(result["transcript"], "Your glucose is 90 mg/dL.")
        self.assertEqual(result["sessionId"], "s1")

    def test_audio_response_decoded_and_returned(self):
        self.mock_bedrock.invoke_model_with_response_stream.return_value = {
            "body": _make_stream_event(text="Hello!", audio_b64=SAMPLE_RESP_AUDIO)
        }
        result = voice_service.process_voice_turn(text="Hi", family_id="fam-1", session_id="s2")
        # audioData should be non-empty since we returned audio in stream
        self.assertTrue(len(result["audioData"]) > 0)
        # Verify it's valid base64 by decoding without error
        decoded = base64.b64decode(result["audioData"])
        self.assertIsInstance(decoded, bytes)

    def test_empty_stream_returns_empty_audio(self):
        self.mock_bedrock.invoke_model_with_response_stream.return_value = {"body": []}
        result = voice_service.process_voice_turn(text="Hi", family_id="fam-1", session_id="s3")
        self.assertEqual(result["audioData"], "")
        self.assertEqual(result["transcript"], "")

    def test_bedrock_client_error_propagates(self):
        from botocore.exceptions import ClientError
        err = ClientError({"Error": {"Code": "AccessDeniedException", "Message": "denied"}},
                          "InvokeModelWithResponseStream")
        self.mock_bedrock.invoke_model_with_response_stream.side_effect = err
        with self.assertRaises(ClientError):
            voice_service.process_voice_turn(text="Hi", family_id="fam-1", session_id="s4")

    def test_mime_type_always_audio_mp3(self):
        self.mock_bedrock.invoke_model_with_response_stream.return_value = {
            "body": _make_stream_event(text="Hi")
        }
        result = voice_service.process_voice_turn(text="Hi", family_id="fam-1", session_id="s5")
        self.assertEqual(result["mimeType"], "audio/mp3")


if __name__ == "__main__":
    unittest.main()
