# MediAgent Architecture & Data Flow

MediAgent is built on a serverless, event-driven architecture designed for high-performance medical report extraction and agentic health insights.

## System Topology

![Chetana AWS Architecture](aws_architecture.png)

### Detailed Service Map

```mermaid
graph TB
    subgraph Clients["🖥️ Clients"]
        Web["Web App<br/>(React + Vite)"]
        Android["Android App<br/>(Kotlin)"]
    end

    subgraph Edge["☁️ AWS Edge"]
        CF["CloudFront<br/>CDN Distribution"]
        S3Web[("S3<br/>Frontend Assets")]
        APIGW["API Gateway<br/>REST (prod stage)"]
    end

    subgraph Compute["⚡ AWS Lambda Functions"]
        L1["ExtractReportFunction<br/>Report Upload + Nova Extraction"]
        L2["AgentChatFunction<br/>Bedrock Agent Invocation"]
        L3["InsightsEngineFunction<br/>Proactive Insights + Follow-ups"]
        L5["FamilyManagementFunction<br/>Family & Member CRUD"]
    end

    subgraph Bedrock["🧠 Amazon Bedrock"]
        NovaPro["Nova Pro 1.0<br/>Reasoning + Extraction"]
        NovaMicro["Nova Micro<br/>Translations"]
        
        subgraph Agent["Bedrock Agent: chetana-health-agent"]
            AG1["Action Group:<br/>HealthDataActions"]
            AG2["Action Group:<br/>TrendActions"]
            AG3["Action Group:<br/>FamilyActions"]
            KB["Knowledge Base<br/>Clinical Guidelines"]
            GR["Guardrails<br/>SaMD Safety"]
        end
    end

    subgraph Storage["💾 Storage"]
        DDB[("DynamoDB<br/>Single Table<br/>(FHIR Data)")]
        S3Reports[("S3<br/>Report Images<br/>AES-256")]
    end

    EB["EventBridge<br/>Daily Cron Schedule"]

    %% Client → Edge
    Web --> CF
    Android --> CF
    CF -->|"Static Assets"| S3Web
    CF -->|"/api/*"| APIGW

    %% API Gateway → Lambdas
    APIGW --> L1
    APIGW --> L2
    APIGW --> L5
    APIGW --> L3

    %% Lambda → Bedrock
    L1 -->|"Multimodal Extraction"| NovaPro
    L2 -->|"invoke_agent()"| Agent
    L3 -->|"Insight Generation"| NovaPro
    L3 -->|"Translate"| NovaMicro

    %% Agent → Action Groups → DynamoDB
    AG1 --> DDB
    AG2 --> DDB
    AG3 --> DDB

    %% Lambda → Storage
    L1 -->|"Store FHIR"| DDB
    L1 -->|"Fetch Image"| S3Reports
    L5 --> DDB
    L3 --> DDB

    %% Upload flow
    Web -->|"Direct PUT<br/>(Presigned URL)"| S3Reports
    Android -->|"Direct PUT<br/>(Presigned URL)"| S3Reports

    %% EventBridge
    EB -->|"Scheduled Trigger"| L3

    %% Styles
    classDef client fill:#232F3E,stroke:#FF9900,color:#fff,stroke-width:2px
    classDef edge fill:#1B2631,stroke:#FF9900,color:#fff,stroke-width:2px
    classDef lambda fill:#D35400,stroke:#FF9900,color:#fff,stroke-width:2px
    classDef bedrock fill:#2E4053,stroke:#5DADE2,color:#fff,stroke-width:2px
    classDef storage fill:#1A5276,stroke:#FF9900,color:#fff,stroke-width:2px
    classDef event fill:#6C3483,stroke:#FF9900,color:#fff,stroke-width:2px

    class Web,Android client
    class CF,S3Web,APIGW edge
    class L1,L2,L3,L5 lambda
    class NovaPro,NovaMicro,AG1,AG2,AG3,KB,GR bedrock
    class DDB,S3Reports storage
    class EB event
```


---

## 3. Bedrock Agent: chetana-health-agent

### Agent Configuration

