"""
Bootstrap module for insights_engine Lambda.
Appends backend/ to sys.path so that `lambdas.shared.*` is importable.
"""
import sys
import os

# __file__ is .../insights_engine/_bootstrap.py
# parent  = .../insights_engine/
# parent² = .../lambdas/
# parent³ = .../backend/   <-- what we need
_backend_dir = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..")
)
if _backend_dir not in sys.path:
    sys.path.insert(0, _backend_dir)
