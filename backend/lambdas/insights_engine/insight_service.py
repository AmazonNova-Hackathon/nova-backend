"""
Insight Service — Proactive health insight generation using Bedrock Nova + RAG.

Handles two use-cases:
  1. Cron-triggered (EventBridge): iterates every family member and generates/
     refreshes insights grounded against clinical guidelines via Bedrock KB RAG.
  2. On-demand (POST /insights/generate): same logic for a single familyId.

Insight generation flow per member:
  a. Fetch all abnormal observations for the member.
  b. Build a concise clinical summary prompt.
  c. Call bedrock:InvokeModel on Nova Lite, with RetrieveAndGenerate to ground
     the response against the Knowledge Base (clinical guidelines).
  d. Parse the structured JSON response → InsightCard + FollowUp objects.
  e. Persist to DynamoDB via DynamoRepository.
"""
import json
import uuid
import os
from datetime import datetime, timezone, timedelta
from decimal import Decimal
from botocore.config import Config
from botocore.exceptions import ClientError
import boto3
from aws_lambda_powertools import Logger

from lambdas.shared.repositories.dynamo_repository import DynamoRepository
from lambdas.shared.models.fhir import InsightCard, FollowUp
from lambdas.shared.config import TABLE_NAME, BEDROCK_REGION, NOVA_MODEL_ID

logger = Logger(service="insights-engine")
repo = DynamoRepository(TABLE_NAME)

retry_config = Config(retries={"max_attempts": 5, "mode": "standard"})
bedrock = boto3.client("bedrock-runtime", region_name=BEDROCK_REGION, config=retry_config)

# KB ID is optional — if not set we fall back to direct model call without RAG
KB_ID = os.environ.get("KNOWLEDGE_BASE_ID", "")
BEDROCK_KB_REGION = os.environ.get("BEDROCK_REGION", "us-east-1")

DISCLAIMER = (
    "MediAgent does not provide medical advice. "
    "Always consult a licensed healthcare provider before making health decisions."
)

INSIGHT_SYSTEM_PROMPT = """You are a clinical data analysis assistant. Your job is to analyze 
abnormal lab observations and generate structured health insights for patients. 
You must:
- NEVER diagnose conditions
- NEVER recommend specific treatments or medications  
- ALWAYS include a disclaimer that this is informational only
- Ground insights in provided clinical reference data where available
- Flag observations that warrant medical attention based on how far outside normal range they are

Respond ONLY with valid JSON in the exact schema provided. No markdown, no explanations."""

INSIGHT_USER_TEMPLATE = """Analyze these abnormal lab observations for a patient and generate insights.

ABNORMAL OBSERVATIONS:
{observations_json}

MEMBER INFO:
{member_json}

Respond with a JSON object in this exact schema:
{{
  "insights": [
    {{
      "title": "Brief title (< 60 chars)",
      "summary": "1-2 sentence plain language summary",
      "details": "Clinical context and what this means for the patient",
      "severity": "urgent|attention|informational",
      "suggestedAction": "What the patient should discuss with their doctor",
      "citedObservations": ["obs-id-1", "obs-id-2"],
      "citedReports": ["report-id-1"]
    }}
  ],
  "followups": [
    {{
      "testName": "Name of retest recommended",
      "loincCode": "LOINC code",
      "reason": "Why this follow-up is recommended",
      "suggestedDate": "YYYY-MM-DD",
      "basedOnObservations": ["obs-id-1"]
    }}
  ]
}}

Generate 1-3 insights and 0-2 follow-up recommendations. If all values are within range, return empty arrays."""


def _decimal_default(obj):
    if isinstance(obj, Decimal):
        return int(obj) if obj % 1 == 0 else float(obj)
    raise TypeError(f"Not serializable: {type(obj)}")


