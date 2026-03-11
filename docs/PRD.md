# MediAgent — AWS Backend PRD

**What:** Serverless backend for an Android app that extracts lab data from photos using Amazon Nova, stores it as FHIR R4, and lets users chat with their health records via a Strands Agent.

**Demo Patient ID:** `patient-demo-001` (hardcoded, no auth for hackathon)
**Region:** `us-east-1`
**Nova Model:** `amazon.nova-lite-v1:0`

---

## AWS Services — What You're Deploying

| Service | Resource Name | What It Does |
|---|---|---|
| API Gateway | `MediAgentApi` (stage: prod) | REST API entry point. CORS open for Android. |
| Lambda #1 | `mediagent-extract-report` | Nova extraction + FHIR storage. Handles 3 routes. |
| Lambda #2 | `mediagent-agent-chat` | Strands Agent chat. Reads DynamoDB, reasons with Nova. |
| DynamoDB | `mediagent-fhir` | Single-table. All reports + observations. PAY_PER_REQUEST. SSE on. |
| S3 | `mediagent-reports-{accountId}` | Original report images. AES-256. All public access blocked. |
| Bedrock | `amazon.nova-lite-v1:0` | Multimodal extraction + agentic reasoning. |

---

## Setup — Step by Step (Beginner-Friendly)

### 1. Install Tools

```bash
# AWS CLI
# Download from https://aws.amazon.com/cli — follow installer for your OS
aws --version        # verify

# SAM CLI
pip install aws-sam-cli
sam --version        # verify

# Python 3.12 — download from python.org
python3 --version    # verify

# Docker Desktop — download from docker.com (needed for sam build)
docker --version     # verify
```

### 2. Create an IAM User (Don't Use Root)

Log in as root to create this user, then switch to it for all development work.

**2a. Create the user:**
1. Search **"IAM"** in the AWS console top bar → click IAM
2. Left sidebar → **Users** → **Create user**
3. User name: `mediagent-dev`
4. Check **"Provide user access to the AWS Management Console"**
5. Set a custom password or let AWS auto-generate one

**2b. Attach permissions:**
1. On the permissions page, choose **"Attach policies directly"**
2. Search for `AdministratorAccess` → check it (narrow this post-hackathon)
3. Click through → Create user

**2c. Create access keys for CLI:**
1. Click into the `mediagent-dev` user you just created
2. Go to **Security credentials** tab
3. Scroll to **Access keys** → **Create access key**
4. Use case: select **"Command Line Interface (CLI)"**
5. Check the acknowledgment box → proceed
6. **Copy both Access Key ID and Secret Access Key immediately** — the secret is shown only once. Save in a password manager.

**2d. Lock down root (do this while still logged in as root):**
1. Go to root account → **Security credentials** (top-right dropdown)
2. Delete any existing root access keys
3. Enable **MFA** on root (use an authenticator app)
4. Log out of root → log back in as `mediagent-dev`

### 3. Configure CLI

```bash
aws configure
# Access Key ID:     <paste from step 2c>
# Secret Access Key: <paste from step 2c>
# Default region:    us-east-1
# Output format:     json
```

Verify you're using the right identity:
```bash
aws sts get-caller-identity
# Should show "mediagent-dev" in the ARN, NOT "root"
```

### 4. Enable Bedrock Model Access

> ⚠️ **This is the #1 "why is it broken" mistake.** Bedrock models are OFF by default.

1. Open **Amazon Bedrock console** in us-east-1
2. Left sidebar → **Model access**
3. Click **Manage model access**
4. Enable **Amazon Nova Lite** (`amazon.nova-lite-v1:0`)
5. Wait for status: "Access granted" (usually instant)

### 5. Build & Deploy

```bash
# First time — builds Lambda packages
sam build

# First time — interactive guided deploy
sam deploy --guided
```

**Guided deploy prompts:**

