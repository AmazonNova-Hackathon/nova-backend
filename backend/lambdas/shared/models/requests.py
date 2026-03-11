from typing import Literal

from pydantic import BaseModel


class UploadRequest(BaseModel):
    patientId: str
    imageBase64: str
    reportType: Literal["lab_report", "prescription", "doctor_note"]


class ChatMessage(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str


class ChatRequest(BaseModel):
    patientId: str
    message: str
    conversationHistory: list[ChatMessage] = []
