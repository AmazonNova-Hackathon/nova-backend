# Complete LOINC Mapping lookup table for MediAgent

LOINC_MAP = {
    # Blood Sugar
    "glucose": {"code": "2339-0", "unit": "mg/dL"},
    "fasting glucose": {"code": "2339-0", "unit": "mg/dL"},
    "hba1c": {"code": "4548-4", "unit": "%"},
    "insulin": {"code": "20448-7", "unit": "µIU/mL"},
    
    # Lipid Panel
    "total cholesterol": {"code": "2093-3", "unit": "mg/dL"},
    "cholesterol": {"code": "2093-3", "unit": "mg/dL"},
    "ldl": {"code": "18262-6", "unit": "mg/dL"},
    "hdl": {"code": "2085-9", "unit": "mg/dL"},
    "triglycerides": {"code": "2571-8", "unit": "mg/dL"},

    # CBC (Complete Blood Count)
    "hemoglobin": {"code": "718-7", "unit": "g/dL"},
    "wbc": {"code": "6690-2", "unit": "10³/µL"},
    "white blood cells": {"code": "6690-2", "unit": "10³/µL"},
    "rbc": {"code": "789-8", "unit": "10⁶/µL"},
    "red blood cells": {"code": "789-8", "unit": "10⁶/µL"},
    "platelets": {"code": "777-3", "unit": "10³/µL"},
    "hematocrit": {"code": "4544-3", "unit": "%"},
    "mcv": {"code": "787-2", "unit": "fL"},

    # Kidney
    "creatinine": {"code": "2160-0", "unit": "mg/dL"},
    "bun": {"code": "3094-0", "unit": "mg/dL"},
    "blood urea nitrogen": {"code": "3094-0", "unit": "mg/dL"},
    "egfr": {"code": "33914-3", "unit": "mL/min/1.73m²"},
    "uric acid": {"code": "3084-1", "unit": "mg/dL"},

    # Liver
    "alt": {"code": "1742-6", "unit": "U/L"},
    "sgpt": {"code": "1742-6", "unit": "U/L"},
    "ast": {"code": "1920-8", "unit": "U/L"},
    "sgot": {"code": "1920-8", "unit": "U/L"},
    "bilirubin": {"code": "1975-2", "unit": "mg/dL"},
    "albumin": {"code": "1751-7", "unit": "g/dL"},
    "alp": {"code": "6768-6", "unit": "U/L"},
    "alkaline phosphatase": {"code": "6768-6", "unit": "U/L"},

    # Thyroid
    "tsh": {"code": "3016-3", "unit": "µIU/mL"},
    "t3": {"code": "3051-0", "unit": "pg/mL"},
    "free t3": {"code": "3051-0", "unit": "pg/mL"},
    "t4": {"code": "3054-4", "unit": "ng/dL"},
    "free t4": {"code": "3054-4", "unit": "ng/dL"},

    # Electrolytes
    "sodium": {"code": "2951-2", "unit": "mEq/L"},
    "potassium": {"code": "2823-3", "unit": "mEq/L"},
    "calcium": {"code": "17861-6", "unit": "mg/dL"},
    "magnesium": {"code": "2601-3", "unit": "mg/dL"},

    # Vitamins & Minerals
    "vitamin d": {"code": "1989-3", "unit": "ng/mL"},
    "vitamin b12": {"code": "2132-9", "unit": "pg/mL"},
    "iron": {"code": "2498-4", "unit": "µg/dL"},
    "ferritin": {"code": "2276-4", "unit": "ng/mL"},
}

def find_loinc_code(test_name: str) -> dict:
    """
    Match test name to LOINC code by converting to lowercase.
    Returns {"code": "unknown", "unit": "unknown"} if no match.
    """
    clean_name = test_name.lower().strip()
    # Direct match first
    if clean_name in LOINC_MAP:
        return LOINC_MAP[clean_name]
    
    # Try substring match (e.g. "serum glucose" -> "glucose")
    for key, value in LOINC_MAP.items():
        if key in clean_name:
            return value

    return {"code": "unknown", "unit": "unknown"}
