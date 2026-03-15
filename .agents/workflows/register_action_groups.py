#!/usr/bin/env python3
"""
register_action_groups.py
=========================
Registers the three action group Lambdas with the Bedrock Agent (chetana-health-agent).
Run this ONCE after SAM deploy. Re-running is safe — it overwrites existing groups.

Usage:
    cd backend
    python register_action_groups.py
"""
import boto3
import json
import time

AGENT_ID = "QKOQZAZT9C"
AGENT_VERSION = "DRAFT"
REGION = "us-east-1"
ACCOUNT_ID = "623810446100"

# Lambda ARNs from SAM deploy output
LAMBDA_HEALTH_DATA = f"arn:aws:lambda:{REGION}:{ACCOUNT_ID}:function:mediagent-ActionHealthDataFunction-NymM8yoEaZEe"
LAMBDA_TRENDS      = f"arn:aws:lambda:{REGION}:{ACCOUNT_ID}:function:mediagent-ActionTrendsFunction-haaqPMZyvbmC"
LAMBDA_FAMILY      = f"arn:aws:lambda:{REGION}:{ACCOUNT_ID}:function:mediagent-ActionFamilyFunction-NgtU0gp6lQAK"

client = boto3.client("bedrock-agent", region_name=REGION)


def param(name, description, required=True, type_="string"):
    return {
        "description": description,
        "required": required,
        "type": type_,
    }, name


def make_function(name, description, params: list):
    """Build a function definition dict for Bedrock Agent action group."""
    parameters = {}
    for p_def, p_name in params:
        parameters[p_name] = p_def
    return {
        "name": name,
        "description": description,
        "parameters": parameters,
    }


def delete_existing_action_groups():
    """Remove any previously registered action groups before re-adding."""
    r = client.list_agent_action_groups(agentId=AGENT_ID, agentVersion=AGENT_VERSION)
    existing = r.get("actionGroupSummaries", [])
    # Skip built-in groups (user input, code interpreter, knowledge base)
    custom = [ag for ag in existing if ag["actionGroupName"] not in 
              ("UserInput", "CodeInterpreter") and not ag["actionGroupName"].startswith("AMAZON.")]
    for ag in custom:
        print(f"  Deleting existing action group: {ag['actionGroupName']}")
        client.delete_agent_action_group(
            agentId=AGENT_ID,
            agentVersion=AGENT_VERSION,
            actionGroupId=ag["actionGroupId"],
        )
        time.sleep(2)  # avoid throttle


def create_group(name, description, lambda_arn, functions):
    print(f"  Creating action group: {name}")
    r = client.create_agent_action_group(
        agentId=AGENT_ID,
        agentVersion=AGENT_VERSION,
        actionGroupName=name,
        description=description,
        actionGroupExecutor={"lambda": lambda_arn},
        functionSchema={"functions": functions},
        actionGroupState="ENABLED",
    )
    ag = r["agentActionGroup"]
    print(f"    → Created: {ag['actionGroupId']}  state={ag['actionGroupState']}")
    return ag


def prepare_agent():
    print("  Preparing agent (compiling DRAFT)...")
    r = client.prepare_agent(agentId=AGENT_ID)
    print(f"    → Agent status: {r['agentStatus']}")


def main():
    print("\n=== MediAgent: Register Action Groups ===\n")

    # --- Step 1: Clean up old groups ---
    print("[1/4] Removing existing custom action groups...")
    delete_existing_action_groups()

    # --- Step 2: Health Data Action Group ---
    print("\n[2/4] Registering chetana-action-health-data...")
    create_group(
        name="chetana-action-health-data",
        description="Retrieves patient lab reports and FHIR observations from DynamoDB. Use for any patient-specific lab result, test value, or report query.",
        lambda_arn=LAMBDA_HEALTH_DATA,
        functions=[
            make_function(
                "getReports",
                "Get all lab reports for a family member. Lists uploaded reports and their summaries.",
                [
                    param("familyId", "The family ID from session attributes", required=True),
                    param("memberId", "The member ID from session attributes", required=True),
                ],
            ),
            make_function(
                "getObservations",
                "Get specific lab test observations (e.g. hemoglobin, glucose) for a member. Pass loincCode for best accuracy, or testName if LOINC is unknown.",
                [
                    param("familyId", "The family ID from session attributes", required=True),
                    param("memberId", "The member ID from session attributes", required=True),
                    param("loincCode", "LOINC code for the test (e.g. 718-7 for hemoglobin). Preferred over testName.", required=False),
                    param("testName", "Human-readable test name (e.g. Hemoglobin). Used if loincCode unknown.", required=False),
                    param("fromDate", "Filter from this date (YYYY-MM-DD)", required=False),
                ],
            ),
            make_function(
                "getReportDetail",
                "Get full details of a specific lab report including all its observations.",
                [
                    param("familyId", "The family ID from session attributes", required=True),
                    param("memberId", "The member ID from session attributes", required=True),
                    param("reportId", "The report ID to fetch details for", required=True),
                ],
            ),
        ],
    )
    time.sleep(3)

    print("\n[3/4] Registering chetana-action-trends...")
    create_group(
        name="chetana-action-trends",
        description="Analyzes health trends over time, compares test results across family members, and detects patterns in observations.",
        lambda_arn=LAMBDA_TRENDS,
        functions=[
            make_function(
                "computeTrend",
                "Compute trend direction (rising/falling/stable) for a specific lab test over time for a member.",
                [
                    param("familyId", "The family ID from session attributes", required=True),
                    param("memberId", "The member ID from session attributes", required=True),
                    param("loincCode", "LOINC code for the test", required=False),
                    param("testName", "Human-readable test name", required=False),
                ],
            ),
            make_function(
                "compareMembers",
                "Compare a specific lab test value across multiple family members.",
                [
                    param("familyId", "The family ID from session attributes", required=True),
                    param("memberIds", "Comma-separated list of member IDs to compare", required=True),
                    param("loincCode", "LOINC code for the test", required=False),
                    param("testName", "Human-readable test name", required=False),
                ],
            ),
            make_function(
                "detectPatterns",
                "Detect abnormal and trending test values across all of a member's reports. Good for a full health summary.",
                [
                    param("familyId", "The family ID from session attributes", required=True),
                    param("memberId", "The member ID from session attributes", required=True),
                ],
            ),
        ],
    )
    time.sleep(3)

    # --- Step 4: Family Action Group ---
    print("\n[4/4] Registering chetana-action-family...")
    create_group(
        name="chetana-action-family",
        description="Retrieves the list of members in a family. Use when user asks about family members or their profile.",
        lambda_arn=LAMBDA_FAMILY,
        functions=[
            make_function(
                "getFamilyMembers",
                "Get all members belonging to a family.",
                [
                    param("familyId", "The family ID from session attributes", required=True),
                ],
            ),
        ],
    )
    time.sleep(3)

    # --- Prepare ---
    print("\n[5/5] Preparing agent with new action groups...")
    prepare_agent()

    print("\n✅ Done! All action groups registered and agent prepared.")
    print("   Next: Go to Bedrock Console → Aliases → update 'prod' alias to the new version,")
    print("   OR run: python register_action_groups.py --update-alias  (coming soon)")
    print()


if __name__ == "__main__":
    main()
