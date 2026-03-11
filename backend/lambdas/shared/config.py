import os

TABLE_NAME = os.environ.get("FHIR_TABLE_NAME", "mediagent-fhir")
BUCKET_NAME = os.environ.get("S3_BUCKET_NAME", "mediagent-reports")
NOVA_MODEL_ID = os.environ.get("NOVA_MODEL_ID", "amazon.nova-lite-v1:0")
BEDROCK_REGION = os.environ.get("BEDROCK_REGION", "us-east-1")
DEMO_PATIENT_ID = os.environ.get("DEMO_PATIENT_ID", "patient-demo-001")
