# AWS Serverless Backend Rules

## 1. Architecture Constraints
- **Layered Architecture**: Abide strictly by the Handler -> Service -> Repository structure.
  - `app.py`: Routing and exception boundaries only. No business logic.
  - `*_service.py`: Core orchestration, calls Bedrock, parses inputs.
  - `repositories/`: Exclusively for interacting with external data sources like DynamoDB / S3.
- **Pydantic Everywhere**: Do not return raw dictionaries from services. Map inputs to Request Models and outputs to Response Models.

## 2. Infrastructure as Code (SAM)
- All AWS infrastructure MUST be defined in `template.yaml`.
- Lambda functions should use Python 3.12.
- Environment variables must be passed from `template.yaml` to the Lambda handler and accessed via a centralized `config.py` file.
- Strictly adhere to least-privilege IAM policies. Use precise SAM Policy Templates when possible (e.g., `DynamoDBCrudPolicy`).

## 3. Generative AI (Amazon Nova)
- All Bedrock prompts should reside in `prompts.py` so they are decoupled from business logic.
- Catch `ThrottlingException` and use boto3 configuration with exponential backoff for a resilient Bedrock integration.
- Ensure that system prompts firmly bound the AI boundaries (e.g., medical disclaimers, no diagnoses).

## 4. Dependencies & Observability
- Add `aws-lambda-powertools` to `requirements.txt`.
- Prefer structured JSON logging over plain `print()` statements so CloudWatch logs are easily queryable.

## 5. Deployment and Version Control
- **NEVER** push code to a remote Git repository without explicit confirmation from the user.
- **DO NOT** manually deploy every single granular change to AWS using `sam deploy`. 
- Since a GitHub Actions CI/CD pipeline is configured, the agent should rely on committing (with permission) and allowing the pipeline to handle AWS deployment automatically, or bulk local deployments when explicitly requested.
