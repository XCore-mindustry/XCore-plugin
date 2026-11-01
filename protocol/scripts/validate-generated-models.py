from __future__ import annotations

import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from generators.families import generated_model_test_paths

JAVA_PYTHON_COMPATIBILITY_TESTS: tuple[str, ...] = (
    "python/tests/test_java_python_route_compatibility.py",
    "python/tests/test_java_python_serialized_compatibility.py",
)


def run_step(repo_root: Path, *args: str) -> None:
    subprocess.run(args, cwd=repo_root, check=True)


def main() -> int:
    repo_root = REPO_ROOT
    for test_path in generated_model_test_paths():
        run_step(repo_root, "uv", "run", "pytest", test_path)
    for test_path in JAVA_PYTHON_COMPATIBILITY_TESTS:
        run_step(repo_root, "uv", "run", "pytest", test_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
