"""
Root-level bootstrap — must live at backend/_bootstrap.py.

When SAM deploys with CodeUri: ./ and handler lambdas.X.app.lambda_handler,
Lambda sets /var/task/ as the working root (== backend/).
Any `import _bootstrap` inside a Lambda subdirectory fails because the
_bootstrap.py inside that subdirectory is NOT in sys.path.

By placing this file at the backend/ root, `import _bootstrap` resolves
correctly from /var/task/_bootstrap.py, which IS in sys.path.

The per-lambda _bootstrap.py files in lambdas/<name>/ can be removed but
are harmless — they will never be reached because this file is found first.
"""
import sys
import os

_backend_root = os.path.dirname(os.path.abspath(__file__))
if _backend_root not in sys.path:
    sys.path.insert(0, _backend_root)