| Prompt | Enter |
|---|---|
| Stack Name | `mediagent` |
| AWS Region | `us-east-1` |
| Confirm changes before deploy? | `Y` |
| Allow SAM CLI IAM role creation? | `Y` |
| Disable rollback? | `N` |
| Save arguments to samconfig.toml? | `Y` |

```bash
# Every time after first deploy
sam build && sam deploy
```

### 6. Get Your API URL

```bash
aws cloudformation describe-stacks \
  --stack-name mediagent \
  --query "Stacks[0].Outputs"
```

Copy `ApiBaseUrl` → paste into Android Retrofit client.

---

## API Endpoints

**Base URL:** `https://<id>.execute-api.us-east-1.amazonaws.com/prod`

### POST /reports/upload
Upload a lab report image → Nova extracts → stores in DynamoDB + S3.

**Request:**
```json
{
  "patientId": "patient-demo-001",
  "imageBase64": "<base64 JPEG, max 1MB>",
  "reportType": "lab_report | prescription | doctor_note"
}
```

**Response 200:**
```json
{
  "reportId": "report-uuid",
  "date": "2024-03-01",
  "labName": "City Diagnostics",
  "observations": [
    {
      "id": "obs-uuid-1",
      "name": "Glucose",
      "loincCode": "2339-0",
      "value": 110,
      "unit": "mg/dL",
      "normalLow": 70,
      "normalHigh": 100,
      "isAbnormal": true,
      "interpretation": "H"
    }
  ],
  "extractionConfidence": 0.95,
  "disclaimer": "This is an informational summary only. Please consult your doctor."
}
```

**Errors:** `400` invalid image / `500` Nova extraction failed

---

### GET /reports
List all uploaded reports.

**Query:** `?patientId=patient-demo-001`

**Response 200:**
```json
{
  "patientId": "patient-demo-001",
  "reports": [
    {
      "reportId": "report-uuid",
      "reportType": "lab_report",
      "date": "2024-03-01",
      "labName": "City Diagnostics",
      "totalObservations": 8,
      "abnormalCount": 2
    }
  ],
  "total": 5
}
```

---

### GET /observations
Get extracted lab values with optional filters.

**Query Params:**

| Param | Required | Example |
|---|---|---|
| `patientId` | Yes | `patient-demo-001` |
| `loincCode` | No | `2339-0` (Glucose) |
| `fromDate` | No | `2024-01-01` |
| `toDate` | No | `2024-12-31` |

---

### POST /chat
Ask questions about your health records via the Strands Agent.

**Request:**
```json
{
  "patientId": "patient-demo-001",
  "message": "Has my glucose been improving?",
  "conversationHistory": [
    { "role": "user", "content": "Show me my last report" },
    { "role": "assistant", "content": "Your last report from March 1st showed..." }
  ]
}
```

**Response 200:**
```json
{
  "reply": "Your glucose was 95 in January, rose to 102 in February, and is now 110...",
  "referencedReports": ["report-uuid-1", "report-uuid-2"],
  "referencedObservations": ["obs-uuid-1", "obs-uuid-2"],
  "disclaimer": "MediAgent does not provide medical advice. Always consult a licensed healthcare provider."
}
```

---

## Error Format (All Endpoints)

```json
{
  "error": {
    "code": "EXTRACTION_FAILED | NOT_FOUND | VALIDATION_ERROR | AGENT_ERROR | BEDROCK_THROTTLE",
    "message": "Human readable error message",
    "requestId": "lambda-request-id"
  }
}
```

---

## DynamoDB — Single Table Design

**Table:** `mediagent-fhir` | **Billing:** PAY_PER_REQUEST | **Encryption:** SSE enabled

| Access Pattern | PK | SK Condition |
|---|---|---|
| All reports | `PATIENT#patient-demo-001` | `begins_with REPORT#` |
| All observations | `PATIENT#patient-demo-001` | `begins_with OBS#` |
| Filter by LOINC | `PATIENT#patient-demo-001` | `begins_with OBS#2339-0#` |
| Single report | `PATIENT#patient-demo-001` | `REPORT#2024-03-01#uuid` |

