from __future__ import annotations

import inspect
from pathlib import Path

import pytest

import xcore_protocol.generated as generated
from xcore_protocol.generated import (
    MetricsSnapshotV1,
    ROUTES_BY_MESSAGE,
)
from xcore_protocol.paths import fixtures_root
from xcore_protocol.schema_validation import load_json


def _all_valid_fixture_paths() -> tuple[Path, ...]:
    return tuple(sorted((fixtures_root() / "valid").glob("**/*.json")))


def _fixture_id(path: Path) -> str:
    return path.relative_to(fixtures_root()).as_posix()


@pytest.mark.parametrize("fixture_path", _all_valid_fixture_paths(), ids=_fixture_id)
def test_valid_fixture_remains_canonical_golden_payload(fixture_path: Path) -> None:
    payload = load_json(fixture_path)

    model = _model_from_fixture_payload(payload)

    assert model.to_payload() == payload


def _model_from_fixture_payload(payload: dict[str, object]) -> object:
    schema_version = payload.get("schemaVersion")
    if schema_version is not None:
        if schema_version != MetricsSnapshotV1.SCHEMA_VERSION:
            raise AssertionError(f"Unsupported canonical schema fixture: {schema_version}")
        return MetricsSnapshotV1.from_payload(payload)

    message_type = payload.get("messageType")
    message_version = payload.get("messageVersion")
    if not isinstance(message_type, str) or not isinstance(message_version, int):
        raise AssertionError("Canonical fixture is missing message identity")

    route = ROUTES_BY_MESSAGE.get((message_type, message_version))
    if route is not None:
        return route.payloadType.from_payload(payload)

    model_type = _message_models_by_identity().get((message_type, message_version))
    if model_type is None:
        raise AssertionError(f"No generated message model for {message_type}@v{message_version}")
    return model_type.from_payload(payload)


def _message_models_by_identity() -> dict[tuple[str, int], type[object]]:
    models: dict[tuple[str, int], type[object]] = {}
    for candidate in vars(generated).values():
        if not inspect.isclass(candidate):
            continue
        message_type = getattr(candidate, "MESSAGE_TYPE", None)
        message_version = getattr(candidate, "MESSAGE_VERSION", None)
        if not isinstance(message_type, str) or not isinstance(message_version, int):
            continue
        if not callable(getattr(candidate, "from_payload", None)):
            continue
        models[(message_type, message_version)] = candidate
    return models
