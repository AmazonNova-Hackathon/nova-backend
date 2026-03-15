"""
Agent Service — Chat via Bedrock Agent Runtime

Replaces the old bedrock-runtime.converse manual tool-use loop.
The Bedrock Agent (with its Action Group Lambdas) now handles all tool
dispatch and multi-turn memory. This service simply:
  1. Calls bedrock-agent-runtime.invoke_agent
  2. Consumes the EventStream, assembling text chunks
  3. Returns a ChatResponse with the assembled reply and the sessionId
"""
import uuid
import os
from botocore.config import Config
from botocore.exceptions import ClientError
import boto3
from aws_lambda_powertools import Logger

from lambdas.shared.models.requests import ChatRequest
from lambdas.shared.models.responses import ChatResponse

logger = Logger(service="agent-chat")

AGENT_ID = os.environ.get("AGENT_ID", "")
AGENT_ALIAS_ID = os.environ.get("AGENT_ALIAS_ID", "")
BEDROCK_REGION = os.environ.get("BEDROCK_REGION", "us-east-1")

retry_config = Config(retries={"max_attempts": 5, "mode": "standard"})
agent_runtime = boto3.client(
    "bedrock-agent-runtime",
    region_name=BEDROCK_REGION,
    config=retry_config,
)


def process_chat(raw_payload: dict) -> ChatResponse:
    """
    Invoke the Bedrock Agent and assemble the streamed response.

    Session continuity:
      - Client sends sessionId="" to start a new conversation.
      - On first call we generate a UUID and use it as sessionId.
      - On subsequent turns the client echoes the same sessionId back
        so the Bedrock Agent recalls context from previous turns.

    Family context:
      - familyId is injected as a sessionState attribute so the agent's
        Action Group instructions can reference it and pass it to the
        Action Group Lambda functions.
    """
    req = ChatRequest(**raw_payload)

    # Use the client-supplied session or mint a fresh one
    session_id = req.sessionId if req.sessionId else str(uuid.uuid4())

    logger.info(
        "Invoking Bedrock Agent",
        extra={
            "agentId": AGENT_ID,
            "agentAliasId": AGENT_ALIAS_ID,
            "sessionId": session_id,
            "familyId": req.familyId,
        },
    )

    try:
        response = agent_runtime.invoke_agent(
            agentId=AGENT_ID,
            agentAliasId=AGENT_ALIAS_ID,
            sessionId=session_id,
            inputText=req.message,
            sessionState={
                # Session attributes are available to Action Group instructions
                # and can be forwarded to action group Lambda functions.
                "sessionAttributes": {
                    "familyId": req.familyId,
                    "memberId": req.memberId,
                    "reportId": req.reportId,
                    "language": req.language,
                },
                "promptSessionAttributes": {
                    "instructions": f"Please respond to the user in {req.language}. If you are providing medical interpretations, ensure they are grounded in the provided context but delivered in {req.language}."
                }
            },
        )

        # Consume the EventStream — assemble all text chunk bytes into a reply.
        # API Gateway doesn't support true HTTP streaming, so we collect everything
        # before returning. The sessionId is preserved for the next client turn.
        reply_parts = []
        for event in response["completion"]:
            if "chunk" in event:
                chunk_bytes = event["chunk"].get("bytes", b"")
                if chunk_bytes:
                    reply_parts.append(chunk_bytes.decode("utf-8"))

        reply = "".join(reply_parts).strip()

        if not reply:
            reply = (
                "I wasn't able to retrieve the information you requested. "
                "Please try rephrasing your question."
            )

    except ClientError as e:
        error_code = e.response["Error"]["Code"]
        if error_code == "AccessDeniedException":
            logger.error("Bedrock Agent access denied — check IAM permissions for bedrock:InvokeAgent")
            reply = "I'm unable to access the health assistant right now. Please try again shortly."
        elif error_code == "ResourceNotFoundException":
            logger.error(
                "Bedrock Agent not found",
                extra={"agentId": AGENT_ID, "agentAliasId": AGENT_ALIAS_ID},
            )
            reply = "The health assistant is not configured. Please contact support."
        else:
            logger.exception("Bedrock ClientError invoking agent")
            reply = "I'm having trouble processing your request right now. Please try again later."

    except Exception:
        logger.exception("Unexpected non-AWS error invoking Bedrock Agent")
        reply = "I'm having trouble processing your request right now. Please try again later."

    return ChatResponse(
        reply=reply,
        sessionId=session_id,
    )