**Report item (SK = `REPORT#date#uuid`):**
`reportId`, `labName`, `date`, `totalObservations`, `abnormalCount`, `s3Key`

**Observation item (SK = `OBS#loincCode#date#uuid`):**
`obsId`, `name`, `loincCode`, `value`, `unit`, `normalLow`, `normalHigh`, `isAbnormal`, `interpretation`, `date`, `reportId`

**Interpretation codes:** `N` = Normal 🟢 | `H` = High 🔴 | `L` = Low 🔵 | `HH` = Critical High 🚨 | `LL` = Critical Low 🚨

---

## Lambda Specs

### Lambda 1: `mediagent-extract-report`

| Property | Value |
|---|---|
| Runtime | Python 3.12 |
| Handler | `app.lambda_handler` |
| Timeout | 30s |
| Memory | 512 MB |
| Code path | `lambdas/extract_report/` |
| Routes | `POST /reports/upload`, `GET /reports`, `GET /observations` |

**IAM:** DynamoDB CRUD on `mediagent-fhir` + S3 CRUD on reports bucket + `bedrock:InvokeModel`

**Processing flow:**
1. Validate JSON (patientId, imageBase64, reportType)
2. Decode base64 → upload to S3 as `patient-demo-001/report-{uuid}.jpg`
3. Invoke Nova via `bedrock:InvokeModel` with image + extraction prompt
4. Parse response → map each test to LOINC code (unknown if no match)
5. Determine interpretation (H/L/N/HH/LL) from lab's printed reference range
6. Batch write DiagnosticReport + Observation items to DynamoDB
7. Return structured response with confidence + disclaimer

### Lambda 2: `mediagent-agent-chat`

| Property | Value |
|---|---|
| Runtime | Python 3.12 |
| Handler | `app.lambda_handler` |
| Timeout | 60s |
| Memory | 1024 MB |
| Code path | `lambdas/agent_chat/` |
| Routes | `POST /chat` |
| Framework | Strands Agents |

**IAM:** DynamoDB **read-only** on `mediagent-fhir` + `bedrock:InvokeModel` + `bedrock:InvokeModelWithResponseStream`

**Agent tools:**

| Tool | What It Does |
|---|---|
| `get_all_reports` | Query all DiagnosticReport items for the patient |
| `get_observations` | Query observations with optional LOINC + date filters |
| `get_report_detail` | Fetch single report + its observations |
| `compute_trend` | Retrieve history for a LOINC code, compute trend direction |

---

## How Amazon Nova AI Powers This App

Nova is used in both Lambdas, but for fundamentally different jobs.

### Lambda 1 — Nova as "Eyes" (Multimodal Vision)

When the user uploads a photo, your Python code sends the image + a text instruction to Nova via Bedrock. Nova *looks* at the image, reads the printed text (test names, values, ranges), and returns structured JSON. Your code then maps those to LOINC codes and writes to DynamoDB.

```python
bedrock = boto3.client('bedrock-runtime', region_name='us-east-1')

response = bedrock.invoke_model(
    modelId='amazon.nova-lite-v1:0',
    body=json.dumps({
        "messages": [{
            "role": "user",
            "content": [
                {
                    "image": {
                        "format": "jpeg",
                        "source": {"bytes": image_base64}
                    }
                },
                {
                    "text": "Extract all lab test results from this report. Return JSON..."
                }
            ]
        }]
    })
)
```

Image + text instruction together = "multimodal". This is the core AI capability.

### Lambda 2 — Nova as "Brain" (Agentic Reasoning via Strands)

The Strands Agent uses Nova as its thinking engine. It doesn't just answer — it reasons in a loop, deciding which tools to call:

```python
from strands import Agent
from strands.models.bedrock import BedrockModel

model = BedrockModel(model_id='amazon.nova-lite-v1:0', region_name='us-east-1')
agent = Agent(model=model, system_prompt="...", tools=[get_observations, compute_trend, ...])
response = agent("Has my glucose been improving?")
```

