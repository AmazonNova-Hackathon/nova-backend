# MediAgent — Chetana Patient Portal

Serverless AWS backend for an Android/web app that extracts lab data using **Amazon Nova**, stores it in a hierarchical DynamoDB structure, and enables agentic multi-turn chat via Amazon Bedrock.

---

## 🔥 Key Features

- **Multimodal Extraction**: Supports `PNG`, `JPEG`, `WebP`, and `PDF` (high-res mobile photos & documents).
- **Hierarchical REST API**: Strictly organized paths: `/families/{fid}/members/{mid}/...`.
- **Session-Aware AI Chat**: Native support for multi-turn conversations via Bedrock Agents.
- **Health Trends**: Automated mapping of extracted values to **LOINC codes** for longitudinal tracking.
- **Data Integrity**: Optimized for medical precision with `Decimal` serialization and `meta`-block tracking.

---

## 🗂 Project Structure

```
medi-agent/
├── backend/              # SAM application (all Lambda functions)
│   ├── lambdas/
│   │   ├── shared/       # Shared models (FHIR), Repositories, Serialization
│   │   ├── family_management/
│   │   ├── extract_report/
│   │   ├── insights_engine/
│   │   └── agent_chat/
│   ├── template.yaml     # SAM infrastructure definition
│   └── test_local.py     # Rapid local testing (No Docker)
├── frontend-web/         # Vite + React web frontend
└── docs/                 # API Specs, Guidelines, Architecture
```

---

## ⚡ Quick Start

### 1. Backend Setup

**Prerequisite**: Python 3.12 exactly.

```powershell
cd backend
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt

# Deploy
sam build
sam deploy
```

### 2. Postman Testing (v4.1+)

Import into Postman:
1. **Collection**: `docs/mediagent-postman-collection.json`
2. **Environment**: `docs/mediagent-environment.json`

The collection includes scripts that automatically capture `familyId`, `memberId`, and `sessionId` to streamline testing.

---

## 🏗 Architecture & Data Flow

### The Upload Pipeline (10-20s)
1. **Provision**: Client gets a signed S3 URL via `/upload-url`.
2. **Binary Stream**: Client puts raw bytes directly to **Amazon S3**.
3. **Trigger**: Client signals the Lambda to start processing.
4. **AI Analysis**: `ExtractReportFunction` invokes **Amazon Nova** (multimodal) to extract data.
5. **Persistence**: Results are validated via **Pydantic**, serialized as **Decimals**, and saved to **DynamoDB**.

### Data Hierarchy (Single-Table Design)
| Key | Pattern |
|:---|:---|
| **Family** | `FAMILY#{fid} / META` |
| **Member** | `FAMILY#{fid} / MEMBER#{mid}` |
| **Report** | `FAMILY#{fid} / REPORT#{mid}#{date}#{id}` |
| **Observation**| `FAMILY#{fid} / OBS#{mid}#{loinc}#{date}#{id}` |

---

## 📖 Key Documentation

- **API Specification**: [docs/openapi.yml](docs/openapi.yml)
- **Deep Architecture**: [docs/architecture.md](docs/architecture.md)
- **Android Integration**: [docs/android_guidelines.md](docs/android_guidelines.md)
- **Product Requirements**: [docs/PRD.md](docs/PRD.md)

---

## 🐛 Common Issues

- **Float Serialization**: Fixed! The system now uses `Decimal` types for all DB operations.
- **MIME Mismatch**: Fixed! Supported formats include `image/*` and `application/pdf`.
- **Chat Context loss**: Fixed! Always pass the `sessionId` from the previous response.
