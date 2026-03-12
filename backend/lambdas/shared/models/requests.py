from typing import Literal
from pydantic import BaseModel


class UploadRequest(BaseModel):
    patientId: str
    s3Key: str
    reportType: Literal["lab_report", "prescription", "doctor_note"]


class ChatMessage(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str


class ChatRequest(BaseModel):
    familyId: str
    message: str
    # sessionId: empty string = start new agent conversation; non-empty = continue existing session.
    # The Bedrock Agent holds multi-turn conversation history server-side via sessionId,
    # so conversationHistory is no longer needed on the client side.
    sessionId: str = ""
