"""
Lambda 4: Voice Gateway
Handler: app.lambda_handler

POST /voice/session

Request body:
  {
    "familyId":  "fam-xxx",        # required — passed to Nova Sonic as context
    "sessionId": "uuid-or-empty",  # optional — empty = new session
    "audioData": "<base64>",       # optional — raw audio input (base64 encoded)
    "text":      "Hello?",         # optional — text fallback if no audio
    "mimeType":  "audio/lpcm"      # optional — defaults to audio/lpcm
  }

Response body (200):
  {
    "sessionId":  "uuid",          # echo this back on next turn
    "audioData":  "<base64>",      # base64-encoded MP3 response audio
    "transcript": "...",           # Nova Sonic's text transcript
    "mimeType":   "audio/mp3"
  }

At least one of `audioData` or `text` must be present.
"""
import _bootstrap  # noqa: F401 — must be first

import json
import uuid
import traceback
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.typing import LambdaContext
from botocore.exceptions import ClientError

from voice_service import process_voice_turn

logger = Logger(service="voice-gateway")


def _ok(body: dict) -> dict:
    return {
        "statusCode": 200,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
        },
        "body": json.dumps(body),
    }


def _err(status: int, code: str, message: str) -> dict:
    return {
        "statusCode": status,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
        },
        "body": json.dumps({"error": {"code": code, "message": message}}),
    }


@logger.inject_lambda_context(log_event=False)  # Don't log audio payloads (too large + PII)
def lambda_handler(event: dict, context: LambdaContext) -> dict:
    method = event.get("httpMethod", "")
    path = event.get("path", "")

    if method != "POST" or path != "/voice/session":
        return _err(404, "NOT_FOUND", f"Route not found: {method} {path}")

    # Parse and validate request
    try:
        body = json.loads(event.get("body") or "{}")
    except json.JSONDecodeError:
        return _err(400, "INVALID_JSON", "Request body must be valid JSON")

    family_id = body.get("familyId")
    if not family_id:
        return _err(400, "MISSING_FAMILY_ID", "familyId is required")

    audio_b64 = body.get("audioData")
    text = body.get("text")

    if not audio_b64 and not text:
        return _err(400, "MISSING_INPUT", "At least one of audioData or text must be provided")

    session_id = body.get("sessionId") or str(uuid.uuid4())
    mime_type = body.get("mimeType", "audio/lpcm")

    logger.info(
        "Processing voice turn",
        extra={
            "sessionId": session_id,
            "familyId": family_id,
            "hasAudio": bool(audio_b64),
            "hasText": bool(text),
        },
    )

    try:
        result = process_voice_turn(
            audio_b64=audio_b64,
            text=text,
            session_id=session_id,
            family_id=family_id,
            mime_type=mime_type,
        )
        return _ok(result)

    except ClientError as e:
        code = e.response["Error"]["Code"]
        logger.error("Bedrock ClientError in voice gateway", extra={"code": code})

        if code == "AccessDeniedException":
            return _err(503, "VOICE_UNAVAILABLE",
                        "Voice service is temporarily unavailable. Please try again.")
        if code == "ThrottlingException":
            return _err(429, "RATE_LIMITED",
                        "Too many requests. Please wait a moment and try again.")
        if code == "ValidationException":
            return _err(400, "INVALID_AUDIO",
                        "Audio format not supported. Please use lpcm or pcm encoding.")
        return _err(503, "BEDROCK_ERROR", "Voice model returned an error. Please try again.")

    except Exception:
        logger.exception("Unexpected error in voice gateway")
        return _err(500, "INTERNAL_ERROR",
                    "An unexpected error occurred processing your voice request.")
