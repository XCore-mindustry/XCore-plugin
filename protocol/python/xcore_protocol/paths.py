from __future__ import annotations

from pathlib import Path


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def spec_root() -> Path:
    return repo_root() / "spec"


def fixtures_root() -> Path:
    return repo_root() / "fixtures"