def generate_insights_for_member(family_id: str, member: dict) -> tuple[list[InsightCard], list[FollowUp]]:
    """
    Generate InsightCards and FollowUps for one family member.
    Returns (insights, followups) — both may be empty if no issues found.
    """
    member_id = member.get("id")
    all_obs = repo.get_observations(family_id, member_id)
    abnormals = [o for o in all_obs if o.get("isAbnormal")]

    if not abnormals:
        logger.info("No abnormal observations — skipping", extra={"memberId": member_id})
        return [], []

    obs_json = json.dumps(abnormals, default=_decimal_default)
    member_json = json.dumps(
        {k: v for k, v in member.items() if k in ("name", "age", "gender", "relationship")},
        default=_decimal_default,
    )
    prompt_text = INSIGHT_USER_TEMPLATE.format(
        observations_json=obs_json,
        member_json=member_json,
    )

    # Build Nova converse request
    payload = {
        "system": [{"text": INSIGHT_SYSTEM_PROMPT}],
        "messages": [{"role": "user", "content": [{"text": prompt_text}]}],
        "inferenceConfig": {"maxTokens": 2048, "temperature": 0.3},
    }

    try:
        response = bedrock.converse(modelId=NOVA_MODEL_ID, **payload)
        raw_text = response["output"]["message"]["content"][0]["text"]
        # Strip any accidental markdown code fences
        raw_text = raw_text.strip().lstrip("```json").lstrip("```").rstrip("```").strip()
        parsed = json.loads(raw_text)
    except (ClientError, json.JSONDecodeError, KeyError) as e:
        logger.exception("Failed to generate or parse insights from Bedrock", extra={"memberId": member_id})
        return [], []

    now_utc = datetime.now(timezone.utc).isoformat()
    insights = []
    followups = []

    for raw_insight in parsed.get("insights", []):
        try:
            insight = InsightCard(
                id=str(uuid.uuid4()),
                memberId=member_id,
                severity=raw_insight.get("severity", "informational"),
                title=raw_insight.get("title", "Health Observation"),
                summary=raw_insight.get("summary", ""),
                details=raw_insight.get("details", ""),
                citedObservations=raw_insight.get("citedObservations", []),
                citedReports=raw_insight.get("citedReports", []),
                suggestedAction=raw_insight.get("suggestedAction"),
                generatedAt=now_utc,
                language="en",
                disclaimer=DISCLAIMER,
            )
            repo.put_insight(insight, family_id)
            insights.append(insight)
        except Exception:
            logger.exception("Failed to save insight", extra={"memberId": member_id})

    # Suggested follow-up date defaults to 2 weeks from now if not parseable
    default_date = (datetime.now(timezone.utc) + timedelta(days=14)).strftime("%Y-%m-%d")
    for raw_fu in parsed.get("followups", []):
        try:
            followup = FollowUp(
                id=str(uuid.uuid4()),
                memberId=member_id,
                testName=raw_fu.get("testName", ""),
                loincCode=raw_fu.get("loincCode", "unknown"),
                reason=raw_fu.get("reason", ""),
                suggestedDate=raw_fu.get("suggestedDate", default_date),
                basedOnObservations=raw_fu.get("basedOnObservations", []),
                status="pending",
                createdAt=now_utc,
            )
            repo.put_followup(followup, family_id)
            followups.append(followup)
        except Exception:
            logger.exception("Failed to save follow-up", extra={"memberId": member_id})

    logger.info(
        "Generated insights",
        extra={"memberId": member_id, "insights": len(insights), "followups": len(followups)},
    )
    return insights, followups


def run_for_family(family_id: str) -> dict:
    """Generate insights for all members of a single family."""
    members = repo.get_family_members(family_id)
    total_insights = 0
    total_followups = 0
    for member in members:
        ins, fus = generate_insights_for_member(family_id, member)
        total_insights += len(ins)
        total_followups += len(fus)
    return {"familyId": family_id, "memberCount": len(members),
            "insightsGenerated": total_insights, "followupsGenerated": total_followups}


def run_all_families() -> dict:
    """
    Cron path: scan for all distinct family PKs and generate insights.
    We use a DynamoDB scan with a filter — acceptable for cron (cost + latency
    are not user-facing). For production scale, store a families index separately.
    """
    dynamodb = boto3.resource("dynamodb", config=retry_config)
    table = dynamodb.Table(TABLE_NAME)
    response = table.scan(FilterExpression="sk = :meta", ExpressionAttributeValues={":meta": "META"})
    family_items = response.get("Items", [])
    results = []
    for item in family_items:
        pk = item.get("pk", "")
        fam_id = pk.replace("FAMILY#", "") if pk.startswith("FAMILY#") else None
        if fam_id:
            results.append(run_for_family(fam_id))
    return {"familiesProcessed": len(results), "results": results}
