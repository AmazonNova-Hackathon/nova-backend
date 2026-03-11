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

### 3. Deployment
```bash
cd backend
sam build
sam deploy --guided
```
Accept default prompts. Make sure to accept the deployment since the functions do not use AWS IAM authentication to remain natively accessible from Android via an API Key context.

## 🗝️ API Authentication (API Keys)
The API logic requires an `x-api-key` header for security. You can find the deployed Auto-Generated API Key from the API Gateway dashboard inside the AWS Console under **API Keys**.

## 📖 Useful Links
- [PRD & Endpoint Specs](docs/PRD.md)
- [Architecture Details](docs/architecture.md)
- [API Postman Collection](docs/mediagent-postman-collection.json)
