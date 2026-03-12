"""
Voice Service — Amazon Nova 2 Sonic integration via Bedrock.

Architecture note:
  Nova 2 Sonic's native API is `InvokeModelWithBidirectionalStream`, which
  requires a full-duplex WebSocket connection. API Gateway REST APIs do not
  support persistent bidirectional streams, so this Lambda implements a
  practical REST-friendly adaptation:

  Client pattern (turn-based voice):
    1. Client records an audio segment (up to ~30s) and base64-encodes it.
    2. Client POSTs {familyId, sessionId, audioData (base64), mimeType, text?}
       to POST /voice/session.
    3. This Lambda:
         a. Sends the audio (or text fallback) to Nova Sonic via
            InvokeModelWithResponseStream.
         b. Collects the streamed response chunks into one audio payload.
         c. Returns {sessionId, audioData (base64), transcript, mimeType}.
    4. Client decodes audioData and plays it back.

  For a true real-time bidirectional voice experience (sub-300ms latency),
  a WebSocket API Gateway → persistent Lambda or ECS task would be needed.
  This endpoint is the REST bridge that enables the MVP demo flow.

IAM:  bedrock:InvokeModelWithResponseStream  (already in template.yaml)
Model: amazon.nova-2-sonic-v1:0
"""
import json
import base64
import os
from botocore.config import Config
from botocore.exceptions import ClientError
import boto3
from aws_lambda_powertools import Logger

from lambdas.shared.config import BEDROCK_REGION

logger = Logger(service="voice-gateway")

SONIC_MODEL_ID = "amazon.nova-2-sonic-v1:0"
BEDROCK_REGION_VOICE = os.environ.get("BEDROCK_REGION", BEDROCK_REGION)

retry_config = Config(retries={"max_attempts": 3, "mode": "standard"})
bedrock = boto3.client(
    "bedrock-runtime",
    region_name=BEDROCK_REGION_VOICE,
    config=retry_config,
)

SYSTEM_PROMPT = (
    "You are MediAgent, a friendly health record voice assistant. "
    "Help patients understand their own lab results in plain, conversational language. "
    "NEVER diagnose conditions or recommend treatments. "
    "Keep answers concise — this is a voice conversation. "
    "Always end by saying they should consult their doctor."
)


def _build_sonic_payload(audio_b64: str = None, text: str = None,
                         mime_type: str = "audio/lpcm", session_id: str = "",
                         family_id: str = "") -> dict:
    """
    Build the Nova Sonic InvokeModelWithResponseStream request body.

    Nova Sonic accepts multi-modal input (audio + text) in a conversational
    message format. We pass audio when available, falling back to text.
    """
    content = []

    if audio_b64:
        content.append({
            "audio": {
                "source": {"bytes": audio_b64},
                "format": mime_type.split("/")[-1].upper(),  # e.g. "LPCM", "PCM", "MP3"
            }
        })

    if text:
        content.append({"text": text})

    if not content:
        # Shouldn't happen if caller validates, but be defensive
        content.append({"text": "Hello"})

    return {
        "system": [{"text": SYSTEM_PROMPT + f" The user's family ID is {family_id}."}],
        "messages": [
            {"role": "user", "content": content}
        ],
        "inferenceConfig": {
            "maxTokens": 512,
            "temperature": 0.7,
        },
        # Request audio output from Nova Sonic
        "audioOutput": {
            "voiceId": "tiffany",          # Default Nova Sonic English voice
            "encoding": "base64",
            "format": "mp3",
        },
    }


def process_voice_turn(
    audio_b64: str = None,
    text: str = None,
    session_id: str = "",
    family_id: str = "",
    mime_type: str = "audio/lpcm",
) -> dict:
    """
    Send one voice turn to Nova Sonic and collect the streaming response.

    Returns:
        {
            "sessionId":   str,
            "audioData":   base64-encoded MP3 response (may be empty if model
                           returns text-only),
            "transcript":  str (model's text response),
            "mimeType":    "audio/mp3",
        }
    """
    payload = _build_sonic_payload(
        audio_b64=audio_b64,
        text=text,
        mime_type=mime_type,
        session_id=session_id,
        family_id=family_id,
    )

    try:
        response = bedrock.invoke_model_with_response_stream(
            modelId=SONIC_MODEL_ID,
            body=json.dumps(payload),
            contentType="application/json",
            accept="application/json",
        )
    except ClientError as e:
        code = e.response["Error"]["Code"]
        logger.error("Bedrock ClientError in voice gateway", extra={"code": code})
        raise

    # Consume the stream — accumulate text transcript and raw audio bytes
    transcript_parts = []
    audio_chunks = []

    for event in response.get("body", []):
        chunk = event.get("chunk", {})
        raw = chunk.get("bytes", b"")
        if not raw:
            continue
        try:
            parsed = json.loads(raw.decode("utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            # Raw audio chunk (binary) — accumulate as-is
            audio_chunks.append(raw)
            continue

        # Nova Sonic streams JSON events with different types
        event_type = parsed.get("type", "")

        if event_type == "contentBlockDelta":
            delta = parsed.get("delta", {})
            if "text" in delta:
                transcript_parts.append(delta["text"])
            elif "audio" in delta:
                # base64-encoded audio delta from Nova Sonic
                audio_b64_chunk = delta["audio"].get("data", "")
                if audio_b64_chunk:
                    audio_chunks.append(base64.b64decode(audio_b64_chunk))

        elif event_type == "messageStop":
            break  # Stream complete

    transcript = "".join(transcript_parts).strip()
    final_audio_bytes = b"".join(audio_chunks)
    final_audio_b64 = base64.b64encode(final_audio_bytes).decode("utf-8") if final_audio_bytes else ""

    logger.info(
        "Voice turn complete",
        extra={
            "sessionId": session_id,
            "transcriptLen": len(transcript),
            "audioBytes": len(final_audio_bytes),
        },
    )

    return {
        "sessionId": session_id,
        "audioData": final_audio_b64,
        "transcript": transcript,
        "mimeType": "audio/mp3",
    }
