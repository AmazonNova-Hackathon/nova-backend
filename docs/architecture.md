# MediAgent Architecture

## AWS Service Topology
MediAgent relies on an event-driven serverless topology heavily optimized for Amazon Bedrock multimodal generation.

- **API Gateway**: Provides REST endpoints protected by x-api-key.
- **AWS Lambda**: Contains business logic segmented strictly out of handlers.
- **Amazon DynamoDB**: Operates as a single-table datastore handling composite index-based access to FHIR Diagnostic Reports and Observations.
- **Amazon S3**: Cheap object storage containing the encrypted raw photos.
- **Amazon Bedrock**: Used symmetrically for both visual capability (`extract_report` payload ingestion) and agentic LLM context routing (`agent_chat` Bedrock Converse).

## Code Structure Patterns
Our lambdas rigidly follow the **Service-Repository Pattern**:

1. `app.py` (Handler Layer): Isolates AWS API Gateway context routing mapping, validates incoming data models, and acts as an ultimate error boundary.
2. `*_service.py` (Service Layer): Owns core Bedrock interactions, orchestrates business logic loops (Agent loops), and passes sanitized data.
3. `dynamo_repository.py` / `s3_repository.py` (Data Access): Boto3 clients only belong here. Queries and puts abstract DynamoDB implementations.

## Agent Loop Pattern without "Strands"
We bypass heavy abstractions for cross-platform stability. We utilize the native AWS Bedrock `converse` library passing `toolConfig` parameters.
1. The lambda pushes available DynamoDB query definitions to Nova Lite dynamically.
2. Nova responds with a `stop_reason == "tool_use"`.
3. Native python routes execution down into our data access layer and pushes back an artificial user-turn mimicking tool results into the conversational array.
