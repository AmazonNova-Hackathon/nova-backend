# MediAgent — Full Product Requirements Document

> **Planned rebrand:** Chetana (चेतना) — "Awareness" in Sanskrit. Tagline: *"Awaken to your health."*
> Rename will be applied across codebase when ready.

**What:** AI-powered health record companion that extracts lab data from photos, stores it as FHIR R4, delivers proactive health insights, and lets users converse with their records — across family members and in Indian vernacular languages.

**Demo Patient ID:** `patient-demo-001` (hardcoded, no auth for hackathon)
**Region:** `us-east-1`
**Hackathon:** Amazon Nova — Agentic AI category

---

## Nova AI Models — How We Use Each One

This is NOT a "talk to PDF" app. We use **four** Nova models, each for a distinct job:

| Model | Model ID | Role in App | Why This Model |
|---|---|---|---|
| **Nova 2 Lite** | `amazon.nova-2-lite-v1:0` | Primary brain — extraction, reasoning, insights, extended thinking | 1M context, multimodal, extended thinking with 3 intensity levels |
| **Nova Multimodal Embeddings** | `amazon.nova-2-multimodal-embeddings-v1:0` | RAG — embeds report images + text into unified vector space | First model to embed text, image, doc, video, audio into one space |
| **Nova 2 Sonic** | `amazon.nova-2-sonic-v1:0` | Voice interaction in Hindi + English | Real-time speech-to-speech, <700ms latency, Hindi polyglot voice |
| **Nova Micro** | `amazon.nova-micro-v1:0` | Fast translations, insight summaries, lightweight lookups | Lowest latency, lowest cost, 200+ languages |

### Nova 2 Lite — "Eyes + Brain"

Used in two fundamentally different ways:

**As Eyes (multimodal vision):** Receives a photo of a lab report and extracts structured data.
```python
bedrock = boto3.client('bedrock-runtime', region_name='us-east-1')
response = bedrock.invoke_model(
    modelId='amazon.nova-2-lite-v1:0',
    body=json.dumps({
        "messages": [{
            "role": "user",
            "content": [
                {"image": {"format": "jpeg", "source": {"bytes": image_base64}}},
                {"text": "Extract all lab test results from this report. Return JSON..."}
            ]
        }]
    })
)
```

**As Brain (agentic reasoning with extended thinking):** A Bedrock Agent powered by Nova 2 Lite reasons over health records. The agent is fully managed — AWS handles the orchestration loop, session memory, and tool selection. You define Action Groups (Lambda functions) that the agent can call:
```python
import boto3

bedrock_agent_runtime = boto3.client('bedrock-agent-runtime', region_name='us-east-1')

response = bedrock_agent_runtime.invoke_agent(
    agentId='your-agent-id',
    agentAliasId='your-alias-id',
    sessionId='user-session-id',      # Bedrock manages conversation memory
    inputText="How is my family's health overall?"
)

# Agent autonomously:
# 1. Reasons about what data it needs
# 2. Calls Action Groups (your Lambda tools) to query DynamoDB
# 3. Uses Knowledge Base (RAG) to ground responses in guidelines
# 4. Applies Guardrails to enforce SaMD safety rules
# 5. Returns a composed response with citations
```

**Extended thinking intensity levels:**
- **Low:** Quick lookups — "What was my last glucose?"
- **Medium:** Trend analysis — "Is my cholesterol improving?"
- **High:** Cross-member family insights, proactive health alerts, prescription-based follow-up inference

### Nova Multimodal Embeddings — "Memory" (RAG)

Every uploaded report image + its extracted text gets embedded into a unified vector space via Bedrock Knowledge Bases. This enables:
- **Semantic search:** "Show me reports where iron was discussed" finds reports even if "iron" wasn't in the extracted data but was visible in the image
- **Citation with source:** When insights reference data, the app can point to the exact region of the original report image
- **Cross-modal retrieval:** Query with text, get back relevant report images

```python
# Embedding a report image into the vector space
request_body = {
    "taskType": "SINGLE_EMBEDDING",
    "singleEmbeddingParams": {
        "embeddingPurpose": "GENERIC_INDEX",
        "embeddingDimension": 1024,
        "image": {"source": {"bytes": image_base64}},
    },
}
response = bedrock.invoke_model(
    modelId='amazon.nova-2-multimodal-embeddings-v1:0',
    body=json.dumps(request_body)
)
```

### Nova 2 Sonic — "Voice"

Real-time speech-to-speech in Hindi and English. User speaks in Hindi, system responds in Hindi — no text-to-speech pipeline, it's native audio.

**Supported languages for voice:** English (US, UK, Indian, Australian), Hindi, Spanish, German, French, Italian, Portuguese
**Indian language limitation:** Tamil, Marathi, Bengali, Telugu are NOT supported for voice yet — text-only for these.

### Nova Micro — "Quick Translator"

