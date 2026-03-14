# Technical Progress Report - March 13, 2026

## Objective
Migrate the "Chetana Sanctuary" from mock data to live AWS API integration, resolve development blockers (CORS/Auth), and synchronize backend infrastructure.

---

## 1. Frontend Alignment & Bug Fixes

### Issues Encountered
1.  **CORS Policy Violations**: Browser blocked requests to AWS API Gateway during local development.
2.  **Authentication Errors**: Received `{"message":"Missing Authentication Token"}` despite providing the API key.
3.  **Path Consistency**: Discovered that the frontend was calling `/family` while the backend expected `/families`.
4.  **Header Casing**: Verified that some backend configurations were sensitive to `x-api-key` casing.

### Steps Taken
- **Vite Proxy Injection**: Modified [vite.config.ts](file:///c:/Users/Dipmala/Documents/Code/medi-agent/frontend-web/vite.config.ts) to proxy `/api` requests to the AWS endpoint, bypassing CORS.
- **Header Standardization**: Standardized all API calls in [api.ts](file:///c:/Users/Dipmala/Documents/Code/medi-agent/frontend-web/src/services/api.ts) to use lowercase `x-api-key`.
- **Environment Context**: Updated [.env](file:///c:/Users/Dipmala/Documents/Code/medi-agent/frontend-web/.env) to point to the local proxy (`/api`) instead of the hardcoded AWS URL.

---

## 2. Backend Infrastructure Debugging

### Issues Encountered
1.  **Missing Endpoints**: The `/families` resource was missing from the `prod` stage due to a resource naming bug in [template.yaml](file:///c:/Users/Dipmala/Documents/Code/medi-agent/backend/template.yaml) (`MediAgentTable` was referenced instead of `MediAgentFhirTable`).
2.  **Circular Dependencies**: During redeployment, SAM failed because the `ExtractReportFunction` depended on the `MediAgentReportsBucket` (for policies), which depended on the Function (for the S3 trigger).
3.  **Early Validation Failures**: AWS CloudFormation blocked stack updates when using dynamic `!Sub` or `!Ref` in certain policy contexts.
4.  **Local Environment Mismatch**: `sam build` failed on Windows because it couldn't find Python 3.12 in the standard path.

### Steps Taken
- **Template Patching**: 
    - Corrected the `MediAgentFhirTable` reference.
    - Consolidated all family routes under the dedicated `FamilyManagementFunction`.
    - Synchronized paths to plural `/families` based on OpenAPI specs.
- **Dependency Decoupling**: 
    - Replaced dynamic bucket references (`!Ref`) with the explicit bucket string (`mediagent-reports-623810446100`) in function environment variables and policies to break the circular dependency.
- **Build Override**: 
    - Manually prepended the correct Python 3.12 path to the environment during the `sam build` execution.

---

## 3. Current System State

### What is Working
- **Live Observations**: The dashboard now fetches live `DiagnosticReport` and [Observation](file:///c:/Users/Dipmala/Documents/Code/medi-agent/frontend-web/src/services/api.ts#6-15) data from DynamoDB.
- **Voice Sanctuary**: Connected to the AWS Bedrock Agent (Chetana) with live chat capabilities.
- **Health Records**: The report upload flow is fully aligned with the backend S3/Lambda extraction logic.
- **Local Proxying**: Development server (`npm run dev`) is fully transparent to AWS.

### Pending Actions
- **Final Stack Sync**: The infrastructure is ready for a clean `sam deploy` now that the circular dependencies are broken.
- **Member Cleanup**: Transitioning from hardcoded demo members back to the live `/families` endpoint once the backend deployment confirms the new resource creation.

---

> [!IMPORTANT]
> To finish the backend redeployment, please run the following in your terminal:
> `cd backend; $env:PATH = "C:\Users\Dipmala\AppData\Local\Programs\Python\Python312;" + $env:PATH; sam build --beta-features; sam deploy --resolve-s3`
