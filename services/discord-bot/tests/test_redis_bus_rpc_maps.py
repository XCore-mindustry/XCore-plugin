from __future__ import annotations

import json
from types import MethodType, SimpleNamespace
from typing import Any

import pytest
from xcore_protocol.generated.maps import (
    MapsListRequestV1,
    MapsListResponseV1,
    MapsRemoveRequestV1,
    MapsRemoveResponseV1,
)
from xcore_protocol.generated.shared import MapEntryV1

from xcore_discord_bot.redis_bus import RedisBus


@pytest.mark.asyncio
async def test_publish_maps_load_uses_expected_event_metadata() -> None:
    settings = SimpleNamespace(
        redis_url="redis://127.0.0.1:6379",
        redis_group_prefix="xcore:cg",
        redis_consumer_name="discord-bot",
    )
    bus = RedisBus(settings)
    captured: dict[str, Any] = {}

    async def fake_publish_event(self, **kwargs):
        captured.update(kwargs)

    bus._publish_event = MethodType(fake_publish_event, bus)

    files = [{"url": "https://example/maps/one.msav", "filename": "one.msav"}]
    await bus.publish_maps_load(server="mini-pvp", files=files)

    assert captured["stream"] == "xcore:cmd:maps-load:mini-pvp"
    assert captured["event_type"] == "maps.load.command"
    assert captured["ttl_ms"] == 300_000
    assert captured["server"] == "mini-pvp"
    assert captured["idempotency_prefix"] == "maps.load"
    payload = captured["payload"]
    assert payload["messageType"] == "maps.load.command"
    assert payload["messageVersion"] == 1
    assert payload["server"] == "mini-pvp"
    assert payload["files"] == [
        {"url": "https://example/maps/one.msav", "fileName": "one.msav"}
    ]


@pytest.mark.asyncio
async def test_rpc_maps_list_parses_canonical_response() -> None:
    settings = SimpleNamespace(
        redis_url="redis://127.0.0.1:6379",
        redis_group_prefix="xcore:cg",
        redis_consumer_name="discord-bot",
    )
    bus = RedisBus(settings)

    response_payload = MapsListResponseV1(
        server="mini-pvp",
        maps=(
            MapEntryV1(
                name="Map A",
                fileName="a.msav",
                author="Alice",
                width=120,
                height=80,
                fileSizeBytes=2048,
                like=5,
                dislike=2,
                reputation=3,
                popularity=7.5,
                interest=1.5,
                gameMode="pvp",
            ),
            MapEntryV1(name="Map B", fileName="b.msav", author="Bob"),
        ),
    ).to_payload()

    async def fake_rpc_request(
        self, server: str, rpc_type: str, payload: dict, timeout_ms: int
    ):
        assert server == "mini-pvp"
        assert rpc_type == MapsListRequestV1.MESSAGE_TYPE
        assert payload["messageType"] == MapsListRequestV1.MESSAGE_TYPE
        assert payload["messageVersion"] == 1
        assert payload["server"] == "mini-pvp"
        return {"payload_json": json.dumps(response_payload)}

    bus._rpc_request = MethodType(fake_rpc_request, bus)

    result = await bus.rpc_maps_list("mini-pvp", 5000)

    assert result == [
        {
            "name": "Map A",
            "file_name": "a.msav",
            "author": "Alice",
            "width": "120",
            "height": "80",
            "file_size_bytes": "2048",
            "like": "5",
            "dislike": "2",
            "reputation": "3",
            "popularity": "7.5",
            "interest": "1.5",
            "game_mode": "pvp",
        },
        {
            "name": "Map B",
            "file_name": "b.msav",
            "author": "Bob",
            "width": "",
            "height": "",
            "file_size_bytes": "",
            "like": "",
            "dislike": "",
            "reputation": "",
            "popularity": "",
            "interest": "",
            "game_mode": "",
        },
    ]


@pytest.mark.asyncio
async def test_rpc_remove_map_uses_canonical_payload() -> None:
    settings = SimpleNamespace(
        redis_url="redis://127.0.0.1:6379",
        redis_group_prefix="xcore:cg",
        redis_consumer_name="discord-bot",
    )
    bus = RedisBus(settings)

    async def fake_rpc_request(
        self, server: str, rpc_type: str, payload: dict, timeout_ms: int
    ):
        assert server == "mini-pvp"
        assert rpc_type == MapsRemoveRequestV1.MESSAGE_TYPE
        assert payload["messageType"] == MapsRemoveRequestV1.MESSAGE_TYPE
        assert payload["messageVersion"] == 1
        assert payload["fileName"] == "map-a.msav"

        response = MapsRemoveResponseV1(server="mini-pvp", result="ok")
        return {"payload_json": json.dumps(response.to_payload())}

    bus._rpc_request = MethodType(fake_rpc_request, bus)

    result = await bus.rpc_remove_map("mini-pvp", "map-a.msav", 5000)
    assert result == "ok"


