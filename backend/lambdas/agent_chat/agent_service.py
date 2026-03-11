import json
import logging
from botocore.config import Config
import boto3

from lambdas.shared.config import BEDROCK_REGION, NOVA_MODEL_ID
from lambdas.shared.models.requests import ChatRequest
from lambdas.shared.models.responses import ChatResponse

from prompts import AGENT_SYSTEM_PROMPT
from agent_tools import (
    BEDROCK_TOOLS,
    execute_tool,
    reset_citations,
    get_citations
)

logger = logging.getLogger()

retry_config = Config(retries={"max_attempts": 5, "mode": "standard"})
bedrock_client = boto3.client('bedrock-runtime', region_name=BEDROCK_REGION, config=retry_config)

def process_chat(raw_payload: dict) -> ChatResponse:
    req = ChatRequest(**raw_payload)
    
    # 1. Reset citations
    reset_citations()
    
    # 2. Reconstruct Converse API message history
    messages = []
    
    for turn in req.conversationHistory:
        if turn.role in ["user", "assistant"]:  # Converse API only supports user/assistant messages at root
            messages.append({
                "role": turn.role,
                "content": [{"text": turn.content}]
            })
            
    messages.append({
        "role": "user",
        "content": [{"text": req.message}]
    })

    system_prompts = [{"text": AGENT_SYSTEM_PROMPT}]

    try:
        # Loop to natively handle agentic tool choices
        MAX_LOOPS = 5
        response_text = ""
        
        for _ in range(MAX_LOOPS):
            response = bedrock_client.converse(
                modelId=NOVA_MODEL_ID,
                messages=messages,
                system=system_prompts,
                toolConfig={"tools": BEDROCK_TOOLS}
            )
            
            output_message = response['output']['message']
            messages.append(output_message) # Append assistant's turn directly
            
            # Check if Nova decided to stop or use a tool
            stop_reason = response['stopReason']
            
            if stop_reason == "tool_use":
                tool_results = []
                
                for content_block in output_message['content']:
                    if 'toolUse' in content_block:
                        tool_use = content_block['toolUse']
                        tool_use_id = tool_use['toolUseId']
                        tool_name = tool_use['name']
                        tool_input = tool_use['input']
                        
                        logger.info(f"Agent requested tool: {tool_name} with {tool_input}")
                        
                        try:
                            # Safely execute python dispatch passing the patientId context silently
                            result_string = execute_tool(tool_name, req.patientId, tool_input)
                            tool_result = {
                                "toolUseId": tool_use_id,
                                "content": [{"json": {"result": result_string}}]
                            }
                        except Exception as e:
                            logger.error(f"Tool execution failed: {e}")
                            tool_result = {
                                "toolUseId": tool_use_id,
                                "content": [{"text": f"Error executing tool: {e}"}],
                                "status": "error"
                            }
                            
                        tool_results.append({"toolResult": tool_result})
                
                # Append the user response mapping the tool output back into the conversation for thinking
                messages.append({
                    "role": "user",
                    "content": tool_results
                })
            else:
                # Finished reasoning
                for content_block in output_message['content']:
                    if 'text' in content_block:
                        response_text += content_block['text']
                break
                
    except Exception as e:
        logger.error(f"Bedrock Converse Agent failure: {e}")
        response_text = "I'm having trouble analyzing your request right now. Please try again later."
    
    # 5. Extract Citations
    referenced_reports, referenced_observations = get_citations()
    
    return ChatResponse(
        reply=response_text,
        referencedReports=referenced_reports,
        referencedObservations=referenced_observations
    )
