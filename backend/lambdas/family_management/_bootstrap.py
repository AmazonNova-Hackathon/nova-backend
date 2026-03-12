"""
Bootstrap module for family_management Lambda.
Appends backend/ to sys.path so lambdas.shared.* is importable.
"""
import sys
import os

_backend_dir = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..")
)
if _backend_dir not in sys.path:
    sys.path.insert(0, _backend_dir)
