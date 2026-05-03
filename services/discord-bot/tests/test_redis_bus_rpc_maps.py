from __future__ import annotations

import json
from types import MethodType, SimpleNamespace

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
    ):  # noqa: ANN001
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
    ):  # noqa: ANN001
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
