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
    patientId: str
    message: str
    conversationHistory: list[ChatMessage] = []