Example reasoning chain:
```
User: "Has my glucose been improving?"
  → Nova thinks: "I need glucose history"
  → Calls get_observations(loincCode="2339-0")
  → Gets: [{Jan: 95}, {Feb: 102}, {Mar: 110}]
  → Nova thinks: "Rising trend. Let me check HbA1c too"
  → Calls get_observations(loincCode="4548-4")
  → Gets: [{Mar: 6.2}]
  → Composes: "Your glucose has gradually increased (95→102→110).
     Your HbA1c is 6.2%. Please consult your doctor for medical advice."
```

That multi-step tool selection is what makes it "agentic" — and what hackathon judges are looking for.

### How Auth Works for Nova Calls

Your code never passes API keys to Nova. Lambda functions get an **IAM execution role** automatically from SAM (defined in `template.yaml`). When your Python code calls `boto3.client('bedrock-runtime')`, it picks up permissions from that role. No keys in code, ever.

**Same model, two jobs:** reading images in Lambda 1, thinking and reasoning in Lambda 2. That's your hackathon story — "One AI model powering both vision extraction and agentic reasoning."

---

## Lambda Architecture — Layered, Not Throwaway

This isn't throwaway hackathon code. The architecture follows clean separation of concerns so you can swap out any layer later (different AI model, different database, add auth) without rewriting everything.

### Design Principles

1. **Layered architecture** — handler → service → repository. Each layer has one job.
2. **Typed models everywhere** — Pydantic models define every request, response, and domain object. No raw dicts flying around.
3. **Repository pattern** — DynamoDB access is isolated. If you switch to PostgreSQL later, you change one file.
4. **Service layer** — business logic lives here, not in the handler and not in the repository.
5. **No silent failures** — every exception is caught, logged, and returned as a structured error.
6. **Config via environment variables** — table names, model IDs, bucket names come from SAM template, never hardcoded in Python.

### Project Structure

```
backend/
├── template.yaml                          # SAM template (infra as code)
│
├── lambdas/
│   ├── shared/                            # Shared code across Lambdas
│   │   ├── models/
│   │   │   ├── __init__.py
│   │   │   ├── fhir.py                    # FHIR domain models (Observation, DiagnosticReport)
│   │   │   ├── requests.py                # API request models (UploadRequest, ChatRequest)
│   │   │   └── responses.py               # API response models (UploadResponse, ChatResponse, ErrorResponse)
│   │   ├── repositories/
│   │   │   ├── __init__.py
│   │   │   ├── dynamo_repository.py       # All DynamoDB read/write operations
│   │   │   └── s3_repository.py           # All S3 upload/download operations
│   │   ├── config.py                      # Environment variables + constants
│   │   └── loinc_mapping.py               # LOINC code lookup table
│   │
│   ├── extract_report/
│   │   ├── app.py                         # Lambda handler (routing only)
│   │   ├── extraction_service.py          # Nova extraction logic
│   │   ├── report_service.py              # Report + observation CRUD logic
│   │   └── requirements.txt
│   │
│   └── agent_chat/
│       ├── app.py                         # Lambda handler
│       ├── agent_service.py               # Strands Agent setup + execution
│       ├── agent_tools.py                 # @tool functions for DynamoDB queries
│       ├── prompts.py                     # System prompt + safety rules (separate file, easy to tune)
│       └── requirements.txt
```

### Why This Structure Matters

Think of it like Android MVVM — you wouldn't put Retrofit calls inside your Composable. Same principle:

| Android (what you know) | Lambda (same idea) |
|---|---|
| Composable Screen | `app.py` handler — receives request, returns response |
| ViewModel | `*_service.py` — orchestrates business logic |
| Repository | `repositories/` — talks to DynamoDB, S3, Bedrock |
| Data classes | `models/` — Pydantic models (like Kotlin data classes) |
| strings.xml | `prompts.py` — AI prompts separated from logic |
| BuildConfig | `config.py` — environment variables |

