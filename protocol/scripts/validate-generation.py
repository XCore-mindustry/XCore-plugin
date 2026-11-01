from __future__ import annotations

import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from generators.families import entrypoint_family_names


def run_step(repo_root: Path, *args: str) -> None:
    subprocess.run(args, cwd=repo_root, check=True)


def main() -> int:
    repo_root = REPO_ROOT
    for family_name in entrypoint_family_names():
        run_step(repo_root, "uv", "run", "python", "-m", "generators", "generate", "--family", family_name)
        run_step(repo_root, "uv", "run", "python", "-m", "generators", "check", "--family", family_name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
