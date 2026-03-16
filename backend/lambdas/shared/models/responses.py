from pydantic import BaseModel

from .fhir import Observation


class UploadResponse(BaseModel):
    reportId: str
    date: str | None
    labName: str | None
    observations: list[Observation]
    extractionConfidence: float
    disclaimer: str = "This is an informational summary only. Please consult your doctor."


class PreSignedUrlResponse(BaseModel):
    url: str
    reportId: str
    s3Key: str


class ErrorResponse(BaseModel):
    error: dict

    @classmethod
    def create(cls, code: str, message: str, request_id: str = "unknown"):
        return cls(error={"code": code, "message": message, "requestId": request_id})


class ChatResponse(BaseModel):
    reply: str
    sessionId: str = ""
    referencedReports: list[str] = []
    referencedObservations: list[str] = []
    audioBase64: str = ""
    disclaimer: str = (
        "MediAgent does not provide medical advice. Always consult a licensed healthcare provider."
    )