---

### Layer Details

#### Layer 1: Handler (`app.py`) — Routing Only

The handler does three things and nothing else: parse the incoming event, call the right service, return the response.

```python
# extract_report/app.py — thin routing layer
def lambda_handler(event, context):
    method = event['httpMethod']
    path = event['path']

    try:
        if method == 'POST' and path == '/reports/upload':
            return handle_upload(event)
        elif method == 'GET' and path == '/reports':
            return handle_get_reports(event)
        elif method == 'GET' and path == '/observations':
            return handle_get_observations(event)
        else:
            return error_response(404, "NOT_FOUND", "Route not found")
    except ValidationError as e:
        return error_response(400, "VALIDATION_ERROR", str(e))
    except Exception as e:
        logger.exception("Unhandled error")
        return error_response(500, "INTERNAL_ERROR", "Something went wrong")
```

No business logic here. No DynamoDB calls. No Nova calls. Just routing + error boundary.

#### Layer 2: Service (`*_service.py`) — Business Logic

This is where the interesting work happens. The service orchestrates calls between repositories and AI.

**`extraction_service.py`** — handles the Nova multimodal pipeline:
1. Takes raw base64 image
2. Calls Bedrock `invoke_model` with image + extraction prompt
3. Parses Nova's JSON response
4. Maps each test name → LOINC code using `loinc_mapping.py`
5. Determines interpretation (H/L/N/HH/LL) from extracted reference ranges
6. Returns typed `ExtractionResult` objects

**`report_service.py`** — handles CRUD operations:
1. Takes `ExtractionResult` from extraction service
2. Builds FHIR-shaped DynamoDB items (DiagnosticReport + Observations)
3. Calls repository to batch write
4. Also handles GET /reports and GET /observations by calling repository queries

**`agent_service.py`** — handles the Strands Agent:
1. Initializes the agent with Nova model, system prompt from `prompts.py`, and tools from `agent_tools.py`
2. Passes user message + conversation history
3. Captures referenced reports/observations from tool calls
4. Returns structured response with citations + disclaimer

#### Layer 3: Repository (`repositories/`) — Data Access Only

Pure data access. No business logic. No AI calls.

**`dynamo_repository.py`:**
```python
class DynamoRepository:
    def __init__(self, table_name: str):
        self.table = boto3.resource('dynamodb').Table(table_name)

    def put_report(self, report: DiagnosticReportItem) -> None: ...
    def put_observations(self, observations: list[ObservationItem]) -> None: ...
    def get_reports(self, patient_id: str) -> list[dict]: ...
    def get_observations(self, patient_id: str, loinc_code: str = None,
                         from_date: str = None, to_date: str = None) -> list[dict]: ...
    def get_report_detail(self, patient_id: str, report_id: str) -> dict: ...
```

**`s3_repository.py`:**
```python
class S3Repository:
    def __init__(self, bucket_name: str):
        self.s3 = boto3.client('s3')
        self.bucket = bucket_name

    def upload_image(self, key: str, image_bytes: bytes) -> str: ...
```

If you ever move to PostgreSQL or Aurora, you write a new repository class with the same interface. Nothing else changes.

#### Layer 4: Models (`models/`) — Typed Everything

Every request, response, and domain object is a Pydantic model. This gives you automatic validation, serialization, and documentation — similar to Kotlin data classes with built-in JSON parsing.

**`models/requests.py`:**
```python
class UploadRequest(BaseModel):
    patientId: str
    imageBase64: str          # validated: must be valid base64
    reportType: Literal["lab_report", "prescription", "doctor_note"]

class ChatRequest(BaseModel):
    patientId: str
    message: str              # min length 1
    conversationHistory: list[ChatMessage] = []  # max 10 items
```

