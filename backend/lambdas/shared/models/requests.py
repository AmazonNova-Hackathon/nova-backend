from typing import Literal
from pydantic import BaseModel


class UploadRequest(BaseModel):
    memberId: str
    s3Key: str
    reportType: Literal["lab_report", "prescription", "doctor_note"]


class ChatMessage(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str


class ChatRequest(BaseModel):
    familyId: str
    memberId: str
    message: str
    sessionId: str = ""
    reportId: str = ""
    language: str = "English"
    responseFormat: Literal["text", "audio", "both"] = "text"
