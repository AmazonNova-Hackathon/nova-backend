# Implementation Checklist for MediAgent (PRD2)

## 1. AWS Manual Infrastructure Setup (Prerequisites)
- [x] Enable model access in Bedrock for the following models:
  - `amazon.nova-2-lite-v1:0`
  - `amazon.nova-2-multimodal-embeddings-v1:0`
  - `amazon.nova-2-sonic-v1:0`
  - `amazon.nova-micro-v1:0`
- [x] Create Bedrock Guardrail (`chetana-samd-guardrail`) with Medical diagnosis/treatment denied topics.
- [x] Create S3 Bucket for Guidelines (`mediagent-guidelines-623810446100`).
- [x] Create S3 Bucket for KB Chunks (`mediagent-guidelines-chunks-623810446100`).
- [x] Create Bedrock Knowledge Base (`chetana-health-kb`) using S3 data source and Nova Multimodal Embeddings with S3 Vectors.
- [x] Create Bedrock Agent (`chetana-health-agent`) - ID: `QKOQZAZT9C`, Alias ID: `7MVGBJC3KY`.

## 2. Shared Layer & Data Model Updates
- [x] Update DynamoDB models to use `FAMILY#{familyId}` partition keys.
- [x] Add new Pydantic models for `Family`, `Member`, `InsightCard`, `FollowUp` in [fhir.py](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/lambdas/shared/models/fhir.py).
- [x] Rewrite [dynamo_repository.py](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/lambdas/shared/repositories/dynamo_repository.py) for new schema patterns.

## 3. Lambda 1: Extract & FHIR Storage (Update)
- [x] Implement `POST /reports/upload-url` (Presigned URL generation with family-aware S3 key).
- [x] Implement S3 Event trigger handler (`s3:ObjectCreated:*`) in [app.py](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/lambdas/extract_report/app.py).
- [x] Rewrite [extraction_service.py](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/lambdas/extract_report/extraction_service.py) for async S3-triggered extraction via Nova 2 Lite.
- [x] Implement member auto-detection: Nova extracts `patientName` → fuzzy-matched to family members.
- [x] Implement `GET /reports/{id}/status` polling endpoint.

## 4. Lambda: Action Groups Implementation (New)
- [x] Create [health_data_actions.py](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/lambdas/agent_chat/action_groups/health_data_actions.py) — `getReports`, `getObservations`, `getReportDetail`.
- [x] Create [family_actions.py](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/lambdas/agent_chat/action_groups/family_actions.py) — `getFamilyDashboard`, `getMemberDetail`, `getAbnormals`.
- [x] Create [trend_actions.py](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/lambdas/agent_chat/action_groups/trend_actions.py) — `computeTrend`, `compareMembers`, `detectPatterns`.

## 5. Lambda 2: Chat Agent Wrapper (Rewrite)
- [x] Remove `strands-agents` (was not present in codebase).
- [x] Delete old `agent_service.py`, `agent_tools.py`, `prompts.py`.
- [x] Re-implement [app.py](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/lambdas/agent_chat/app.py) to invoke `bedrock-agent-runtime.invoke_agent`.

## 6. Lambda 3: Insights Engine (New)
- [x] Create `lambdas/insights_engine/` with EventBridge handler and REST routes.
- [x] Implement `GET /insights`, `PATCH /insights/{id}`, `GET /followups`, `PATCH /followups/{id}` API routes.
- [x] Integrate Bedrock KB (RAG) to ground insights against clinical guidelines.

## 7. Lambda 4: Voice Gateway (New)
- [x] Implement `POST /voice/session` brokering bidirectional audio with Nova 2 Sonic.

## 8. SAM Template ([template.yaml](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/template.yaml)) Updates
- [x] Define Lambda 3, Lambda 4, and Action Group lambdas.
- [x] Define S3 CORS and S3 Upload Event Trigger.
- [x] Define EventBridge schedule for Lambda 3.
- [x] Map all API Gateway routes (Family, Insights, Followups, Reports, Voice).
- [x] Set Environment Variables for Agent ID (`QKOQZAZT9C`) and Agent Alias ID (`7MVGBJC3KY`).

## 9. Final Deployment & Integration Pipeline
- [x] Run `sam build && sam deploy`. (Build verified locally with Python 3.12)
- [x] Retrieve Lambda ARNs and wire them into the Bedrock Agent Action Groups via AWS Console.
- [x] Standardize Python environment to 3.12 for local SAM compatibility.

## 10. Family Management API (New)
- [x] Create `lambdas/family_management/` with requirements, bootstrap, and app.
- [x] Implement `POST /families` (New family creation).
- [x] Implement `POST /families/{id}/members` (Add member).
- [x] Implement `GET /families/{id}/members` (List family members).
- [x] Update [template.yaml](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/template.yaml) with `FamilyManagementFunction` and API routes.

## 11. Final Polish & Handover
- [x] Rename `swagger.yaml` to `openapi.yml` for GitHub preview support.
- [x] Update `README.md` with local setup instructions (Python 3.12 venv).
- [x] Reverse-update PRD specifications with final implementation detail.
- [x] Update Android integration guidelines for new Family/Smart-Onboarding APIs.
- [x] Verify full project compliance (67/67 tests passing).