**`models/fhir.py`:**
```python
class Observation(BaseModel):
    id: str                   # UUID
    name: str                 # "Glucose"
    loincCode: str            # "2339-0"
    value: float              # 110
    unit: str                 # "mg/dL"
    normalLow: float
    normalHigh: float
    isAbnormal: bool
    interpretation: Literal["N", "H", "L", "HH", "LL"]
    date: str                 # YYYY-MM-DD
    reportId: str
```

**`models/responses.py`:**
```python
class UploadResponse(BaseModel):
    reportId: str
    date: str
    labName: str
    observations: list[Observation]
    extractionConfidence: float
    disclaimer: str = "This is an informational summary only. Please consult your doctor."

class ErrorResponse(BaseModel):
    code: str
    message: str
    requestId: str
```

#### Config (`config.py`) — No Hardcoded Values

```python
import os

TABLE_NAME = os.environ['FHIR_TABLE_NAME']       # from SAM template
BUCKET_NAME = os.environ['S3_BUCKET_NAME']        # from SAM template
NOVA_MODEL_ID = os.environ.get('NOVA_MODEL_ID', 'amazon.nova-lite-v1:0')
BEDROCK_REGION = os.environ.get('BEDROCK_REGION', 'us-east-1')
DEMO_PATIENT_ID = os.environ.get('DEMO_PATIENT_ID', 'patient-demo-001')
```

These environment variables are set in `template.yaml` under `Globals.Function.Environment.Variables` — already defined in the SAM template.

#### LOINC Mapping (`loinc_mapping.py`) — Lookup Table

```python
LOINC_MAP = {
    "glucose":          {"code": "2339-0",  "unit": "mg/dL"},
    "hba1c":            {"code": "4548-4",  "unit": "%"},
    "hemoglobin":       {"code": "718-7",   "unit": "g/dL"},
    "total cholesterol": {"code": "2093-3", "unit": "mg/dL"},
    # ... all 30 tests from the LOINC reference
}

def find_loinc_code(test_name: str) -> dict:
    """Fuzzy match test name to LOINC code. Returns {"code": "unknown"} if no match."""
```

#### Prompts (`prompts.py`) — Separated From Logic

```python
EXTRACTION_PROMPT = """Extract all lab test results from this medical report image.
Return ONLY valid JSON in this exact format:
{
  "labName": "name of the lab",
  "date": "YYYY-MM-DD",
  "tests": [
    {"name": "...", "value": 0.0, "unit": "...", "refLow": 0.0, "refHigh": 0.0}
  ]
}
Rules:
- Only extract values that are visibly printed. Never guess or infer.
- Include the lab's own reference range, not standard ranges.
- If date is not visible, use null.
"""

AGENT_SYSTEM_PROMPT = """You are MediAgent, a health record assistant.
You help patients understand their own lab results by retrieving and summarizing their data.

STRICT RULES — violating any of these is a critical failure:
- NEVER generate a diagnosis
- NEVER recommend treatment or medication
- NEVER say the patient "has" or "suffers from" any condition
- ALWAYS end your response with: "Please consult your doctor for medical advice."
- ONLY use data from the patient's own records via your tools
- When referencing data, cite the specific report date and test name
- If you don't have enough data, say so honestly
"""
```

Keeping prompts in a separate file means you can tune them without touching any logic — just like externalizing strings in Android.

---

### Agent Tools — What the Strands Agent Can Do

Each tool is a Python function with a docstring that Nova reads to understand when to use it. The agent autonomously picks which tools to call based on the user's question.

| Tool | When Agent Uses It | Example Question |
|---|---|---|
| `get_all_reports` | Needs report list or latest report | "Show my reports" / "When was my last checkup?" |
| `get_observations` | Needs specific lab values | "What's my glucose?" / "Show abnormal values" |
| `get_report_detail` | Needs full details of one report | "What did my March report show?" |
| `compute_trend` | Needs to compare values over time | "Is my cholesterol improving?" |

**Multi-step reasoning examples:**