@pytest.mark.asyncio
async def test_upload_metadata_roundtrips_through_outbound_and_list_normalization() -> (
    None
):
    from xcore_discord_bot.protocol_outbound import build_maps_load_command

    upload = {
        "uploadedBy": {
            "discordId": "123456789012345678",
            "discordUsername": "Reviewer",
        },
        "uploadedAt": 1791806400000,
        "creator": {"discordId": "234567890123456789", "discordUsername": "Creator"},
    }
    command = build_maps_load_command(
        "mini-pvp",
        [
            {
                "url": "https://example/arena.msav",
                "filename": "arena.msav",
                "discordUpload": upload,
            }
        ],
    )
    assert command.to_payload()["files"][0]["discordUpload"] == upload
    entry = MapEntryV1.from_payload(
        {
            "name": "Arena",
            "fileName": "arena.msav",
            "author": "File author",
            "discordUpload": upload,
        }
    )
    result = RedisBus._normalize_map_entry_v1(entry)
    assert result["uploaded_by_discord_id"] == upload["uploadedBy"]["discordId"]
    assert result["creator_name"] == "Creator"
    assert result["uploaded_at"] == str(upload["uploadedAt"])


@pytest.mark.asyncio
async def test_upload_rpc_uses_canonical_request_and_matches_result_files() -> None:
    from unittest.mock import AsyncMock

    settings = SimpleNamespace(
        redis_url="redis://127.0.0.1:6379",
        redis_group_prefix="xcore:cg",
        redis_consumer_name="discord-bot",
    )
    bus = RedisBus(settings)
    response = {
        "messageType": "maps.upload.response",
        "messageVersion": 1,
        "server": "mini-pvp",
        "results": [{"fileName": "arena.msav", "status": "installed"}],
    }
    bus._rpc_request = AsyncMock(return_value={"payload_json": json.dumps(response)})
    files = [
        {
            "filename": "arena.msav",
            "url": "https://example/arena",
            "discordUpload": {
                "uploadedBy": {"discordId": "123"},
                "uploadedAt": 1,
                "uploadedByHandle": "reviewer",
            },
        }
    ]
    result = await bus.rpc_upload_maps(
        server="mini-pvp", files=files, timeout_ms=120000
    )
    assert result.results[0].status.value == "installed"
    payload = bus._rpc_request.call_args.kwargs["payload"]
    assert payload["messageType"] == "maps.upload.request"
    assert payload["files"][0]["discordUpload"]["uploadedByHandle"] == "reviewer"
    response["results"][0]["fileName"] = "other.msav"
    bus._rpc_request.return_value = {"payload_json": json.dumps(response)}
    with pytest.raises(ValueError, match="does not match"):
        await bus.rpc_upload_maps(server="mini-pvp", files=files, timeout_ms=120000)


@pytest.mark.asyncio
async def test_upload_response_cannot_add_or_omit_requested_files() -> None:
    from unittest.mock import AsyncMock

    settings = SimpleNamespace(
        redis_url="redis://127.0.0.1:6379",
        redis_group_prefix="xcore:cg",
        redis_consumer_name="discord-bot",
    )
    bus = RedisBus(settings)
    response = {
        "messageType": "maps.upload.response",
        "messageVersion": 1,
        "server": "mini-pvp",
        "results": [{"fileName": "arena.msav", "status": "installed"}],
    }
    bus._rpc_request = AsyncMock(return_value={"payload_json": json.dumps(response)})
    files = [
        {"filename": "arena.msav", "url": "https://example/arena"},
        {"filename": "second.msav", "url": "https://example/second"},
    ]
    with pytest.raises(ValueError, match="does not match"):
        await bus.rpc_upload_maps(server="mini-pvp", files=files, timeout_ms=120000)
    response["results"].append({"fileName": "arena.msav", "status": "installed"})
    bus._rpc_request.return_value = {"payload_json": json.dumps(response)}
    with pytest.raises(ValueError, match="does not match"):
        await bus.rpc_upload_maps(server="mini-pvp", files=files[:1], timeout_ms=120000)
