"""
Bootstrap module for Action Group Lambdas.

SAM packages each CodeUri independently, so `lambdas.shared.*` is not on the
Python path by default. This module appends the `backend/` directory to
sys.path at Lambda cold-start, making the shared package importable exactly as
if the function were running from the repository root. Import this module ONCE
at the top of each action handler before any shared imports.

Usage:
    import _bootstrap  # noqa: F401 — side-effect import, must come first
    from lambdas.shared.repositories.dynamo_repository import DynamoRepository
"""
import sys
import os

# Resolve the `backend/` directory regardless of where Lambda extracts the zip.
# __file__ is .../action_groups/_bootstrap.py
# parent  = .../action_groups/
# parent² = .../agent_chat/
# parent³ = .../lambdas/
# parent⁴ = .../backend/    <-- what we need
_backend_dir = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "..")
)

if _backend_dir not in sys.path:
    sys.path.insert(0, _backend_dir)