Question: *"How does my cholesterol compare to last year?"*
→ `get_observations(loincCode="2093-3", fromDate="2023-01-01", toDate="2023-12-31")`
→ `get_observations(loincCode="2093-3", fromDate="2024-01-01", toDate="2024-12-31")`
→ Compares both sets → composes answer

Question: *"Summarize my last report and flag anything concerning"*
→ `get_all_reports()` → finds most recent
→ `get_report_detail(reportId=latest)` → gets all observations
→ Filters `isAbnormal: true` → summarizes with context

---

### Dependencies (requirements.txt)

**`lambdas/extract_report/requirements.txt`:**
```
boto3>=1.34.0
pydantic>=2.0.0
```

**`lambdas/agent_chat/requirements.txt`:**
```
boto3>=1.34.0
strands-agents>=0.1.0
strands-agents-tools>=0.1.0
```

> `boto3` is pre-installed in Lambda but pinning ensures consistency. `pydantic` handles validation. `strands-agents` is the agent framework.

---

### Build Order

| Step | What | When |
|---|---|---|
| 1 | Write `shared/` (models, repositories, config, loinc_mapping) | Foundation — everything depends on this |
| 2 | Write `extract_report/` (handler + services) | Core AI pipeline |
| 3 | `sam build` → `sam deploy` → test POST /reports/upload with Postman | Validate extraction end-to-end |
| 4 | Test GET /reports and GET /observations | Confirm data stored correctly |
| 5 | Write `agent_chat/` (handler + agent service + tools + prompts) | Needs data in DynamoDB to test |
| 6 | `sam build` → `sam deploy` → test POST /chat | Validate agent reasoning |
| 7 | Plug API URL into Android Retrofit client | Connect frontend |

---

## SaMD Safety Rules — Non-Negotiable

| | Rule | How It's Enforced |
|---|---|---|
| ❌ | Never generate a diagnosis | Agent system prompt + output validation |
| ❌ | Never recommend treatment or medication | Agent system prompt + output validation |
| ❌ | Never say patient "has" a condition | Agent system prompt |
| ✅ | Always include medical disclaimer | Every response has `disclaimer` field |
| ✅ | Only explain, organize, trend, summarize | Agent tools are read-only |
| ✅ | Show extraction confidence | `extractionConfidence` in upload response |
| ✅ | Cite which data was used | `referencedReports` + `referencedObservations` in chat |

---

## Android ↔ Backend Contract

| Rule | Detail |
|---|---|
| Dates | `YYYY-MM-DD` |
| Timestamps | ISO 8601 `YYYY-MM-DDTHH:MM:SSZ` |
| Images | Max 1MB JPEG before base64 encoding |
| Chat history | Max last 10 messages in `conversationHistory` |
| Disclaimer | Always present — Android must display it |
| Content-Type | `application/json` |
| CORS | `*` (all origins, hackathon only) |

---

## Testing Checklist (Postman)

Run these in order after `sam deploy`:

| # | Endpoint | Test | Expected |
|---|---|---|---|
| 1 | `POST /reports/upload` | Upload sample lab image | 200, observations array, confidence > 0.8 |
| 2 | `GET /reports` | Fetch all reports | 200, reports array, total ≥ 1 |
| 3 | `GET /observations` | Fetch all observations | 200, matches what was uploaded |
| 4 | `GET /observations?loincCode=2339-0` | Filter glucose | 200, only glucose results |
| 5 | `POST /chat` | "What was my last glucose?" | 200, natural language + disclaimer |
| 6 | `POST /reports/upload` | Invalid base64 | 400 VALIDATION_ERROR |
| 7 | `POST /chat` | "Do I have diabetes?" | 200, agent declines diagnosis + disclaimer |

---

## LOINC Codes — Supported Tests

### Blood Sugar
| Test | LOINC | Unit | Low | High |
|---|---|---|---|---|
| Glucose (Fasting) | `2339-0` | mg/dL | 70 | 100 |
| HbA1c | `4548-4` | % | 4.0 | 5.6 |
| Insulin (Fasting) | `20448-7` | µIU/mL | 2.6 | 24.9 |

