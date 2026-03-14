---
description: Test Lambda functions locally without Docker using the Python test runner
---
# AWS SAM Local Testing Workflow

Test Lambda handlers locally without Docker. The `test_local.py` runner imports handlers directly into Python and calls them with mock events against real DynamoDB.

> ⚠️ **Python 3.12 venv must be active.** Uses the same venv as the deploy workflow.

1. Activate the Python 3.12 virtual environment.
```powershell
# Windows
cd backend
.\.venv\Scripts\Activate.ps1

# Mac/Linux
source backend/.venv/bin/activate
```

2. Run the import smoke test — no AWS credentials required, confirms all Lambdas can be imported.
// turbo
```bash
py -3.12 test_local.py imports
```
All 5 Lambdas should print `[OK]`. If any show `[FAIL]` with `ImportError`, the import path is broken — fix before deploying.

3. Run the full integration suite (requires `aws configure` credentials).
```bash
py -3.12 test_local.py all
```
This creates a real family and member in DynamoDB, then tests observations and insights. Family/member IDs are printed at the end — copy them into Postman for further testing.

4. Run individual suites as needed.
```bash
py -3.12 test_local.py families       # POST /families + members flow
py -3.12 test_local.py observations   # GET /observations
py -3.12 test_local.py insights       # GET /insights
```