| Property | Value |
|---|---|
| **Agent ID** | `QKOQZAZT9C` |
| **Prod Alias ID** | `7MVGBJC3KY` |
| **Foundation Model** | Amazon Nova Pro 1.0 |
| **Region** | `us-east-1` |
| **Knowledge Base** | Medical guidelines KB (`JMI0YTDSGI`) |
| **Knowledge Base Purpose** | General clinical reference *only* (e.g. "what is a normal hemoglobin range?"). **Never** used for patient-specific data. |

### How the Chat Flow Works

1. Mobile/web sends `POST /families/{fid}/members/{mid}/chat` with `{ message, sessionId, language }`
2. `AgentChatFunction` calls `bedrock-agent-runtime.invoke_agent`, injecting `familyId` + `memberId` as **session attributes** and a mandatory per-request instruction via `promptSessionAttributes`
3. The Bedrock Agent orchestrates: it reads the instructions, picks the right action group function, and invokes the Lambda
4. The action group Lambda queries **DynamoDB directly** and returns structured JSON
5. The agent synthesizes the data into a natural-language reply and streams it back
6. `AgentChatFunction` assembles the streamed chunks and returns `{ reply, sessionId }`

> The `sessionId` is echoed back to the client and must be sent on subsequent turns to maintain conversation history server-side.

### Session Attributes

These are injected on every `invoke_agent` call in `agent_service.py` and are available in every action group Lambda via `event["sessionAttributes"]`:

| Attribute | Source | Purpose |
|---|---|---|
| `familyId` | URL path parameter | Scope all DynamoDB queries to the correct family |
| `memberId` | URL path parameter | Scope queries to the specific member being chatted with |
| `reportId` | Request body (optional) | If set, agent prioritises detail from this specific report |
| `language` | Request body (default: English) | Agent responds in the user's preferred language |

### Action Groups

Action groups are the agent's "tools" — functions it can call to fetch real patient data before answering.

> ⚠️ **Important**: Action groups must be manually registered with the agent via `backend/register_action_groups.py`. They are **not** automatically configured by SAM deploy.

#### 1. `chetana-action-health-data`
**Lambda**: `mediagent-ActionHealthDataFunction-NymM8yoEaZEe`
**Handler**: `lambdas/agent_chat/action_groups/health_data_actions.py`
**Purpose**: Core patient data retrieval — the primary action group for answering lab result questions.

| Function | Parameters | Description |
|---|---|---|
| `getReports` | `familyId`, `memberId` | Lists all uploaded lab reports for a member |
| `getObservations` | `familyId`, `memberId`, `loincCode`*, `testName`*, `fromDate`* | Fetches individual test values (e.g., hemoglobin). Resolves LOINC codes automatically and falls back to name-based search if code lookup returns empty. |
| `getReportDetail` | `familyId`, `memberId`, `reportId` | Returns full report with all observations inside it |

*optional

**LOINC Resolution Logic**: `getObservations` calls `loinc_mapping.find_loinc_code()` to convert natural language (e.g., "Hemoglobin") → LOINC code (e.g., `718-7`) before querying DynamoDB SK prefix `OBS#{memberId}#{loincCode}#`. If no results, falls back to case-insensitive name search across all member observations.

---

#### 2. `chetana-action-trends`
**Lambda**: `mediagent-ActionTrendsFunction-haaqPMZyvbmC`
**Handler**: `lambdas/agent_chat/action_groups/trend_actions.py`
**Purpose**: Time-series analysis, cross-member comparison, and pattern detection.

| Function | Parameters | Description |
|---|---|---|
| `computeTrend` | `familyId`, `memberId`, `loincCode`*, `testName`* | Returns direction (rising/falling/stable) based on last 3 readings |
| `compareMembers` | `familyId`, `memberIds` (comma-separated), `loincCode`*, `testName`* | Side-by-side latest value for a test across multiple members |
| `detectPatterns` | `familyId`, `memberId` | Scans all observations, surfaces abnormal + trending tests first |

---

#### 3. `chetana-action-family`
**Lambda**: `mediagent-ActionFamilyFunction-NgtU0gp6lQAK`
**Handler**: `lambdas/agent_chat/action_groups/family_actions.py`
**Purpose**: Profile discovery — lets the agent know who is in the family.

| Function | Parameters | Description |
|---|---|---|
| `getFamilyMembers` | `familyId` | Returns all members (name, age, relationship) in the family |

---