### Lipid Panel
| Test | LOINC | Unit | Low | High |
|---|---|---|---|---|
| Total Cholesterol | `2093-3` | mg/dL | 0 | 200 |
| LDL | `18262-6` | mg/dL | 0 | 100 |
| HDL | `2085-9` | mg/dL | 40 | 60 |
| Triglycerides | `2571-8` | mg/dL | 0 | 150 |

### CBC
| Test | LOINC | Unit | Low | High |
|---|---|---|---|---|
| Hemoglobin | `718-7` | g/dL | 12.0 | 17.5 |
| WBC | `6690-2` | 10³/µL | 4.5 | 11.0 |
| RBC | `789-8` | 10⁶/µL | 4.2 | 5.9 |
| Platelets | `777-3` | 10³/µL | 150 | 400 |
| Hematocrit | `4544-3` | % | 36 | 52 |
| MCV | `787-2` | fL | 80 | 100 |

### Kidney
| Test | LOINC | Unit | Low | High |
|---|---|---|---|---|
| Creatinine | `2160-0` | mg/dL | 0.6 | 1.2 |
| BUN | `3094-0` | mg/dL | 7 | 20 |
| eGFR | `33914-3` | mL/min/1.73m² | 60 | 120 |
| Uric Acid | `3084-1` | mg/dL | 2.4 | 7.0 |

### Liver
| Test | LOINC | Unit | Low | High |
|---|---|---|---|---|
| ALT (SGPT) | `1742-6` | U/L | 7 | 56 |
| AST (SGOT) | `1920-8` | U/L | 10 | 40 |
| Bilirubin Total | `1975-2` | mg/dL | 0.1 | 1.2 |
| Albumin | `1751-7` | g/dL | 3.4 | 5.4 |
| ALP | `6768-6` | U/L | 44 | 147 |

### Thyroid
| Test | LOINC | Unit | Low | High |
|---|---|---|---|---|
| TSH | `3016-3` | µIU/mL | 0.4 | 4.0 |
| T3 (Free) | `3051-0` | pg/mL | 2.3 | 4.2 |
| T4 (Free) | `3054-4` | ng/dL | 0.8 | 1.8 |

### Electrolytes
| Test | LOINC | Unit | Low | High |
|---|---|---|---|---|
| Sodium | `2951-2` | mEq/L | 136 | 145 |
| Potassium | `2823-3` | mEq/L | 3.5 | 5.1 |
| Calcium | `17861-6` | mg/dL | 8.5 | 10.5 |
| Magnesium | `2601-3` | mg/dL | 1.7 | 2.2 |

### Vitamins & Minerals
| Test | LOINC | Unit | Low | High |
|---|---|---|---|---|
| Vitamin D | `1989-3` | ng/mL | 30 | 100 |
| Vitamin B12 | `2132-9` | pg/mL | 200 | 900 |
| Iron | `2498-4` | µg/dL | 60 | 170 |
| Ferritin | `2276-4` | ng/mL | 12 | 300 |

---

## Post-Hackathon — What to Add for Production

| Priority | What | AWS Service | Why |
|---|---|---|---|
| **P0** | User auth | Cognito | Multi-user, PHI protection |
| **P0** | HIPAA BAA | AWS Artifact | Legal requirement for health data |
| **P0** | KMS encryption | KMS | Customer-managed keys for PHI |
| **P1** | VPC isolation | VPC + Endpoints | Network security for Lambdas |
| **P1** | Audit logging | CloudTrail + CloudWatch | Compliance trail |
| **P1** | API protection | WAF | Prevent abuse |
| **P2** | CI/CD | CodePipeline | Automated deploys |
| **P2** | Multi-region | DynamoDB Global Tables | DR + latency |
| **P2** | Nova 2 upgrade | Bedrock | Better accuracy |
| **P3** | Voice | Nova 2 Sonic | Third modality |
