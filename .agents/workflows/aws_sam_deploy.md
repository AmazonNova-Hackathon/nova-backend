---
description: Build and deploy the AWS SAM backend stack
---
# AWS SAM Deployment Workflow

This workflow builds the Lambda packages and deploys the stack to AWS using SAM CLI.

1. Navigate to the `backend` directory (or the directory containing `template.yaml`).
2. Run the SAM build command to resolve Python dependencies and package the applications.
// turbo
```bash
sam build
```
3. Deploy the application to the AWS account. If this is the first time, use `--guided`. Otherwise, run the standard deploy.
// turbo
```bash
sam deploy --resolve-s3 || sam deploy --guided
```