### Agent Instructions (Static — set in Agent Builder)

The full instruction set is in the Bedrock console under **Edit in Agent Builder → Instructions**. Key rules enforced:

1. **Always call action groups before answering** — never hallucinate health data from base model knowledge
2. **Use KB only for general reference** — never for patient-specific queries
3. **familyId + memberId are always available** — never ask the user for them
4. **Educational only** — no diagnosis, prescription, or medication recommendations
5. **LOINC code hints** — agent is given LOINC codes for common tests in the instructions

---

### Operational Runbook

#### After every `sam deploy`:
```bash
# Action group Lambda code is updated in-place (same ARN), so the
# Bedrock Agent automatically uses the new code — no re-registration needed.
# However, if you change function schemas or add new action groups:
cd backend
python register_action_groups.py
```

#### After `register_action_groups.py`:
1. Go to Bedrock Console → `chetana-health-agent` → **Aliases** tab
2. Click `prod` alias → Edit → **"Create new version and update alias"**
3. Save — this ensures the prod alias routes to the latest prepared DRAFT

#### CloudWatch Logs for action groups:
- `/aws/lambda/mediagent-ActionHealthDataFunction-NymM8yoEaZEe`
- `/aws/lambda/mediagent-ActionTrendsFunction-haaqPMZyvbmC`
- `/aws/lambda/mediagent-ActionFamilyFunction-NgtU0gp6lQAK`

#### If agent returns "insufficient data" / ignores action groups:
1. Check trace in Bedrock Console → test the agent → enable **"Show trace"**
2. If trace shows `knowledgebase_search` instead of action group calls → static agent instructions need updating and re-prepare
3. If action groups show empty results → check Lambda logs and DynamoDB data with the member's `familyId`/`memberId`
4. If action groups never appear in trace → run `python register_action_groups.py` again

---

### Recent Architectural Learnings & Challenges (Bedrock Agent Integration)

During the stabilization phase of the Bedrock Agent, several critical data-fetching bugs were identified and resolved. These are documented here to prevent regressions:

#### 1. The `reportId` Relational Disconnect
**Symptom**: The agent failed to read specific reports when asked via the Web Portal, but succeeded in the Bedrock testing console.
**Root Cause**: The Web Portal API injects a `reportId` session attribute to force the agent into reading a specific report. The agent complied by calling `getReportDetail(reportId)`. However, the report extraction pipeline was sometimes failing to attach the `reportId` property onto the individual child `Observation` records in DynamoDB, resulting in the Lambda returning 0 observations for that report.
**Resolution**:
- **Future-proofing**: `DynamoRepository.put_report_and_observations` was updated to explicitly coerce and stamp the `reportId` onto every observation before insertion, ignoring whatever the extraction LLM outputted.
- **Backward-compatibility**: `get_report_detail` was updated to loosely join observations. If `obs.reportId` is missing, it falls back to checking if `obs.date == report.date` to logically group them.

#### 2. The "Unknown" String Hallucination
**Symptom**: The agent would randomly fail to find common tests (like MCV or PCV) on subsequent retry attempts.
**Root Cause**: When the LLM was unsure of a LOINC code for a test, it would populate the parameters as `{ "loincCode": "unknown", "testName": "Mean Corpuscular Volume" }`. In Python, `search_query = loinc_code or test_name` evaluated the literal string `"unknown"` as truthy. The backend subsequently searched the entire database for the word "unknown" and ignored the actual test name.
**Resolution**: The action group Lambda now explicitly intercepts and nullifies the string `"unknown"` (case-insensitive) from any incoming agent parameters before evaluating the search logic.

#### 3. Brittle Exact-Match Fallbacks
**Symptom**: Searching for "Mean Corpuscular Volume" returned 0 results if the DB stored it as "Mean Corpuscular Volume (MCV)".
**Root Cause**: The fallback search logic was attempting an exact substring match of the `search_query` alone.
**Resolution**: The `getObservations` fallback mechanism was rewritten as a "wide search". If the primary exact LOINC lookup fails, the Lambda gathers every observation for that patient. It then creates a pool of search terms (the `testName`, `loincCode`, `search_query`) and checks if *any* of those terms appear as a substring inside either the saved observation's name or code. This fuzzier matching prevents the agent from dead-ending on slight syntactic differences.

