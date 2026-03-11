# MediAgent Backend

Serverless AWS backend for an Android app that extracts lab data using Amazon Nova Lite, stores it as FHIR R4 in DynamoDB, and lets users seamlessly chat with their health records via a natively implemented agentic workflow on Bedrock Converse.

## 🚀 Quick Start

### Prerequisites
* Python 3.12+ 
* AWS CLI installed and configured
* AWS SAM CLI installed
* Docker Desktop (optional, but recommended if Python versions drift)

### 1. Install Dependencies
```bash
# Recommended: Create a virtual environment
python -m venv venv
venv\Scripts\activate  # Windows
# source venv/bin/activate # Mac/Linux

# Install development dependencies
pip install -r requirements-dev.txt
```

### 2. Local Testing
You can start the AWS API Gateway locally to test the endpoints without deploying.
```bash
cd backend
sam build
sam local start-api
```
*Note: This requires valid AWS credentials to reach the cloud DynamoDB and Bedrock.*

### 3. Deployment (AWS SAM)
The AWS Serverless Application Model (SAM) handles provisioning ALL necessary infrastructure and internal Lambda environment variables (like DynamoDB table names) for you.

```bash
cd backend
sam build
sam deploy --guided
```
Accept default prompts. Keep in mind that since Hackathon APIs are public to the Android app without Cognito, say **yes** when SAM warns that your functions have no authentication.

## 🔐 Environment & Security

### 1. API Authentication (API Keys)
The API logic requires an `x-api-key` header for security. You can find the deployed Auto-Generated API Key from the API Gateway dashboard inside the AWS Console under **API Keys**.

### 2. CI/CD GitHub Secrets
To use the automated GitHub Actions deployment pipeline (`.github/workflows/deploy.yml`), configure the following Repository Secrets in your GitHub repo settings:
- `AWS_ACCESS_KEY_ID`: IAM user access key (needs CloudFormation/Lambda/API Gateway permissions).
- `AWS_SECRET_ACCESS_KEY`: IAM user secret access key.

### 3. AWS Native Integration
We do not use messy `.env` files locally because we rely strictly on AWS native CloudFormation referencing. The `template.yaml` defines and securely passes resources like `TABLE_NAME` and `BUCKET_NAME` directly down into the Lambda function's runtime environment!

## 📖 API & Documentation
- **API Spec**: [Swagger OpenAPI](docs/swagger.yaml) | [Postman Collection](docs/mediagent-postman-collection.json)
- **Design Details**: [PRD](docs/PRD.md) | [Architecture Patterns](docs/architecture.md)
