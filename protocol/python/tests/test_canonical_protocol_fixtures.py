from __future__ import annotations

import json
from collections.abc import Callable, Mapping
from pathlib import Path
from typing import Any

import pytest

from xcore_protocol.generated import (
    ActorRefV1ActorType,
    DiscordAdminAccessChangedCommandV1,
    DiscordUnlinkCommandV1,
    ServerHeartbeatV1,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_valid


REPO_ROOT = fixtures_root().parent
CANONICAL_FIXTURES_ROOT = REPO_ROOT / "spec" / "fixtures" / "valid"


def _load_fixture(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _model_dump_json(model: Any) -> dict[str, Any]:
    model_dump = getattr(model, "model_dump", None)
    if callable(model_dump):
        payload = model_dump(mode="json")
        if not isinstance(payload, dict):
            raise TypeError("model_dump(mode='json') must return a dict payload")
        return payload

    to_payload = getattr(model, "to_payload", None)
    if callable(to_payload):
        payload = to_payload()
        if not isinstance(payload, dict):
            raise TypeError("to_payload() must return a dict payload")
        return payload

    raise TypeError("Generated model must provide model_dump(mode='json') or to_payload()")


def _assert_no_snake_case_keys(value: Any) -> None:
    if isinstance(value, Mapping):
        for key, nested_value in value.items():
            assert "_" not in key, f"Found legacy snake_case key: {key}"
            _assert_no_snake_case_keys(nested_value)
        return

    if isinstance(value, list):
        for item in value:
            _assert_no_snake_case_keys(item)


CanonicalAssertion = Callable[[Any, dict[str, Any]], None]


def _assert_admin_access_semantics(model: DiscordAdminAccessChangedCommandV1, payload: dict[str, Any]) -> None:
    assert model.source.actorType is ActorRefV1ActorType.SYSTEM
    assert payload["source"]["actorType"] == "system"
    assert model.actor.actorType is ActorRefV1ActorType.DISCORD
    assert model.actor.actorName == payload["actor"]["actorName"]


def _assert_unlink_semantics(model: DiscordUnlinkCommandV1, payload: dict[str, Any]) -> None:
    assert model.actor.actorType is ActorRefV1ActorType.DISCORD
    assert model.actor.actorName == payload["actor"]["actorName"]
    assert payload["actor"]["actorName"] == "Beta Display"


def _assert_heartbeat_semantics(model: ServerHeartbeatV1, payload: dict[str, Any]) -> None:
    assert model.serverName == payload["serverName"]
    assert model.discordChannelId == payload["discordChannelId"]


CANONICAL_CASES: list[tuple[str, Path, Path, type[Any], CanonicalAssertion]] = [
    (
        "discord_admin_access_grant",
        spec_root() / "messages" / "discord" / "discord.admin-access.changed.command.v1.json",
        CANONICAL_FIXTURES_ROOT / "discord" / "discord.admin-access.changed.command.v1.grant.json",
        DiscordAdminAccessChangedCommandV1,
        _assert_admin_access_semantics,
    ),
    (
        "discord_admin_access_revoke",
        spec_root() / "messages" / "discord" / "discord.admin-access.changed.command.v1.json",
        CANONICAL_FIXTURES_ROOT / "discord" / "discord.admin-access.changed.command.v1.revoke.json",
        DiscordAdminAccessChangedCommandV1,
        _assert_admin_access_semantics,
    ),
    (
        "discord_unlink",
        spec_root() / "messages" / "discord" / "discord.unlink.command.v1.json",
        CANONICAL_FIXTURES_ROOT / "discord" / "discord.unlink.command.v1.json",
        DiscordUnlinkCommandV1,
        _assert_unlink_semantics,
    ),
    (
        "server_heartbeat",
        spec_root() / "messages" / "chat" / "server.heartbeat.v1.json",
        CANONICAL_FIXTURES_ROOT / "chat" / "server.heartbeat.v1.json",
        ServerHeartbeatV1,
        _assert_heartbeat_semantics,
    ),
]


def test_canonical_fixture_inventory_exists() -> None:
    assert fixtures_root().exists()

    for _, schema_path, fixture_path, _, _ in CANONICAL_CASES:
        assert schema_path.exists()
        assert fixture_path.exists()


@pytest.mark.parametrize(
    ("_case_name", "schema_path", "fixture_path", "model_type", "assert_semantics"),
    CANONICAL_CASES,
)
def test_canonical_fixtures_remain_schema_valid(
    _case_name: str,
    schema_path: Path,
    fixture_path: Path,
    model_type: type[Any],
    assert_semantics: CanonicalAssertion,
) -> None:
    del model_type, assert_semantics
    assert_valid(schema_path, fixture_path)


@pytest.mark.parametrize(
    ("_case_name", "schema_path", "fixture_path", "model_type", "assert_semantics"),
    CANONICAL_CASES,
)
def test_canonical_generated_models_roundtrip(
    _case_name: str,
    schema_path: Path,
    fixture_path: Path,
    model_type: type[Any],
    assert_semantics: CanonicalAssertion,
) -> None:
    del schema_path
    payload = _load_fixture(fixture_path)

    model = model_type.from_payload(payload)
    roundtrip_payload = _model_dump_json(model)

    assert roundtrip_payload == payload
    assert_semantics(model, payload)


def test_canonical_fixtures_do_not_use_legacy_snake_case_keys() -> None:
    for _, _, fixture_path, _, _ in CANONICAL_CASES:
        _assert_no_snake_case_keys(_load_fixture(fixture_path))
