from typing import Literal, Optional

from pydantic import BaseModel


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


class DiagnosticReport(BaseModel):
    reportId: str
    patientId: str
    reportType: str
    date: Optional[str] = None
    labName: Optional[str] = None
    totalObservations: int = 0
    abnormalCount: int = 0
    s3Key: Optional[str] = None