#### 4. Unified Voice and Text Chat Interface
**Challenge**: The application previously had two distinct chat interfaces: `VoiceModal` (for general "Voice Sanctuary" family interactions) and `ChetanaAssistant` (for individual report-specific text chats). This caused fragmented user experiences and duplicated code. The Voice Sanctuary also lacked a way to review voice recordings before sending.
**Resolution**: 
- **Component Consolidation**: Merged both into a single cohesive interface (`ChetanaAssistant`) capable of handling both global family-level context and report-specific context via an `isGlobal` prop.
- **Audio Review & Live Transcription**: Integrated `MediaRecorder` to capture audio blobs for a "listen before you send" feature, alongside `react-speech-recognition` to provide live text transcription of the user's voice. If a user is not satisfied with the recording, they can discard the standalone media or textual transcription directly within the UI.

---

## Core Processing Flows

### 1. Medical Report Extraction (10-20 Seconds)
We use a high-performance **Presigned URL** pattern to bypass API Gateway limits:
1. **Provision**: Mobile requests a signed S3 URL via `/reports/upload-url`.
2. **Direct PUT**: Mobile streams binary data directly to S3 (supports PNG, JPEG, WebP, PDF).
3. **Signal**: Mobile calls `/reports/upload` with the S3 key.
4. **Extraction**:
    *   `ExtractReportFunction` fetches file bytes.
    *   Calls **Amazon Nova Pro/Lite** with multimodal capabilities.
    *   Nova extracts Lab Name, Date, and a list of Test Results.
    *   Lambda maps results to **LOINC codes** and calculates abnormal reference ranges.
5. **Persistence**: Saves a hierarchical `REPORT` item and multiple `OBS` (Observation) items to DynamoDB.

### 2. Proactive Insights Engine
*   **Trigger**: Can be manual (via UI) or scheduled (EventBridge Cron).
*   **Logic**: Scans the last 30 days of observations for a family member.
*   **AI Synthesis**: Generates `InsightCard` (Summary + Severity) and `FollowUp` (Next steps) items.
*   **Persistence**: Stored with `INSIGHT#` and `FOLLOWUP#` prefixes for instant retrieval.

## Data Organization (Single-Table Design)

We use **DynamoDB** with a strictly hierarchical Sort Key (SK) structure to ensure high performance and data locality:

| Resource | Partition Key (PK) | Sort Key (SK) |
| :--- | :--- | :--- |
| **Family** | `FAMILY#{fid}` | `META` |
| **Member** | `FAMILY#{fid}` | `MEMBER#{mid}` |
| **Report** | `FAMILY#{fid}` | `REPORT#{mid}#{date}#{id}` |
| **Observation** | `FAMILY#{fid}` | `OBS#{mid}#{loinc}#{date}#{id}` |
| **Insight** | `FAMILY#{fid}` | `INSIGHT#{mid}#{date}#{id}` |
| **FollowUp** | `FAMILY#{fid}` | `FOLLOWUP#{mid}#{date}#{id}` |

## Observability & Debugging

### Where to check Logs? (AWS CloudWatch)
*   **Report Processing**: `/aws/lambda/mediagent-ExtractReportFunction`
*   **Insights Generation**: `/aws/lambda/mediagent-InsightsEngineFunction`
*   **AI Chat (Sanctuary)**: `/aws/lambda/mediagent-AgentChatFunction`
*   **General Management**: `/aws/lambda/mediagent-FamilyManagementFunction`

### Where to check Data/Output?
1.  **Web App**:
    *   **Trends Tab**: Shows all `OBS` (Observation) records extracted.
    *   **Insights Tab**: Shows generated AI `InsightCards`.
    *   **History**: Shows the list of `REPORT` objects.
2.  **API**: Use the Postman collection to query `GET .../observations` or `GET .../insights`.
3.  **DynamoDB Console**: Look for items in the `mediagent-HealthData` table using the PK/SK patterns above.

## Code Standards
*   **Runtime**: Python 3.12 (Serverless)
*   **Architectural Pattern**: Service-Repository (isolates Boto3 calls from business logic).
*   **Metadata**: Every item includes a `meta` block with `version`, `createdAt`, `updatedAt`, and `isDeleted` for robust soft-delete and optimistic locking.
