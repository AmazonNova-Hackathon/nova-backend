---
name: AWS Serverless Python Development
description: Specialized skill for developing enterprise-grade AWS Lambda functions in Python using AWS SAM, DynamoDB, and Bedrock.
---
# AWS Serverless Python Skill

When tasked with implementing or modifying AWS Lambdas in this project, adhere to the following guidelines:

## 1. Project Scaffolding
- Respect the existing structural separation: `handlers/`, `services/`, `repositories/`, and `models/`.
- Do not mix DynamoDB read/writes with Bedrock API calls. They belong in separate repository or service files.

## 2. Best Practices for Boto3 & Bedrock
- Always configure the `boto3` client with custom retry logic for robust API interactions.
```python
from botocore.config import Config
retry_config = Config(retries={"max_attempts": 5, "mode": "standard"})
bedrock = boto3.client("bedrock-runtime", config=retry_config)
```
- When using `invoke_model` with Amazon Nova, handle multimodal payloads strictly following Nova's expected message array schema.

## 3. Best Practices for DynamoDB
- Implement Single-Table Design.
- Use composite primary keys (PK/SK) using constants that are clearly documented.
- Leverage `boto3.resource('dynamodb').Table()` for easier item serialization than the low-level client.

## 4. Lambda Powertools
- Integrate `aws-lambda-powertools` for Logging, Metrics, and Tracing out-of-the-box.
- Decorate handlers with `@logger.inject_lambda_context(log_event=True)`.

If the user asks to implement a new endpoint, remember to update `template.yaml` with the corresponding API Gateway event triggers.
