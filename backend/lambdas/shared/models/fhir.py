from typing import Literal, Optional, List
from pydantic import BaseModel

class MetaData(BaseModel):
    createdAt: str
    updatedAt: str
    createdBy: str
    updatedBy: str
    version: int = 1
    isDeleted: bool = False

class Family(BaseModel):
    id: str  # familyId
    name: Optional[str] = None
    meta: Optional[MetaData] = None

class Member(BaseModel):
    id: str  # memberId
    familyId: str
    name: str
    age: Optional[int] = None
    gender: Optional[str] = None
    relationship: str  # e.g., "Self", "Spouse", "Mother", "Language"
    meta: Optional[MetaData] = None

class InsightCard(BaseModel):
    id: str  # insightId
    memberId: str
    severity: Literal["urgent", "attention", "informational"]
    title: str
    summary: str
    details: str
    citedObservations: List[str] = []
    citedReports: List[str] = []
    suggestedAction: Optional[str] = None
    generatedAt: str
    read: bool = False
    language: str = "en"
    disclaimer: str
    meta: Optional[MetaData] = None

class FollowUp(BaseModel):
    id: str  # followUpId
    memberId: str
    testName: str
    loincCode: str
    reason: str
    suggestedDate: str
    basedOnObservations: List[str] = []
    status: Literal["pending", "accepted", "dismissed"]
    createdAt: str
    meta: Optional[MetaData] = None

class Observation(BaseModel):
    id: str  # UUID
    name: str  # "Glucose"
    loincCode: str  # "2339-0"
    value: float  # 110
    unit: str  # "mg/dL"
    normalLow: Optional[float] = None
    normalHigh: Optional[float] = None
    isAbnormal: bool
    interpretation: Literal["N", "H", "L", "HH", "LL", "U"]
    date: Optional[str] = None  # YYYY-MM-DD
    reportId: str
    memberId: str
    meta: Optional[MetaData] = None

class DiagnosticReport(BaseModel):
    reportId: str
    familyId: str
    memberId: str
    reportType: str
    date: Optional[str] = None
    labName: Optional[str] = None
    totalObservations: int = 0
    abnormalCount: int = 0
    s3Key: Optional[str] = None
    status: str = "uploading"
    meta: Optional[MetaData] = None
