---
description: Build and deploy the AWS SAM backend stack
---
# AWS SAM Deployment Workflow

This workflow builds the Lambda packages and deploys the stack to AWS using SAM CLI.

> ⚠️ **Python 3.12 venv must be active before every build.** SAM validates the `python` binary on PATH — using any other version causes `PythonPipBuilder:Validation` failure.

1. Activate the Python 3.12 virtual environment.
```powershell
# Windows
cd backend
.\.venv\Scripts\Activate.ps1

# Mac/Linux
source backend/.venv/bin/activate
```
If the venv does not exist yet, create it first:
```powershell
py -3.12 -m venv backend/.venv
```

2. Verify Python version.
// turbo
```bash
python --version   # Must print Python 3.12.x
```

3. Run the SAM build command.
// turbo
```bash
sam build
```

4. Deploy the application to AWS.
// turbo
```bash
sam deploy
```
If this is the first deploy, add `--guided` and accept all defaults.

5. After deploy, fetch the API key value and update `frontend-web/.env`.
```bash
aws apigateway get-api-keys --include-values --region us-east-1 --output table
```
