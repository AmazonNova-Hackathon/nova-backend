# Technical Review: MediAgent AWS Backend

## Overall Assessment
The proposed architecture in [PRD.md](file:///c:/Users/Dipmala/Documents/Code/medi-agent/PRD.md) is exceptionally well-structured for a hackathon. It balances rapid prototyping with enterprise-grade design patterns. The use of a layered architecture with Pydantic ensures robustness, and the dual-use of Amazon Nova Lite for both vision and agentic reasoning is a compelling hackathon narrative.

## Strengths
1. **Separation of Concerns**: The Handler -> Service -> Repository pattern is excellent. It prevents "fat handler" anti-patterns and ensures business logic and data access are decoupled.
2. **IAM Principle of Least Privilege**: Using execution roles instead of hard-coded API keys to invoke Bedrock is a best practice often overlooked in hackathons.
3. **Data Modeling**: The single-table DynamoDB design is well-optimized for the listed access patterns.
4. **Strong Typing**: Leveraging Pydantic across boundaries adds the reliability of static typing to Python payloads.

## Constructive Feedback & Potential Pitfalls to Avoid
1. **API Gateway 30-Second Limit**: API Gateway has a hard 30-second timeout. Strands Agent reasoning loops involving multiple Bedrock inferences and DynamoDB fetches could easily exceed this limit, especially since Nova takes a few seconds per turn. 
   > [!WARNING]
   > Monitor latency closely. If the chat endpoint times out, consider migrating it to a WebSocket API (which allows up to 2-hour connections) or implementing an async payload model.
2. **Observability**: Debugging autonomous agents requires visibility into their thought process.
   > [!TIP]
   > Integrate `aws-lambda-powertools` for structured logging, metrics, and tracing. Enable AWS X-Ray to visualize the exact duration of each Bedrock model invocation.
3. **Bedrock Throttling**: During peak hackathon judging, on-demand Bedrock limits might cause `ThrottlingException`.
   > [!IMPORTANT]
   > Ensure your `BedrockModel` implementation uses standard AWS standard retries with exponential backoff.
4. **Image Handling Payload Limits**: API Gateway restricts payloads to 10MB. 1MB JPEG constraints mentioned in the PRD are safe, but enforce this validation early before decoding base64 to save memory.

I have created several targeted workflows, skills, and rule documents in your repository to help Antigravity build out this backend seamlessly according to AWS best practices.
