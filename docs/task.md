# Implementation Checklist for MediAgent (PRD2)

## 1. AWS Manual Infrastructure Setup (Prerequisites)
- [x] Enable model access in Bedrock for the following models:
  - `amazon.nova-2-lite-v1:0`
  - `amazon.nova-2-multimodal-embeddings-v1:0`
  - `amazon.nova-2-sonic-v1:0`
  - `amazon.nova-micro-v1:0`
- [x] Create Bedrock Guardrail (`chetana-samd-guardrail`) with Medical diagnosis/treatment denied topics.
- [x] Create S3 Bucket for Guidelines (`chetana-guidelines-{accountId}`).
- [x] Create Bedrock Knowledge Base (`chetana-health-kb`) using S3 data source and Nova Multimodal Embeddings with S3 Vectors.
- [x] Create Bedrock Agent (`chetana-health-agent`) with Nova 2 Lite, attach KB and Guardrail. (Action groups will be attached after SAM deploy).

## 2. Shared Layer & Data Model Updates
- [x] Update DynamoDB models to use `FAMILY#{familyId}` partition keys.
- [x] Add new Pydantic models for Family, Member, Insight, and Follow-up entities.
- [x] Update `loinc_mapping.py` and structured requests/responses.

## 3. Lambda 1: Extract & FHIR Storage (Update)
- [x] Implement `POST /reports/upload-url` (Presigned URL generation).
- [x] Implement S3 Event trigger handler (`s3:ObjectCreated:*`).
- [x] Update [extraction_service.py](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/lambdas/extract_report/extraction_service.py) to use Nova 2 Lite multimodal + PDF support.
- [x] Implement member auto-detection from name and map to DynamoDB.
- [x] Implement `GET /reports/{id}/status` for client polling.

## 4. Lambda: Action Groups Implementation (New)
- [ ] Create `chetana-action-health-data` (getReports, getObservations, getReportDetail).
- [ ] Create `chetana-action-family` (getFamilyDashboard, getMemberDetail, getAbnormals).
- [ ] Create `chetana-action-trends` (computeTrend, compareMembers, detectPatterns).

## 5. Lambda 2: Chat Agent Wrapper (Rewrite)
- [x] Remove `strands-agents` from requirements and code. (Not present initially)
- [ ] Re-implement handler to invoke `bedrock-agent-runtime.invoke_agent`.
- [ ] Support streaming chunks to client.

## 6. Lambda 3: Insights Engine (New)
- [ ] Implement EventBridge handler for daily cron proactively evaluating members.
- [ ] Implement `GET /insights`, `PATCH /insights/{id}`, `GET /followups`, `PATCH /followups/{id}` API routes.
- [ ] Integrate Bedrock KB (RAG) to ground insights against clinical guidelines.

## 7. Lambda 4: Voice Gateway (New)
- [ ] Implement `POST /voice/session` brokering bidirectional audio with Nova 2 Sonic.

## 8. SAM Template ([template.yaml](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/template.yaml)) Updates
- [ ] Define Lambda 3, Lambda 4, and Action Group lambdas.
- [ ] Define S3 CORS and S3 Upload Event Trigger.
- [ ] Define EventBridge schedule for Lambda 3.
- [ ] Map all API Gateway routes.
- [ ] Set Environment Variables for Agent ID and Agent Alias ID.

## 9. Final Deployment & Integration Pipeline
- [ ] Run `sam build && sam deploy`.
- [ ] Retrieve Lambda ARNs and wire them into the Bedrock Agent Action Groups via AWS Console.
- [ ] Update Lambda 2 environments with deployed Agent IDs.
- [ ] End-to-end testing of `POST /reports/upload-url` -> Direct S3 PUT -> S3 trigger -> Polling.
