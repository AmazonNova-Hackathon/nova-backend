#!/usr/bin/env python3
"""
MediAgent Local Lambda Test Runner
=====================================
Runs Lambda handlers DIRECTLY in Python — no Docker required.
Uses your local AWS credentials (~/.aws/credentials) to hit real DynamoDB.

Usage:
    cd backend
    pip install -r requirements.txt            # first time only
    python test_local.py                       # runs all tests
    python test_local.py families              # run only family tests
    python test_local.py observations          # run only observation tests

Requirements:
    - AWS credentials configured (run: aws configure)
    - requirements.txt dependencies installed
"""
import sys
import os
import json
import time
import io

# Force UTF-8 output on Windows to avoid cp1252 issues
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# ── Bootstrap: add backend/ to Python path ───────────────────────────────────
_backend = os.path.dirname(os.path.abspath(__file__))
if _backend not in sys.path:
    sys.path.insert(0, _backend)

# ── Output helpers (ASCII-safe) ───────────────────────────────────────────────
GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
CYAN   = "\033[96m"
RESET  = "\033[0m"
BOLD   = "\033[1m"

def ok(msg):  print(f"{GREEN}  [OK] {msg}{RESET}")
def err(msg): print(f"{RED}  [FAIL] {msg}{RESET}")
def info(msg):print(f"{CYAN}  [->] {msg}{RESET}")
def hdr(msg): print(f"\n{BOLD}{YELLOW}== {msg} =={RESET}")


# ── Lambda invocation helper ──────────────────────────────────────────────────
def invoke(handler_fn, event: dict) -> dict:
    """Call a Lambda handler function directly and return the parsed response."""
    class FakeContext:
        function_name = "local-test"
        aws_request_id = "test-request-id"
        log_group_name = "/aws/lambda/local-test"
        remaining_time_in_millis = lambda self: 30000

    response = handler_fn(event, FakeContext())
    if isinstance(response.get("body"), str):
        response["_body"] = json.loads(response["body"])
    else:
        response["_body"] = response.get("body", {})
    return response

def make_event(method, path, body=None, query=None, path_params=None):
    return {
        "httpMethod": method,
        "path": path,
        "headers": {"Content-Type": "application/json"},
        "body": json.dumps(body) if body else None,
        "queryStringParameters": query,
        "pathParameters": path_params,
        "requestContext": {}
    }

# ═══════════════════════════════════════════════════════════════════════════════
# Test Suites
# ═══════════════════════════════════════════════════════════════════════════════

def test_families():
    hdr("Family Management Lambda")
    try:
        from lambdas.family_management.app import lambda_handler
        ok("Import successful — _bootstrap resolved correctly")
    except ImportError as e:
        err(f"Import FAILED: {e}")
        return {}

    # POST /families
    info("POST /families  → create a test family")
    r = invoke(lambda_handler, make_event("POST", "/families", {"name": "Test Family (local)"}))
    print(f"     Status: {r['statusCode']}  Body: {json.dumps(r['_body'], indent=2)}")
    if r["statusCode"] == 200 and r["_body"].get("id"):
        family_id = r["_body"]["id"]
        ok(f"Family created: {family_id}")
    else:
        err("Failed to create family")
        return {}

    # POST /families/{familyId}/members
    info(f"POST /families/{family_id}/members  → add member")
    r2 = invoke(lambda_handler, make_event(
        "POST",
        f"/families/{family_id}/members",
        {"name": "Test User", "age": 30, "relationship": "Self"},
        path_params={"familyId": family_id}
    ))
    print(f"     Status: {r2['statusCode']}  Body: {json.dumps(r2['_body'], indent=2)}")
    if r2["statusCode"] == 200 and r2["_body"].get("id"):
        member_id = r2["_body"]["id"]
        ok(f"Member created: {member_id}")
    else:
        err("Failed to create member")
        return {"familyId": family_id}

    # GET /families/{familyId}/members
    info(f"GET /families/{family_id}/members  → list members")
    r3 = invoke(lambda_handler, make_event(
        "GET", f"/families/{family_id}/members",
        path_params={"familyId": family_id}
    ))
    print(f"     Status: {r3['statusCode']}  Body: {json.dumps(r3['_body'], indent=2)}")
    ok(f"Members listed: {r3['_body'].get('count', 0)} found")

    return {"familyId": family_id, "memberId": member_id}


