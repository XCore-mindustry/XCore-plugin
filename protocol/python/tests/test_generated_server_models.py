from __future__ import annotations

from xcore_protocol.generated import (
    PLAYER_DATA_CACHE_RELOAD_COMMAND_V1,
    SERVER_ACTION_V1,
    SERVER_COMMAND_EXECUTE_COMMAND_V1,
    SERVER_HEARTBEAT_V1,
    PlayerDataCacheReloadCommandV1,
    ServerActionV1,
    ServerCommandExecuteCommandV1,
    ServerHeartbeatV1,
    ROUTES_BY_MESSAGE,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def test_generated_server_action_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "server" / "server.action.v1.json")

    model = ServerActionV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "server" / "server.action.v1.json", model.to_payload())


def test_generated_server_heartbeat_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "server" / "server.heartbeat.v1.json")

    model = ServerHeartbeatV1.from_payload(payload)

    assert model == ServerHeartbeatV1(
        serverName="mini-pvp",
        discordChannelId=1234567890123456789,
        players=4,
        maxPlayers=12,
        version="146.1",
        host="play.example.com",
        port=6567,
    )
    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "server" / "server.heartbeat.v1.json", model.to_payload())


def test_generated_server_heartbeat_rejects_alias_fields() -> None:
    invalid_payload = load_json(
        fixtures_root() / "invalid" / "server" / "server.heartbeat.v1.server-host-alias.json"
    )

    try:
        ServerHeartbeatV1.from_payload(invalid_payload)
    except ValueError as error:
        assert "unexpected fields" in str(error)
    else:
        raise AssertionError("Expected strict generated heartbeat parsing to reject alias fields")


def test_generated_route_registry_includes_server_messages() -> None:
    assert SERVER_ACTION_V1.payloadType is ServerActionV1
    assert SERVER_HEARTBEAT_V1.payloadType is ServerHeartbeatV1
    assert SERVER_COMMAND_EXECUTE_COMMAND_V1.payloadType is ServerCommandExecuteCommandV1
    assert PLAYER_DATA_CACHE_RELOAD_COMMAND_V1.payloadType is PlayerDataCacheReloadCommandV1
    assert ROUTES_BY_MESSAGE[("server.action", 1)].stream == "xcore:evt:server:action"
    assert ROUTES_BY_MESSAGE[("server.heartbeat", 1)].stream == "xcore:evt:server:heartbeat"
    assert ROUTES_BY_MESSAGE[("server-command.execute.command", 1)].stream == "xcore:cmd:execute-command:broadcast"
    assert ROUTES_BY_MESSAGE[("player-data-cache.reload.command", 1)].stream == "xcore:cmd:reload-cache:{server}"