Ultra-fast, ultra-cheap text-only model. Used for:
- Translating insights into Marathi, Tamil, Bengali, Telugu (languages Sonic doesn't support for voice)
- Generating insight card summaries in vernacular languages
- Quick LOINC code lookups and formatting

### How Auth Works for All Nova Calls

Lambda functions get IAM execution roles from SAM template — no API keys in code, ever. When Python calls `boto3.client('bedrock-runtime')`, it inherits permissions from the role. This is the same for all four models.

---

## Architecture Overview

```
                         ┌──────────────────────────────────────┐
                         │         Clients                      │
                         │  ┌──────────┐  ┌──────────────────┐  │
                         │  │ Android  │  │ Web (React/Next) │  │
                         │  │ Kotlin   │  │ TypeScript        │  │
                         │  └────┬─────┘  └───────┬──────────┘  │
                         └───────┼────────────────┼─────────────┘
                                 │                │
                         ┌───────▼────────────────▼─────────────┐
                         │          API Gateway (REST)           │
                         │          CORS: * (hackathon)          │
                         └──┬────┬────┬────┬────┬────┬──────────┘
                            │    │    │    │    │    │
           ┌────────────────▼┐  ┌▼────▼┐ ┌▼────▼┐  ┌▼───────────┐
           │   Lambda 1      │  │ L2   │ │ L3   │  │ Lambda 4   │
           │   Extract +     │  │ Chat │ │ Ins. │  │ Voice      │
           │   FHIR Store    │  │ Agent│ │ Engn │  │ Gateway    │
           └────────┬────────┘  └──┬───┘ └──┬───┘  └─────┬──────┘
                    │              │         │             │
    ┌───────────────▼──────────────▼─────────▼─────────────▼──────┐
    │                    Amazon Bedrock                            │
    │  ┌────────────────┐  ┌──────────────────────────────────┐   │
    │  │  Nova 2 Lite   │  │  Nova Multimodal Embeddings      │   │
    │  │  (Reasoning +  │  │  (RAG Vector Space)              │   │
    │  │   Extraction)  │  │                                  │   │
    │  └────────────────┘  └──────────────────────────────────┘   │
    │  ┌────────────────┐  ┌──────────────────────────────────┐   │
    │  │  Nova 2 Sonic  │  │  Nova Micro                      │   │
    │  │  (Hindi Voice) │  │  (Translations + Notifications)  │   │
    │  └────────────────┘  └──────────────────────────────────┘   │
    └─────────────────────────────────────────────────────────────┘
                    │              │         │
    ┌───────────────▼┐  ┌─────────▼──┐  ┌───▼──────────────────┐
    │   DynamoDB     │  │    S3      │  │ Bedrock Knowledge    │
    │   (FHIR data   │  │  (Report   │  │ Base + OpenSearch    │
    │    + Family)   │  │   images)  │  │ Serverless / S3      │
    │               │  │            │  │ Vectors              │
    └───────────────┘  └────────────┘  └───────────────────────┘
                                            │
                              ┌──────────────▼──────────────┐
                              │  EventBridge (Scheduled)    │
                              │  → Lambda 3 (Insights)      │
                              └─────────────────────────────┘
```

### AWS Services — Complete List

| Service | Resource | Purpose |
|---|---|---|
| API Gateway | `ChetanaApi` (prod) | REST entry point, CORS enabled |
| Lambda 1 | `chetana-extract-report` | Presigned URL generation + S3 event-triggered Nova extraction + FHIR storage |
| Lambda 2 | `chetana-agent-chat` | Bedrock Agent invocation — chat, trends, family insights |
| Lambda 3 | `chetana-insights-engine` | Proactive insights, follow-up scheduling, notifications |
| Lambda 4 | `chetana-voice-gateway` | Nova 2 Sonic bidirectional streaming for voice |
| DynamoDB | `chetana-fhir` | Single-table: families, members, reports, observations |
| S3 | `chetana-reports-{acctId}` | Original report images, AES-256, no public access |
| Bedrock | Nova 2 Lite, Embeddings, Sonic, Micro | All four AI models |
| Bedrock Agent | `chetana-health-agent` | Managed agent — orchestrates reasoning, tools, RAG, guardrails |
| Bedrock Guardrails | `chetana-samd-guardrail` | Platform-level SaMD safety enforcement |
| Bedrock Knowledge Base | `chetana-health-kb` | RAG over report images + clinical guidelines |
| OpenSearch Serverless | `chetana-vectors` | Vector store for embeddings |
| EventBridge | `chetana-insights-schedule` | Cron trigger for proactive insights (daily) |
| SNS / Pinpoint | `chetana-notifications` | Push notifications (post-hackathon) |

### Notification Strategy — Pull Now, Push Later

**Hackathon (pull-based):** Insights and follow-ups are generated by Lambda 3 (on EventBridge schedule) and written to DynamoDB. The app polls for new items on launch and periodically. No SNS/Pinpoint setup needed.

```
EventBridge (daily cron)
  → Lambda 3 generates insights + follow-ups
  → Writes to DynamoDB (INSIGHT# and FOLLOWUP# items)
  → App checks on launch: GET /insights?unread=true
  → Shows badge count on Insights tab + inline cards
```

**How pull notification works in the app:**
1. On app open, call `GET /insights?familyId=...&unread=true`
2. If `unreadCount > 0`, show a red badge on the Insights tab
3. When user views an insight, call `PATCH /insights/{id}` to mark as read
4. Periodically poll (every 15 min while app is in foreground) — or on pull-to-refresh
5. For web: same polling, or use a simple `setInterval` check

**Post-hackathon (push-based):** Add SNS topic + Firebase Cloud Messaging (Android) / Web Push API. Lambda 3 publishes to SNS after generating insights, SNS fans out to device endpoints. This removes polling and gives real-time alerts.

### Bedrock Agent Architecture — How It Wires Together

Instead of writing agent orchestration code ourselves (Strands, LangChain, etc.), we use **Amazon Bedrock Agents** — a fully managed service where AWS owns the reasoning loop. You configure the agent, attach tools and knowledge, and AWS handles everything else.

**Components of our Bedrock Agent:**

```
┌─────────────────────────────────────────────────────────┐
│                  Bedrock Agent                           │
│               (chetana-health-agent)                     │
│                                                         │
│  ┌─────────────────┐     ┌────────────────────────────┐ │
│  │  Foundation Model│     │  Agent Instructions        │ │
│  │  Nova 2 Lite    │     │  (System prompt + SaMD     │ │
│  │                 │     │   safety rules)            │ │
│  └─────────────────┘     └────────────────────────────┘ │
│                                                         │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  Action Groups (your Lambda functions as tools)     │ │
│  │  ┌──────────────┐ ┌──────────────┐ ┌─────────────┐ │ │
│  │  │ Health Data  │ │ Family Data  │ │ Trend       │ │ │
│  │  │ Actions      │ │ Actions      │ │ Compute     │ │ │
│  │  │ (DynamoDB    │ │ (DynamoDB    │ │ Actions     │ │ │
│  │  │  queries)    │ │  queries)    │ │             │ │ │
│  │  └──────────────┘ └──────────────┘ └─────────────┘ │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                         │
│  ┌──────────────────┐     ┌───────────────────────────┐ │
│  │  Knowledge Base  │     │  Guardrails               │ │
│  │  (RAG — report   │     │  (SaMD safety enforced    │ │
│  │   images +       │     │   at platform level)      │ │
│  │   guidelines)    │     │  • No diagnosis            │ │
│  └──────────────────┘     │  • No treatment advice     │ │
│                           │  • Mandatory disclaimer    │ │
│                           └───────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

**Why Bedrock Agents over Strands:**

| Capability | Strands (code-based) | Bedrock Agents (managed) |
|---|---|---|
| Orchestration loop | You write it | AWS manages it |
| Tool selection | LLM decides via your code | LLM decides via managed runtime |
| Knowledge Base (RAG) | Manual integration | Native plug-in — attach directly |
| Guardrails | Prompt-only enforcement | Platform-level enforcement (can't be bypassed) |
| Session memory | You manage `conversationHistory` | Built-in session management per `sessionId` |
| Action Groups | `@tool` Python functions | Lambda functions invoked by the agent |
| Observability | Custom logging | CloudWatch traces built-in |
| Dependencies | `strands-agents` pip package | No SDK dependency — just `boto3` |

**Action Groups** are Lambda functions that the Bedrock Agent can call as tools. Each Action Group has an OpenAPI schema defining its inputs/outputs. The agent reads these schemas and decides when to invoke them:

| Action Group | Lambda | Operations |
|---|---|---|
| `HealthDataActions` | `chetana-action-health-data` | `getReports`, `getObservations`, `getReportDetail` |
| `FamilyActions` | `chetana-action-family` | `getFamilyDashboard`, `getMemberDetail`, `getAbnormals` |
| `TrendActions` | `chetana-action-trends` | `computeTrend`, `compareMembers`, `detectPatterns` |

**Knowledge Base** (`chetana-health-kb`) connects directly to the agent:
- Contains: uploaded report images (embedded via Nova Multimodal Embeddings) + clinical guideline documents
- Vector store: OpenSearch Serverless or S3 Vectors
- The agent automatically queries the KB when it needs context — no code required

**Guardrails** (`chetana-samd-guardrail`) enforce SaMD safety at the platform level:
- Denied topics: diagnosis, treatment recommendations, medication advice
- Content filters: block any output that claims the patient "has" or "suffers from" a condition
- Mandatory disclaimer: appended to every response
- These run AFTER the LLM generates output but BEFORE it reaches the user — even if the prompt fails, the guardrail catches it

**How Lambda 2 works now (much simpler):**
```python
# agent_chat/app.py — thin wrapper, no agent logic
import boto3

bedrock_agent_runtime = boto3.client('bedrock-agent-runtime', region_name='us-east-1')

def lambda_handler(event, context):
    body = json.loads(event['body'])

    response = bedrock_agent_runtime.invoke_agent(
        agentId=os.environ['AGENT_ID'],
        agentAliasId=os.environ['AGENT_ALIAS_ID'],
        sessionId=body.get('sessionId', str(uuid.uuid4())),
        inputText=body['message']
    )

    # Stream response chunks
    completion = ""
    for chunk in response['completion']:
        if 'chunk' in chunk:
            completion += chunk['chunk']['bytes'].decode()

    return {
        "statusCode": 200,
        "body": json.dumps({
            "reply": completion,
            "sessionId": response['sessionId'],
            "disclaimer": get_disclaimer(body.get('preferredLanguage', 'en'))
        })
    }
```

No Strands SDK, no agent orchestration code, no tool wiring. Your Lambda just calls `invoke_agent` and streams the response. All the reasoning, tool selection, RAG retrieval, and guardrail enforcement happens inside Bedrock.

---

## Features — Detailed Design

### Feature 1: Family Management

**Problem:** Indian families share health responsibilities. A son manages parents' reports, a wife tracks the whole family. Current health apps are single-user.

**How it works:**
1. User creates a family and adds members (self, spouse, parents, children)
2. When uploading a report, Nova 2 Lite reads the patient name from the report image and auto-matches to a family member
3. If no match, prompts user: "This report has name 'Sunita Sharma' — is this a new family member or existing?"
4. Family dashboard shows all members' health at a glance
5. Agent can reason across family: "Both you and your mother have elevated cholesterol — this may be hereditary"

**DynamoDB key changes:**
```
PK: FAMILY#{familyId}
SK: MEMBER#{memberId}                    → member profile
SK: REPORT#{memberId}#date#uuid          → report tied to member
SK: OBS#{memberId}#loincCode#date#uuid   → observation tied to member
```

---

### Feature 2: Proactive Insights Engine

**Problem:** Current design only flags H/L in a table. Users don't know what's *actually concerning* or what needs *urgent action*.

**How it works — two modes:**

**Mode A: On-extraction insights (triggered when S3 processing completes)**
When a report finishes extraction (S3 event → Lambda 1), Nova 2 Lite with extended thinking (high) analyses all observations in context of the patient's history:
- Not just "glucose is high" but "your glucose has risen across 3 consecutive reports and your HbA1c is now in pre-diabetic range — please schedule an appointment with your doctor"
- Cross-references observations: elevated creatinine + elevated BUN = kidney function concern
- Generates a severity score (informational / attention / urgent)
- Cites the specific report and observation

**Mode B: Scheduled insights (proactive — pull on app open)**
EventBridge triggers Lambda 3 daily. For each user with recent data:
- Runs Nova 2 Lite with extended thinking over their full observation history
- Detects patterns that develop over time
- Generates insight cards with citations
- Writes to DynamoDB as `INSIGHT#` and `FOLLOWUP#` items
- App fetches unread insights on launch → shows badge + cards

**RAG integration:**
The Bedrock Knowledge Base is pre-loaded with clinical guideline documents. When generating insights, the agent uses RAG to ground statements in guidelines:
> "Based on ADA guidelines, your HbA1c of 6.2% falls in the pre-diabetic range (5.7–6.4%). Standard recommendation is retesting every 3 months."

**Insight card structure:**
```json
{
  "insightId": "insight-uuid",
  "memberId": "member-uuid",
  "severity": "urgent | attention | informational",
  "title": "Glucose Trend Requires Attention",
  "summary": "Your fasting glucose has risen steadily over 3 months...",
  "details": "Detailed explanation with cited values...",
  "citedObservations": ["obs-uuid-1", "obs-uuid-2"],
  "citedReports": ["report-uuid-1"],
  "suggestedAction": "Schedule appointment with your doctor",
  "generatedAt": "2024-03-15T10:00:00Z",
  "read": false,
  "language": "en",
  "disclaimer": "This is not medical advice. Please consult your doctor."
}
```

---

### Feature 3: Follow-up Scheduling

**Problem:** Diabetics need quarterly tests, thyroid patients need periodic T3/T4/TSH, pregnant women have a defined test schedule. Users forget.

**How it works:**
1. Nova 2 Lite analyses observation history and uploaded prescriptions
2. Detects condition patterns (not diagnosis — pattern detection):
   - Elevated glucose + HbA1c pattern → suggests quarterly glucose + HbA1c checks
   - Thyroid medication in prescription → suggests periodic TSH, T3, T4
   - Iron deficiency pattern → suggests ferritin recheck in 3 months
3. Generates follow-up reminders with suggested dates
4. User can accept/modify/dismiss reminders
5. Stored in DynamoDB — app shows on launch and in Follow-ups screen

**RAG grounding:** Follow-up suggestions are grounded in clinical guideline documents loaded into the Knowledge Base. The agent cites the guideline, not its own knowledge.

**Follow-up structure:**
```json
{
  "followUpId": "fu-uuid",
  "memberId": "member-uuid",
  "testName": "HbA1c",
  "loincCode": "4548-4",
  "reason": "Based on your elevated glucose trend, standard guidelines suggest retesting every 3 months",
  "suggestedDate": "2024-06-01",
  "basedOnObservations": ["obs-uuid-1", "obs-uuid-2"],
  "status": "pending | accepted | dismissed",
  "createdAt": "2024-03-15T10:00:00Z"
}
```

---

### Feature 4: Indian Vernacular Languages

**Problem:** 90%+ of India doesn't think in English. Health literacy is already low — forcing English makes it worse.

**Language support matrix:**

| Language | Text (Chat + Insights) | Voice (Sonic) | How |
|---|---|---|---|
| English | ✅ | ✅ | Native Nova 2 Lite + Sonic |
| Hindi | ✅ | ✅ | Native Nova 2 Lite + Sonic (polyglot voice) |
| Marathi | ✅ | ❌ text only | Nova 2 Lite (200+ lang) + Nova Micro translation |
| Tamil | ✅ | ❌ text only | Nova 2 Lite + Nova Micro |
| Bengali | ✅ | ❌ text only | Nova 2 Lite + Nova Micro |
| Telugu | ✅ | ❌ text only | Nova 2 Lite + Nova Micro |
| Kannada | ✅ | ❌ text only | Nova 2 Lite + Nova Micro |
| Gujarati | ✅ | ❌ text only | Nova 2 Lite + Nova Micro |

**How it works:**
1. User sets preferred language in profile
2. All insights, chat responses, follow-up reminders are generated in that language
3. Agent system prompt includes: "Respond in {user.preferredLanguage}. Use simple, non-medical terminology."
4. Original report data stays in English (LOINC codes are universal) — only the presentation layer translates
5. For Hindi users: voice option available via Nova 2 Sonic
6. For other Indian languages: text chat + insights only

---

## API Endpoints — Complete Specification

**Base URL:** `https://<id>.execute-api.us-east-1.amazonaws.com/prod`
**Auth:** None for hackathon | **Content-Type:** `application/json`

### Core APIs (Lambda 1: Extract + FHIR)

#### Upload Flow — Presigned URL + S3 Event Trigger (Async)

Instead of sending base64 in a JSON body (size-limited, slow, wasteful), we use the industry-standard presigned URL pattern. The client uploads directly to S3, an S3 event auto-triggers extraction, and the client polls for results.

```
Client                     Lambda 1              S3                 Lambda 1 (S3 trigger)
  │                           │                   │                    │
  ├── POST /reports/upload-url ─►                 │                    │
  │   ◄── {reportId, uploadUrl, s3Key}            │                    │
  │                           │                   │                    │
  ├── PUT (raw bytes) ────────────────────────────►                    │
  │   (direct to S3, any size)│                   │                    │
  │                           │                   ├── S3 ObjectCreated ─►
  │   User can navigate away  │                   │                    ├─ Nova extraction
  │                           │                   │                    ├─ FHIR storage
  ├── GET /reports/{id}/status ─►                 │                    ├─ Insights generation
  │   ◄── {status: "completed", observations, insights}               │
```

**Step 1: Get presigned upload URL**

#### POST /reports/upload-url

**Request:**
```json
{
  "familyId": "family-demo-001",
  "memberId": "member-uuid (optional — auto-detect from report)",
  "fileName": "lab_report_march.jpg",
  "contentType": "image/jpeg",
  "reportType": "lab_report | prescription | doctor_note"
}
```

Supported content types: `image/jpeg`, `image/png`, `application/pdf`

**Response 200:**
```json
{
  "reportId": "report-uuid",
  "uploadUrl": "https://chetana-reports-{acctId}.s3.amazonaws.com/...?X-Amz-Signature=...",
  "s3Key": "family-demo-001/member-uuid/report-uuid.jpg",
  "expiresIn": 300
}
```

The presigned URL is valid for 5 minutes. Backend also creates a skeleton `REPORT#` item in DynamoDB with `status: "uploading"`.

**Step 2: Upload file directly to S3**

Client uses the presigned URL to PUT the raw file bytes directly to S3. No API Gateway involved, no Lambda invoked, no size limit (S3 handles up to 5GB).

```kotlin
// Android (OkHttp)
val request = Request.Builder()
    .url(uploadResponse.uploadUrl)
    .put(imageBytes.toRequestBody("image/jpeg".toMediaType()))
    .build()
okHttpClient.newCall(request).execute()
```

```typescript
// Web (fetch)
await fetch(uploadUrl, {
  method: 'PUT',
  body: file,
  headers: { 'Content-Type': 'image/jpeg' }
})
```

**Step 3: S3 event auto-triggers processing**

When the file lands in S3, an S3 `ObjectCreated` event automatically invokes Lambda 1. No client action needed. Lambda 1:
1. Updates report status: `"processing"`
2. Reads the file from S3
3. Sends to Nova 2 Lite for extraction (multimodal for images, document mode for PDFs)
4. Maps extracted tests to LOINC codes
5. Auto-detects patient name → matches to family member
6. Writes observations to DynamoDB
7. Generates immediate insights
8. Updates report status: `"completed"` (or `"failed"` with error)

**Step 4: Poll for results**

#### GET /reports/{reportId}/status

**Response — processing:**
```json
{
  "reportId": "report-uuid",
  "status": "processing",
  "progress": "Extracting page 3 of 8...",
  "startedAt": "2024-03-01T10:30:00Z"
}
```

**Response — completed:**
```json
{
  "reportId": "report-uuid",
  "status": "completed",
  "memberId": "member-uuid",
  "memberName": "Rahul Sharma",
  "autoDetected": true,
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
  "immediateInsights": [
    {
      "severity": "attention",
      "title": "Glucose Trending Upward",
      "summary": "Your glucose has risen across 3 consecutive reports (95 → 102 → 110)."
    }
  ],
  "disclaimer": "This is an informational summary only. Please consult your doctor."
}
```

**Response — failed:**
```json
{
  "reportId": "report-uuid",
  "status": "failed",
  "error": "Could not extract data from this image. Please try a clearer photo.",
  "failedAt": "2024-03-01T10:31:00Z"
}
```

**Report status lifecycle:**
```
uploading → processing → completed
                ↘ failed
```

**Client polling strategy:**
- Stay on upload screen: poll every 3 seconds, auto-show results when completed
- Navigate away: poll on return, or check on next app open
- Web: `setInterval` or React Query with `refetchInterval`
- Android: ViewModel coroutine with `delay(3000)` loop

**Batch uploads:** User can upload multiple reports in a row. Each gets its own `reportId` and processes independently. Family dashboard shows "3 reports processing..." indicator.

---

#### GET /reports?familyId=...&memberId=...&status=...
List reports. If only `familyId`, returns all members' reports. Filter by `status` to show only completed reports or find processing ones.

#### GET /observations?familyId=...&memberId=...&loincCode=...&fromDate=...&toDate=...
Query lab values with filters.

---

### Family APIs (Lambda 1)

#### POST /family
```json
{ "familyName": "Sharma Family", "createdBy": "member-uuid" }
```

#### POST /family/members
```json
{
  "familyId": "family-demo-001",
  "name": "Sunita Sharma",
  "relationship": "spouse | parent | child | self",
  "dateOfBirth": "1985-06-15",
  "gender": "female | male | other",
  "preferredLanguage": "hi"
}
```

#### GET /family/dashboard?familyId=...
Returns aggregated family health view with per-member stats + cross-family insights.

#### PATCH /family/members/{memberId}
Update member profile (name, language, etc).

---

### Chat APIs (Lambda 2: Agent)

#### POST /chat
```json
{
  "familyId": "family-demo-001",
  "memberId": "member-uuid",
  "message": "मेरा ग्लूकोज़ कैसा चल रहा है?",
  "preferredLanguage": "hi",
  "conversationHistory": [],
  "thinkingIntensity": "medium"
}
```

**Response includes:** `reply` (in requested language), `referencedReports`, `referencedObservations`, `language`, `disclaimer`

---

### Insights APIs (Lambda 3)

#### GET /insights?familyId=...&memberId=...&severity=...&unread=true
Get insight cards. Use `unread=true` to get only unread insights (for badge count on app launch).

#### PATCH /insights/{insightId}
Mark an insight as read: `{ "read": true }`

#### GET /followups?familyId=...&memberId=...&status=pending
Get pending follow-up reminders.

#### PATCH /followups/{followUpId}
Accept or dismiss: `{ "status": "accepted | dismissed" }`

#### POST /insights/generate
Trigger on-demand insight generation.

---

### Voice APIs (Lambda 4)

#### POST /voice/session
Initiate Nova 2 Sonic voice session. Returns WebSocket URL for bidirectional audio streaming.

```json
{
  "familyId": "family-demo-001",
  "memberId": "member-uuid",
  "language": "hi",
  "voiceId": "TIFFANY"
}
```

> Nova 2 Sonic uses bidirectional streaming, not REST. Lambda 4 brokers the session.

---

### Error Format (All Endpoints)

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

## Data Model — DynamoDB Single Table (Expanded)

**Table:** `chetana-fhir` | **Billing:** PAY_PER_REQUEST | **Encryption:** SSE enabled

### Key Schema

| Entity | PK | SK | Data |
|---|---|---|---|
| Family | `FAMILY#{familyId}` | `META` | familyName, createdBy, createdAt |
| Member | `FAMILY#{familyId}` | `MEMBER#{memberId}` | name, relationship, dob, gender, preferredLanguage |
| Report | `FAMILY#{familyId}` | `REPORT#{memberId}#date#uuid` | reportId, labName, date, totalObs, abnormalCount, s3Key, status (uploading/processing/completed/failed) |
| Observation | `FAMILY#{familyId}` | `OBS#{memberId}#loincCode#date#uuid` | all observation fields |
| Insight | `FAMILY#{familyId}` | `INSIGHT#{memberId}#date#uuid` | severity, title, summary, citedObs[], citedReports[], read (bool) |
| Follow-up | `FAMILY#{familyId}` | `FOLLOWUP#{memberId}#date#uuid` | testName, loincCode, reason, suggestedDate, status |

### Access Patterns

| Use Case | PK | SK Condition |
|---|---|---|
| All family members | `FAMILY#{familyId}` | `begins_with MEMBER#` |
| All reports (family) | `FAMILY#{familyId}` | `begins_with REPORT#` |
| Reports for one member | `FAMILY#{familyId}` | `begins_with REPORT#{memberId}#` |
| Observations by LOINC | `FAMILY#{familyId}` | `begins_with OBS#{memberId}#2339-0#` |
| Pending follow-ups | `FAMILY#{familyId}` | `begins_with FOLLOWUP#` filter status=pending |
| Insights by severity | `FAMILY#{familyId}` | `begins_with INSIGHT#` filter severity |

### Interpretation Codes

`N` = Normal 🟢 | `H` = High 🔴 | `L` = Low 🔵 | `HH` = Critical High 🚨 | `LL` = Critical Low 🚨

---

## Lambda Architecture — Layered Design

### Design Principles

1. **Handler → Service → Repository** — each layer has one job
2. **Pydantic models everywhere** — typed requests, responses, domain objects
3. **Repository pattern** — DynamoDB access isolated, swappable
4. **Config via environment variables** — from SAM template, never hardcoded
5. **Prompts externalized** — `prompts.py` separate from logic
6. **No silent failures** — every exception caught, logged, returned as structured error

### Project Structure

```
backend/
├── template.yaml
├── lambdas/
│   ├── shared/
│   │   ├── models/
│   │   │   ├── fhir.py              # Observation, DiagnosticReport, Member, Family
│   │   │   ├── requests.py          # UploadRequest, ChatRequest, etc.
│   │   │   ├── responses.py         # UploadResponse, InsightCard, ErrorResponse
│   │   │   └── enums.py             # Severity, Interpretation, Language, Relationship
│   │   ├── repositories/
│   │   │   ├── dynamo_repository.py  # All DynamoDB operations
│   │   │   ├── s3_repository.py      # Image upload/download
│   │   │   └── kb_repository.py      # Bedrock Knowledge Base queries (RAG)
│   │   ├── config.py                 # Environment variables
│   │   ├── loinc_mapping.py          # LOINC code lookup
│   │   └── prompts.py                # All Nova prompts
│   │
│   ├── extract_report/
│   │   ├── app.py                    # Lambda handler (API routes + S3 event trigger)
│   │   ├── presigned_url_service.py  # Generate S3 presigned URLs
│   │   ├── extraction_service.py     # Nova 2 Lite multimodal extraction (images + PDFs)
│   │   ├── member_detection.py       # Auto-detect patient name from report
│   │   ├── report_service.py         # FHIR storage + status management + immediate insights
│   │   ├── family_service.py         # Family CRUD
│   │   └── requirements.txt
│   │
│   ├── agent_chat/
│   │   ├── app.py                    # Lambda handler — invokes Bedrock Agent
│   │   ├── agent_service.py          # Bedrock Agent runtime invocation
│   │   ├── action_groups/            # Action Group Lambda handlers
│   │   │   ├── health_data_actions.py  # get_reports, get_observations, compute_trend
│   │   │   └── family_actions.py       # get_family_dashboard, get_member_detail
│   │   ├── translation_service.py    # Nova Micro for language translation
│   │   └── requirements.txt
│   │
│   ├── insights_engine/
│   │   ├── app.py                    # Lambda handler (EventBridge + API)
│   │   ├── insights_service.py       # Pattern detection + insight generation
│   │   ├── followup_service.py       # Follow-up scheduling
│   │   ├── notification_service.py   # Write unread markers to DynamoDB (push via SNS post-hackathon)
│   │   └── requirements.txt
│   │
│   └── voice_gateway/
│       ├── app.py                    # Lambda handler
│       ├── sonic_service.py          # Nova 2 Sonic session management
│       └── requirements.txt
```

### Layer Comparison (Android MVVM → Lambda)

| Android (what you know) | Lambda (same idea) |
|---|---|
| Composable Screen | `app.py` — routing + error boundary |
| ViewModel | `*_service.py` — business logic |
| Repository | `repositories/` — DynamoDB, S3, Bedrock, Knowledge Base |
| Data classes | `models/` — Pydantic typed models |
| strings.xml | `prompts.py` — AI prompts externalized |
| BuildConfig | `config.py` — environment variables |

---

## Frontend — Tech Stack & Screens

### Tech Stack

| Platform | Stack | Purpose |
|---|---|---|
| **Android** | Kotlin + Jetpack Compose + Retrofit + Hilt + CameraX | Primary mobile (camera capture) |
| **Web** | Next.js 14 + TypeScript + Tailwind CSS + shadcn/ui | Dashboard, family mgmt, faster iteration |
| **Shared** | Same REST APIs, same response contracts | Both clients talk to same backend |

### Why Web Too?

1. **Faster iteration** — test APIs without building APK
2. **Family management** — easier on larger screen
3. **Demo flexibility** — show web dashboard to judges alongside mobile
4. **Accessibility** — older family members prefer web

### Web Project Structure

```
web/
├── app/
│   ├── layout.tsx                    # Root layout + sidebar nav
│   ├── page.tsx                      # Family dashboard (home)
│   ├── family/
│   │   ├── page.tsx                  # Family management
│   │   └── [memberId]/
│   │       ├── page.tsx              # Member detail
│   │       ├── reports/page.tsx      # Report history
│   │       └── insights/page.tsx     # Member insights
│   ├── upload/page.tsx               # Report upload (drag-drop + camera)
│   ├── chat/page.tsx                 # Chat interface
│   ├── insights/page.tsx             # Proactive insights dashboard
│   └── followups/page.tsx            # Follow-up reminders
├── components/
│   ├── ui/                           # shadcn/ui components
│   ├── ReportCard.tsx
│   ├── InsightCard.tsx
│   ├── ObservationChart.tsx          # Recharts trend lines
│   ├── FamilyDashboard.tsx
│   ├── ChatInterface.tsx
│   └── LanguageSelector.tsx
├── lib/
│   ├── api.ts                        # Fetch wrapper for all endpoints
│   ├── types.ts                      # TypeScript types matching API contracts
│   └── constants.ts                  # API URL, languages, etc.
├── next.config.js
├── tailwind.config.js
└── package.json
```

### Screen Designs

#### Screen 1: Family Dashboard (Home)
At-a-glance health status for the whole family.
- Top: Family name + language selector
- Cards grid: one per member (name, relationship, last report date, abnormal count, urgent badge)
- Bottom: Family-wide cross-member insights
- FAB: "Upload Report"
- **API:** `GET /family/dashboard`

#### Screen 2: Member Detail
Deep dive into one member's health.
- Header: Name, age, relationship
- Tab 1 — **Reports:** List by date, abnormal count badges
- Tab 2 — **Trends:** Recharts line graphs (glucose, cholesterol, etc. over time)
- Tab 3 — **Insights:** Active insight cards with severity + citations
- Tab 4 — **Follow-ups:** Pending reminders, accept/dismiss

#### Screen 3: Report Upload
Capture or upload a lab report (JPEG, PNG, or multi-page PDF).
- **Android:** Full-screen CameraX preview, capture + gallery picker + file picker for PDFs
- **Web:** Drag-drop zone + file picker + webcam option
- After selecting file:
  1. Call `POST /reports/upload-url` → get presigned URL
  2. Show upload progress bar (PUT to S3 directly — fast even for large PDFs)
  3. Upload completes → show "Nova is reading your report..." with animated card
  4. Poll `GET /reports/{id}/status` every 3 seconds
  5. When `status: "completed"` → show extracted observations (colour-coded) + immediate insights
  6. Auto-detected member name shown with option to correct
- **User can navigate away** during processing — result appears when they return
- **Batch:** upload multiple files in a row, each processes independently
- **API:** `POST /reports/upload-url`, `PUT` to S3, `GET /reports/{id}/status`

#### Screen 4: Report Detail
View one report's extracted data with source citation.
- Report metadata (date, lab, confidence)
- Original image (zoomable)
- Observations table with colour status
- Insights from this report
- "Chat about this report" button

#### Screen 5: Chat
Natural language Q&A about health records.
- WhatsApp-style chat bubbles
- Member selector at top
- Language selector
- Voice button (Hindi/English via Nova 2 Sonic)
- Suggested questions: "How is my glucose?", "Summarize last report", "What should I test next?"
- Agent responses show cited observations inline (tappable)
- Disclaimer bar at bottom
- **API:** `POST /chat`

#### Screen 6: Insights Dashboard
All proactive insights across family, sorted by severity.
- **Badge on tab:** red dot with unread count (from `GET /insights?unread=true`)
- Filter tabs: Urgent | Attention | Informational | All
- Family toggle: all members or filter to one
- Cards: severity badge, title, summary, cited observations, suggested action, "Chat about this"
- Unread cards have a highlighted border — tapping marks as read (`PATCH /insights/{id}`)
- Pull-to-refresh triggers `POST /insights/generate`
- **API:** `GET /insights/family`, `PATCH /insights/{id}`

#### Screen 7: Follow-ups
Upcoming test reminders.
- Timeline view by suggested date
- Cards: test name, reason, date, citations
- Accept / Dismiss / Snooze actions
- **API:** `GET /followups/family`, `PATCH /followups/{id}`

#### Screen 8: Settings
Language, voice, family management.
- Language picker (en, hi, mr, ta, bn, te, kn, gu)
- Voice preference toggle
- Family management (add/remove members)

### Primary User Flow

```
Open App
  → Family Dashboard
  → Tap "Upload Report"
  → Camera captures / gallery picks lab report (JPEG or PDF)
  → Upload progress bar (direct to S3 via presigned URL)
  → "Nova is reading your report..." (user can navigate away)
  → Polls status... processing... completed!
  → Auto-detects "Sunita Sharma" → matches to family member
  → Shows observations (colour-coded) + immediate insights
  → Red banner: "Glucose trending up — schedule appointment"
  → User taps "Chat about this" → asks in Hindi
  → Agent responds in Hindi with cited data
  → Next day: opens app → red badge on Insights tab → "HbA1c recheck due in 2 weeks"
```

---

## SaMD Safety Rules — Non-Negotiable

Enforced at **two levels**: Bedrock Guardrails (platform, can't be bypassed) + agent instructions (prompt).

| | Rule | Enforced By |
|---|---|---|
| ❌ | Never generate a diagnosis | **Bedrock Guardrail** denied topic + agent instructions |
| ❌ | Never recommend treatment or medication | **Bedrock Guardrail** denied topic + agent instructions |
| ❌ | Never say patient "has" a condition | **Bedrock Guardrail** content filter + agent instructions |
| ✅ | Always include medical disclaimer | **Bedrock Guardrail** mandatory suffix + all Lambda responses |
| ✅ | Only explain, organize, trend, summarize | Action Group Lambdas are read-only |
| ✅ | Show extraction confidence | `extractionConfidence` in upload response |
| ✅ | Cite which data was used | `citedObservations` + `citedReports` everywhere |
| ✅ | Translate disclaimers too | Disclaimer in user's preferred language |

> **Why Guardrails matter:** With Strands, safety was only in the system prompt — if the LLM hallucinated past it, there was no safety net. Bedrock Guardrails run as a separate filter AFTER the LLM generates output but BEFORE the user sees it. Even if the model tries to diagnose, the guardrail blocks it.

---

## Client ↔ Backend Contract

| Rule | Detail |
|---|---|
| Dates | `YYYY-MM-DD` |
| Timestamps | ISO 8601 `YYYY-MM-DDTHH:MM:SSZ` |
| File upload | Presigned URL → PUT raw bytes directly to S3 (no base64, no size limit) |
| Supported files | JPEG, PNG, PDF (multi-page supported) |
| Report status polling | Every 3s on upload screen, on-launch elsewhere |
| Chat history | Max 10 messages in `conversationHistory` |
| Disclaimer | Always present in response language — client must display |
| Content-Type | `application/json` for APIs, `image/jpeg` or `application/pdf` for S3 PUT |
| CORS | `*` hackathon, restricted in production |
| Language codes | ISO 639-1: `en`, `hi`, `mr`, `ta`, `bn`, `te`, `kn`, `gu` |

---

## AWS Setup — Step by Step

### What You're Deploying (Full Resource List)

Your current SAM template only covers the basics. Here's everything the current design needs:

| Resource | In Original Template? | Action |
|---|---|---|
| API Gateway | ✅ | Update — add new routes for family, insights, followups, voice, upload-url, status |
| Lambda 1: `chetana-extract-report` | ✅ | Update — add S3 trigger, presigned URL generation, family APIs |
| Lambda 2: `chetana-agent-chat` | ✅ | **Rewrite** — now invokes Bedrock Agent, not Strands |
| Lambda 3: `chetana-insights-engine` | 🆕 | New — insights, follow-ups, EventBridge trigger |
| Lambda 4: `chetana-voice-gateway` | 🆕 | New — Nova 2 Sonic session broker |
| Lambda: `chetana-action-health-data` | 🆕 | New — Action Group for Bedrock Agent (reports, observations) |
| Lambda: `chetana-action-family` | 🆕 | New — Action Group for Bedrock Agent (family, members) |
| Lambda: `chetana-action-trends` | 🆕 | New — Action Group for Bedrock Agent (trends, patterns) |
| DynamoDB | ✅ | Update — PK changes from `PATIENT#` to `FAMILY#`, new entity types |
| S3: reports bucket | ✅ | Update — add CORS config + S3 event notification |
| S3: guidelines bucket/folder | 🆕 | New — clinical guideline documents for Knowledge Base |
| Bedrock Agent | 🆕 | New — managed agent resource |
| Bedrock Guardrails | 🆕 | New — SaMD safety enforcement |
| Bedrock Knowledge Base | 🆕 | New — RAG over reports + guidelines |
| S3 Vectors | 🆕 | New — vector store for embeddings (cheaper than OpenSearch) |
| EventBridge Rule | 🆕 | New — daily cron for Lambda 3 |

> **That's 10 new resources** on top of the original 5. The SAM template needs a major expansion.

### 1. Install Tools

```bash
# AWS CLI — https://aws.amazon.com/cli
aws --version

# SAM CLI
pip install aws-sam-cli
sam --version

# Python 3.12 — python.org
python3 --version

# Docker Desktop — docker.com
docker --version

# Node.js 18+ — nodejs.org (for web frontend)
node --version
```

### 2. Create IAM User (Don't Use Root)

**2a.** IAM Console → Users → Create → name: `chetana-dev`
**2b.** Attach `AdministratorAccess`
**2c.** Security credentials → Create access key → CLI → **copy both keys**
**2d.** Lock root: delete root keys, enable MFA, log out, switch to `chetana-dev`

### 3. Configure CLI

```bash
aws configure
# Access Key, Secret, us-east-1, json

aws sts get-caller-identity
# Verify "chetana-dev" in ARN
```

### 4. Enable Bedrock Model Access

> ⚠️ #1 new-user mistake. Models are OFF by default.

Bedrock console → Model access → Enable all four:
- `amazon.nova-2-lite-v1:0`
- `amazon.nova-2-multimodal-embeddings-v1:0`
- `amazon.nova-2-sonic-v1:0`
- `amazon.nova-micro-v1:0`

### 5. Set Up Bedrock Agent (Console First, Then Codify)

Do this in the console first to understand how it works. Codify in SAM later.

**5a. Create Guardrail:**
1. Bedrock console → Guardrails → Create guardrail
2. Name: `chetana-samd-guardrail`
3. **Denied topics** — add these:
   - "Medical diagnosis" — block any output that diagnoses a condition
   - "Treatment recommendation" — block medication or treatment advice
   - "Condition assertion" — block statements that patient "has" or "suffers from" a condition
4. **Content filters** — set to HIGH for harmful content
5. **Word filters** — optionally block specific phrases like "you have diabetes", "I recommend taking"
6. Save → note the **Guardrail ID** and **Version**

**5b. Create Knowledge Base:**
1. Bedrock console → Knowledge bases → Create
2. Name: `chetana-health-kb`
3. **Data source:** S3 — point to your guidelines bucket/folder
4. **Embedding model:** Amazon Nova Multimodal Embeddings (`amazon.nova-2-multimodal-embeddings-v1:0`)
5. **Vector store:** Choose **S3 Vectors** (not OpenSearch — saves $175/month)
6. **Chunking strategy:** Default (or semantic if available)
7. Create → **Sync** the data source
8. Note the **Knowledge Base ID**

**5c. Create Agent:**
1. Bedrock console → Agents → Create agent
2. Name: `chetana-health-agent`
3. **Foundation model:** Amazon Nova 2 Lite (`amazon.nova-2-lite-v1:0`)
4. **Agent instructions** (system prompt):
```
You are Chetana, a health record assistant for Indian families. You help patients 
understand their own lab results by retrieving and summarizing their data.

STRICT RULES — violating any of these is a critical failure:
- NEVER generate a diagnosis
- NEVER recommend treatment or medication
- NEVER say the patient "has" or "suffers from" any condition
- ALWAYS end your response with a disclaimer in the user's language
- ONLY use data from the patient's own records via your tools
- When referencing data, cite the specific report date and test name
- If you don't have enough data, say so honestly
- Respond in the user's preferred language using simple, non-medical terminology
```
5. **Action Groups** — add three (each points to a Lambda):

| Action Group Name | Lambda ARN | Description |
|---|---|---|
| `HealthDataActions` | `chetana-action-health-data` | Get reports, observations, report detail |
| `FamilyActions` | `chetana-action-family` | Get family dashboard, member details, abnormals |
| `TrendActions` | `chetana-action-trends` | Compute trends, compare members, detect patterns |

6. For each Action Group, you define an **OpenAPI schema** describing the functions. Example for HealthDataActions:
```yaml
openapi: 3.0.0
info:
  title: Health Data Actions
  version: 1.0.0
paths:
  /getObservations:
    get:
      summary: Get lab observations for a family member
      operationId: getObservations
      parameters:
        - name: familyId
          in: query
          required: true
          schema: { type: string }
        - name: memberId
          in: query
          required: true
          schema: { type: string }
        - name: loincCode
          in: query
          required: false
          schema: { type: string }
          description: Filter by LOINC code (e.g. 2339-0 for Glucose)
        - name: fromDate
          in: query
          required: false
          schema: { type: string }
        - name: toDate
          in: query
          required: false
          schema: { type: string }
      responses:
        '200':
          description: List of observations
  /getReports:
    get:
      summary: Get all reports for a family member
      operationId: getReports
      parameters:
        - name: familyId
          in: query
          required: true
          schema: { type: string }
        - name: memberId
          in: query
          required: false
          schema: { type: string }
      responses:
        '200':
          description: List of reports
  /getReportDetail:
    get:
      summary: Get full detail of a specific report
      operationId: getReportDetail
      parameters:
        - name: familyId
          in: query
          required: true
          schema: { type: string }
        - name: reportId
          in: query
          required: true
          schema: { type: string }
      responses:
        '200':
          description: Report with all observations
```

7. **Attach Knowledge Base** — select `chetana-health-kb`
8. **Attach Guardrail** — select `chetana-samd-guardrail`
9. **Create alias** — name it `prod`
10. Note the **Agent ID** and **Agent Alias ID** — these go into Lambda 2's environment variables

**5d. Upload clinical guideline documents:**
Upload PDF/text files to the guidelines S3 folder, then re-sync the Knowledge Base:
- ADA diabetes screening guidelines
- Standard test frequency documents
- Normal range reference charts by age/gender
- Common lab panel interpretation guides

### 6. Update SAM Template

The SAM template needs these additions beyond the original:

```yaml
# ── NEW: S3 CORS for presigned URL uploads ──
ReportsBucket:
  Type: AWS::S3::Bucket
  Properties:
    BucketName: !Sub chetana-reports-${AWS::AccountId}
    BucketEncryption:
      ServerSideEncryptionConfiguration:
        - ServerSideEncryptionByDefault:
            SSEAlgorithm: AES256
    PublicAccessBlockConfiguration:
      BlockPublicAcls: true
      BlockPublicPolicy: true
      IgnorePublicAcls: true
      RestrictPublicBuckets: true
    CorsConfiguration:                    # 🆕 Required for presigned URL PUT from browser
      CorsRules:
        - AllowedHeaders: ['*']
          AllowedMethods: [PUT]
          AllowedOrigins: ['*']           # Restrict in production
          MaxAge: 300

# ── NEW: S3 Event → Lambda 1 trigger ──
ExtractReportFunction:
  Type: AWS::Serverless::Function
  Properties:
    # ... existing config ...
    Events:
      # ... existing API Gateway events ...
      S3Upload:                           # 🆕 Trigger on file upload
        Type: S3
        Properties:
          Bucket: !Ref ReportsBucket
          Events: s3:ObjectCreated:*
      UploadUrl:                          # 🆕 New route
        Type: Api
        Properties:
          RestApiId: !Ref ChetanaApi
          Path: /reports/upload-url
          Method: POST
      ReportStatus:                       # 🆕 New route
        Type: Api
        Properties:
          RestApiId: !Ref ChetanaApi
          Path: /reports/{reportId}/status
          Method: GET

# ── NEW: Lambda 3 — Insights Engine ──
InsightsEngineFunction:
  Type: AWS::Serverless::Function
  Properties:
    FunctionName: chetana-insights-engine
    CodeUri: lambdas/insights_engine/
    Handler: app.lambda_handler
    Timeout: 120                          # Extended thinking needs time
    MemorySize: 1024
    Policies:
      - DynamoDBCrudPolicy:
          TableName: !Ref FhirTable
      - Statement:
          Effect: Allow
          Action: [bedrock:InvokeModel]
          Resource: "*"
    Events:
      DailySchedule:                      # 🆕 EventBridge cron
        Type: Schedule
        Properties:
          Schedule: rate(1 day)
          Description: Daily proactive insights generation
      GetInsights:
        Type: Api
        Properties:
          RestApiId: !Ref ChetanaApi
          Path: /insights
          Method: GET
      MarkInsightRead:
        Type: Api
        Properties:
          RestApiId: !Ref ChetanaApi
          Path: /insights/{insightId}
          Method: PATCH
      GenerateInsights:
        Type: Api
        Properties:
          RestApiId: !Ref ChetanaApi
          Path: /insights/generate
          Method: POST
      GetFollowups:
        Type: Api
        Properties:
          RestApiId: !Ref ChetanaApi
          Path: /followups
          Method: GET
      UpdateFollowup:
        Type: Api
        Properties:
          RestApiId: !Ref ChetanaApi
          Path: /followups/{followUpId}
          Method: PATCH

# ── NEW: Lambda 4 — Voice Gateway ──
VoiceGatewayFunction:
  Type: AWS::Serverless::Function
  Properties:
    FunctionName: chetana-voice-gateway
    CodeUri: lambdas/voice_gateway/
    Handler: app.lambda_handler
    Timeout: 60
    MemorySize: 512
    Policies:
      - Statement:
          Effect: Allow
          Action:
            - bedrock:InvokeModelWithResponseStream
          Resource: "*"
    Events:
      VoiceSession:
        Type: Api
        Properties:
          RestApiId: !Ref ChetanaApi
          Path: /voice/session
          Method: POST

# ── NEW: Action Group Lambdas (invoked by Bedrock Agent, NOT by API Gateway) ──
ActionHealthDataFunction:
  Type: AWS::Serverless::Function
  Properties:
    FunctionName: chetana-action-health-data
    CodeUri: lambdas/agent_chat/action_groups/
    Handler: health_data_actions.lambda_handler
    Timeout: 15
    MemorySize: 256
    Policies:
      - DynamoDBReadPolicy:
          TableName: !Ref FhirTable

ActionFamilyFunction:
  Type: AWS::Serverless::Function
  Properties:
    FunctionName: chetana-action-family
    CodeUri: lambdas/agent_chat/action_groups/
    Handler: family_actions.lambda_handler
    Timeout: 15
    MemorySize: 256
    Policies:
      - DynamoDBReadPolicy:
          TableName: !Ref FhirTable

ActionTrendsFunction:
  Type: AWS::Serverless::Function
  Properties:
    FunctionName: chetana-action-trends
    CodeUri: lambdas/agent_chat/action_groups/
    Handler: trend_actions.lambda_handler
    Timeout: 15
    MemorySize: 256
    Policies:
      - DynamoDBReadPolicy:
          TableName: !Ref FhirTable

# ── NEW: S3 bucket for clinical guidelines (Knowledge Base data source) ──
GuidelinesBucket:
  Type: AWS::S3::Bucket
  Properties:
    BucketName: !Sub chetana-guidelines-${AWS::AccountId}

# ── UPDATED: Agent Chat Lambda — now invokes Bedrock Agent ──
AgentChatFunction:
  Type: AWS::Serverless::Function
  Properties:
    FunctionName: chetana-agent-chat
    CodeUri: lambdas/agent_chat/
    Handler: app.lambda_handler
    Timeout: 60
    MemorySize: 512
    Environment:
      Variables:
        AGENT_ID: !Ref BedrockAgentId       # Set after console setup
        AGENT_ALIAS_ID: !Ref BedrockAgentAlias  # Set after console setup
    Policies:
      - Statement:
          Effect: Allow
          Action:
            - bedrock:InvokeAgent            # 🆕 Not InvokeModel
          Resource: "*"
    Events:
      Chat:
        Type: Api
        Properties:
          RestApiId: !Ref ChetanaApi
          Path: /chat
          Method: POST
```

> **Note:** Bedrock Agent, Knowledge Base, Guardrails, and S3 Vectors are not yet fully supported in SAM/CloudFormation. Set these up via console first (Step 5), then reference their IDs as parameters in your template. AWS is adding CloudFormation support progressively.

### 7. Build & Deploy

```bash
sam build
sam deploy --guided   # first time

# Subsequent
sam build && sam deploy
```

| Prompt | Enter |
|---|---|
| Stack Name | `chetana` |
| Region | `us-east-1` |
| Confirm changes? | `Y` |
| Allow IAM role creation? | `Y` |
| Disable rollback? | `N` |
| Save to samconfig.toml? | `Y` |

### 8. Post-Deploy Wiring

After `sam deploy` completes:

1. **Get outputs:**
```bash
aws cloudformation describe-stacks --stack-name chetana --query "Stacks[0].Outputs"
```

2. **Copy API Base URL** → paste into Android Retrofit client + web `.env.local`

3. **Wire Action Group Lambdas to Bedrock Agent:**
   - Go to Bedrock Agent console → your agent → Action Groups
   - For each group, set the Lambda ARN from the deploy output
   - Save and create a new agent alias version

4. **Set Agent ID and Alias ID as Lambda 2 environment variables:**
```bash
aws lambda update-function-configuration \
  --function-name chetana-agent-chat \
  --environment "Variables={AGENT_ID=your-agent-id,AGENT_ALIAS_ID=your-alias-id,FHIR_TABLE_NAME=chetana-fhir}"
```

5. **Sync Knowledge Base:**
   - Upload guideline docs to `chetana-guidelines-{accountId}` bucket
   - Bedrock console → Knowledge Base → Sync

6. **Test S3 event trigger:**
   - Upload a test image to the reports bucket manually
   - Check CloudWatch logs for Lambda 1 to confirm it triggered

### 9. Deployment Order (What Depends on What)

```
Step 1: sam deploy (creates Lambdas, DynamoDB, S3, API Gateway, EventBridge)
  ↓
Step 2: Console — create Guardrail (no dependencies)
  ↓
Step 3: Console — create Knowledge Base + S3 Vectors + sync guidelines
  ↓
Step 4: Console — create Bedrock Agent + attach Action Groups + KB + Guardrail
  ↓
Step 5: Wire Agent ID/Alias into Lambda 2 env vars
  ↓
Step 6: Test full pipeline with Postman
```

> **Why this order:** The Bedrock Agent needs Lambda ARNs (from Step 1) to create Action Groups. Lambda 2 needs the Agent ID (from Step 4) to invoke it. So SAM deploys first, then console setup, then wiring.

### 10. Teardown

```bash
# 1. Delete Bedrock resources via console first (Agent, KB, Guardrail)
# 2. Then delete SAM stack
sam delete --stack-name chetana

# 3. Empty and delete S3 buckets (SAM won't delete non-empty buckets)
aws s3 rm s3://chetana-reports-{accountId} --recursive
aws s3 rm s3://chetana-guidelines-{accountId} --recursive
```

---

## Testing Checklist

| # | Endpoint | Test | Expected |
|---|---|---|---|
| 1 | `POST /family` | Create family | 200, familyId |
| 2 | `POST /family/members` | Add 2 members | 200, memberIds |
| 3 | `POST /reports/upload-url` | Get presigned URL | 200, reportId + uploadUrl + s3Key |
| 4 | `PUT` to presigned URL | Upload JPEG directly to S3 | 200 from S3 |
| 5 | `GET /reports/{id}/status` | Poll immediately | 200, status: "processing" |
| 6 | `GET /reports/{id}/status` | Poll after ~10s | 200, status: "completed" with observations + insights |
| 7 | `PUT` to presigned URL | Upload multi-page PDF | 200 from S3, then completed with all pages extracted |
| 8 | `GET /reports` | All reports | 200, array with status fields |
| 9 | `GET /observations?loincCode=2339-0` | Filter glucose | 200, glucose only |
| 10 | `GET /family/dashboard` | Family view | 200, members + family insights |
| 11 | `POST /chat` (English) | "What was my last glucose?" | 200, English reply + disclaimer |
| 12 | `POST /chat` (Hindi) | "मेरा ग्लूकोज़ कैसा है?" | 200, Hindi reply + Hindi disclaimer |
| 13 | `GET /insights?unread=true` | Get unread insights | 200, unread cards with severity + citations |
| 14 | `PATCH /insights/{id}` | Mark as read | 200, insight marked read |
| 15 | `GET /followups` | Pending reminders | 200, follow-ups |
| 16 | `POST /reports/upload-url` | Invalid contentType | 400 VALIDATION_ERROR |
| 17 | `POST /chat` | "Do I have diabetes?" | Agent declines + disclaimer |

---

## LOINC Codes — 30 Tests, 8 Categories

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

## Build Order — Phased Roadmap

### Phase 1: Core Pipeline (Hackathon MVP)
| # | What | Target |
|---|---|---|
| 1 | `shared/` — models, repos, config, LOINC, prompts | Foundation |
| 2 | `extract_report/` — Nova 2 Lite extraction + FHIR | Lambda 1 |
| 3 | `sam build && sam deploy` + Postman tests | Deploy |
| 4 | `agent_chat/` — Bedrock Agent + Action Groups + basic chat | Lambda 2 |
| 5 | Web: upload + chat screens (Next.js) | Frontend |
| 6 | Android: camera + upload + chat | Frontend |

### Phase 2: Family + Insights
| # | What | Target |
|---|---|---|
| 7 | Family CRUD + DynamoDB expansion | Lambda 1 |
| 8 | Member auto-detection from reports | Lambda 1 |
| 9 | Family dashboard (web + Android) | Frontend |
| 10 | `insights_engine/` — proactive insights + follow-ups | Lambda 3 |
| 11 | EventBridge scheduled trigger + pull notification polling | Infra |
| 12 | Insights + follow-ups screens | Frontend |

### Phase 3: RAG + Language + Voice
| # | What | Target |
|---|---|---|
| 13 | Bedrock Knowledge Base + Nova Embeddings | Infra |
| 14 | RAG in agent tools (cite original reports) | Lambda 2 |
| 15 | Vernacular languages (Nova Micro translation) | Lambda 2, 3 |
| 16 | Language selector across all screens | Frontend |
| 17 | Nova 2 Sonic voice (Hindi + English) | Lambda 4 |
| 18 | Voice UI in chat | Frontend |

### Phase 4: Polish + Demo
| # | What |
|---|---|
| 19 | E2E test: snap → extract → insights → chat → voice |
| 20 | Demo script: 3 scenarios |
| 21 | Record backup video |
| 22 | DevPost submission + blog |

---

## Cost Estimation — Per Report, Per Family, At Scale

### Nova Model Pricing (us-east-1, on-demand, standard tier)

| Model | Input | Output | Image | Use |
|---|---|---|---|---|
| Nova 2 Lite | $0.30 / 1M tokens | $2.50 / 1M tokens | included in input | Extraction, reasoning, insights |
| Nova Micro | $0.035 / 1M tokens | $0.14 / 1M tokens | text only | Translations, notifications |
| Nova Multimodal Embeddings | $0.14 / 1M tokens | — | $0.0001 / image | RAG embeddings |
| Nova 2 Sonic | $0.30 / 1M input | $2.50 / 1M output | — | Hindi/English voice |

### Cost Per Report — Full Pipeline (1-page lab report, ~8 tests)

| Step | Model | What Happens | Est. Input Tokens | Est. Output Tokens | Cost |
|---|---|---|---|---|---|
| 1. Extraction | Nova 2 Lite | Image + prompt → structured JSON | ~1,500 | ~800 | $0.0025 |
| 2. Embedding | Nova MME | Embed report image for RAG | 1 image | — | $0.0001 |
| 3. Immediate insights | Nova 2 Lite (thinking: high) | Analyse vs history, generate cards | ~3,000 | ~500 | $0.0022 |
| 4. Storage | DynamoDB | Write report + observations | — | — | $0.0001 |
| | | | | **Upload subtotal** | **~$0.005 (~₹0.42)** |

### Cost Per Chat Turn

| Step | Model | What Happens | Est. Input Tokens | Est. Output Tokens | Cost |
|---|---|---|---|---|---|
| 5. Chat Q&A | Nova 2 Lite (Bedrock Agent) | Agent reasons, calls tools, answers | ~2,500 | ~400 | $0.0018 |
| 6. Translation | Nova Micro | Translate to Hindi/Marathi/Tamil | ~500 | ~500 | $0.0001 |
| | | | | **Chat turn subtotal** | **~$0.002 (~₹0.17)** |

### Cost Per Daily Insights Run (per member)

| Step | Model | What Happens | Est. Input Tokens | Est. Output Tokens | Cost |
|---|---|---|---|---|---|
| 7. Pattern detection | Nova 2 Lite (thinking: high) | Scan full observation history | ~4,000 | ~600 | $0.0027 |
| 8. Follow-up suggestion | Nova 2 Lite | Infer next test dates | ~2,000 | ~300 | $0.0014 |
| 9. Translate insight | Nova Micro | Vernacular insight card | ~400 | ~400 | $0.0001 |
| | | | | **Daily insight subtotal** | **~$0.004 (~₹0.34)** |

### Cost Per Voice Turn (Hindi/English)

| Step | Model | What Happens | Cost |
|---|---|---|---|
| 10. Voice Q&A | Nova 2 Sonic | ~10s speech input + ~10s response | ~$0.002 (~₹0.17) |

### Full Lifecycle of 1 Report

| Scenario | Cost | INR (approx) |
|---|---|---|
| Upload + extract + embed + immediate insights | $0.005 | ₹0.42 |
| + 1 chat question in Hindi | $0.007 | ₹0.59 |
| + 5 chat questions in Hindi | $0.015 | ₹1.26 |
| + daily scheduled insight + follow-up | $0.019 | ₹1.60 |
| + 1 voice question | $0.021 | ₹1.77 |
| **Full experience (upload + 5 chats + insight + voice)** | **~$0.02** | **~₹1.70** |

### Monthly Cost at Scale

| Usage Profile | Reports/mo | Chats/mo | Cost/mo | INR/mo |
|---|---|---|---|---|
| 1 family, casual use | 2 | 20 | ~$0.30 | ~₹25 |
| 1 family, active use | 5 | 50 | ~$0.70 | ~₹60 |
| 100 families, casual | 200 | 2,000 | ~$30 | ~₹2,500 |
| 1,000 families, active | 5,000 | 50,000 | ~$350 | ~₹29,000 |

### Infrastructure Costs (Beyond AI Models)

| Service | Hackathon Cost | Production Cost | Notes |
|---|---|---|---|
| DynamoDB | ~$0 (free tier) | ~$1.25/GB/month | PAY_PER_REQUEST, negligible at low scale |
| S3 | ~$0 (free tier) | ~$0.023/GB/month | Report images, very cheap |
| Lambda | ~$0 (free tier) | ~$0.20/1M requests | 1M free requests/month in free tier |
| API Gateway | ~$0 (free tier) | ~$3.50/1M requests | First 1M free |
| **OpenSearch Serverless** | **Avoid — use S3 Vectors** | **~$175/month minimum** | **Hidden cost killer — 2 OCU minimum at $0.24/OCU/hr** |
| S3 Vectors | ~$0 | Pay per query | **Recommended for hackathon + early production** |
| Bedrock Agent | $0 | $0 | No extra charge — you pay for model tokens only |
| Bedrock Guardrails | $0 | $0.75/1K text units | Free in preview, cheap at scale |
| Bedrock Knowledge Base | $0 | $0 | No extra charge — you pay for embedding model + vector store |

> **Cost killer alert:** OpenSearch Serverless has a minimum of 2 OCUs (~$175/month) even with zero traffic. For hackathon and early production, use **S3 Vectors** instead — it's pay-per-query with no minimum. Switch to OpenSearch only when you need sub-100ms retrieval at high volume.

### Hackathon Budget Estimate

| Item | Est. Cost |
|---|---|
| AI model calls (all testing + demo) | $5–10 |
| DynamoDB + S3 + Lambda + API Gateway | $0 (free tier) |
| S3 Vectors | $0–1 |
| **Total hackathon spend** | **~$5–15** |

### Cost Optimization Tips

1. **Use extended thinking wisely:** `high` intensity for abnormal patterns only, `medium` for routine, `low` for simple lookups — this is the biggest cost lever
2. **Nova Micro for translations:** 10x cheaper than Nova 2 Lite for simple text tasks
3. **S3 Vectors over OpenSearch:** saves $175/month minimum at low scale
4. **Prompt caching:** for repeated system prompts, can reduce input token cost by up to 90% (available for Nova models)
5. **Batch inference:** for daily scheduled insights, use batch mode at 50% off on-demand pricing
6. **Intelligent prompt routing:** Bedrock can auto-route between Nova 2 Lite and Nova Micro based on complexity (~30% savings)

---

## Post-Hackathon — Production Roadmap

| Priority | What | AWS Service | Why |
|---|---|---|---|
| **P0** | User auth | Cognito | Multi-family, PHI |
| **P0** | HIPAA BAA | AWS Artifact | Legal |
| **P0** | KMS encryption | KMS | PHI keys |
| **P1** | VPC isolation | VPC + Endpoints | Network security |
| **P1** | Audit logging | CloudTrail + CloudWatch | Compliance |
| **P1** | Push notifications | SNS + FCM / Web Push | Real-time alerts instead of polling |
| **P1** | API protection | WAF | Abuse prevention |
| **P2** | CI/CD | CodePipeline + SAM | Automation |
| **P2** | Multi-region | DynamoDB Global Tables | DR + latency |
| **P2** | Multi-Agent Orchestrator | Bedrock Agents | Orchestrator Agent to route between Health, Billing, and Doctor Booking sub-agents |
| **P2** | GraphRAG | Neptune Analytics | Health knowledge graph |
| **P3** | More Sonic languages | Roadmap | Tamil, Marathi, Bengali voice |
| **P3** | Prescription OCR | Nova 2 Lite | Medication → RxNorm codes |