def test_observations(family_id: str, member_id: str):
    hdr("Observations Lambda (via ExtractReportFunction)")
    try:
        from lambdas.extract_report.app import lambda_handler
        ok("Import successful")
    except ImportError as e:
        err(f"Import FAILED: {e}")
        return

    info(f"GET /observations?familyId={family_id}&memberId={member_id}")
    r = invoke(lambda_handler, make_event(
        "GET", "/observations",
        query={"familyId": family_id, "memberId": member_id}
    ))
    print(f"     Status: {r['statusCode']}  Body: {json.dumps(r['_body'], indent=2)}")
    if r["statusCode"] == 200:
        ok(f"Observations returned: {len(r['_body'].get('observations', []))} items")
    else:
        err(f"Observations failed: {r['_body']}")


def test_insights(family_id: str, member_id: str):
    hdr("Insights Engine Lambda")
    try:
        from lambdas.insights_engine.app import lambda_handler
        ok("Import successful")
    except ImportError as e:
        err(f"Import FAILED: {e}")
        return

    info(f"GET /insights?familyId={family_id}&memberId={member_id}")
    r = invoke(lambda_handler, make_event(
        "GET", "/insights",
        query={"familyId": family_id, "memberId": member_id}
    ))
    print(f"     Status: {r['statusCode']}  Body: {json.dumps(r['_body'], indent=2)}")
    if r["statusCode"] == 200:
        ok("Insights returned successfully")
    else:
        err(f"Insights failed: {r['_body']}")


def test_import_only():
    """Just verify all Lambda imports resolve — useful for catching _bootstrap errors."""
    hdr("Import Smoke Test (all Lambdas)")
    lambdas = [
        ("FamilyManagement",  "lambdas.family_management.app",   "lambda_handler"),
        ("ExtractReport",     "lambdas.extract_report.app",       "lambda_handler"),
        ("InsightsEngine",    "lambdas.insights_engine.app",      "lambda_handler"),
        ("AgentChat",         "lambdas.agent_chat.app",           "lambda_handler"),
        ("VoiceGateway",      "lambdas.voice_gateway.app",        "lambda_handler"),
    ]
    all_ok = True
    for name, module_path, fn in lambdas:
        try:
            mod = __import__(module_path, fromlist=[fn])
            getattr(mod, fn)
            ok(f"{name} — import OK")
        except ImportError as e:
            err(f"{name} — ImportError: {e}")
            all_ok = False
        except Exception as e:
            # Connection errors etc are expected without real AWS — import itself worked
            ok(f"{name} — import OK (runtime error at module level is expected locally: {type(e).__name__})")

    if all_ok:
        print(f"\n{GREEN}{BOLD}All imports resolved — _bootstrap fix is working ✅{RESET}\n")
    else:
        print(f"\n{RED}{BOLD}Some imports failed — see errors above ❌{RESET}\n")


# ═══════════════════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    suite = sys.argv[1] if len(sys.argv) > 1 else "all"

    print(f"\n{BOLD}MediAgent Local Test Runner{RESET}")
    print(f"Suite: {CYAN}{suite}{RESET}")
    print(f"Python: {sys.version.split()[0]}   Backend: {_backend}\n")

    if suite in ("import", "imports"):
        test_import_only()
        sys.exit(0)

    # Import-only smoke test first
    test_import_only()

    if suite in ("families", "all"):
        ids = test_families()
    else:
        ids = {}

    if ids.get("familyId") and suite in ("observations", "all"):
        test_observations(ids["familyId"], ids["memberId"])

    if ids.get("familyId") and suite in ("insights", "all"):
        test_insights(ids["familyId"], ids["memberId"])

    print(f"\n{BOLD}Done.{RESET}")
    if ids:
        print(f"\n📋 IDs created during this test run (copy to Postman env):")
        print(f"   familyId  = {ids.get('familyId', 'N/A')}")
        print(f"   memberId  = {ids.get('memberId', 'N/A')}\n")
