from __future__ import annotations

from pathlib import Path

import pytest

from xcore_protocol.generated import (
    MAPS_LIST_REQUEST_V1,
    MAPS_LOAD_COMMAND_V1,
    MAPS_ROUTES_BY_MESSAGE,
    MapEntryV1,
    MapFileSourceV1,
    MapsListRequestV1,
    MapsListResponseV1,
    MapsLoadCommandV1,
    MapsRemoveRequestV1,
    MapsRemoveResponseV1,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def test_generated_maps_request_roundtrip_matches_fixture() -> None:
    fixture_path = fixtures_root() / "valid" / "maps" / "maps.list.request.v1.json"
    payload = load_json(fixture_path)

    model = MapsListRequestV1.from_payload(payload)

    assert model == MapsListRequestV1(server="mini-pvp")
    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "maps" / "maps.list.request.v1.json", model.to_payload())


def test_generated_maps_response_roundtrip_matches_fixture() -> None:
    fixture_path = fixtures_root() / "valid" / "maps" / "maps.list.response.v1.json"
    payload = load_json(fixture_path)

    model = MapsListResponseV1.from_payload(payload)

    assert model.server == "mini-pvp"
    assert model.maps[0] == MapEntryV1(
        name="Ancient Caldera",
        fileName="ancient-caldera.msav",
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
    )
    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "maps" / "maps.list.response.v1.json", model.to_payload())


def test_generated_maps_load_command_roundtrip_matches_fixture() -> None:
    fixture_path = fixtures_root() / "valid" / "maps" / "maps.load.command.v1.json"
    payload = load_json(fixture_path)

    model = MapsLoadCommandV1.from_payload(payload)

    assert model == MapsLoadCommandV1(
        server="mini-pvp",
        files=(
            MapFileSourceV1(
                url="https://cdn.example.test/maps/ancient-caldera.msav",
                fileName="ancient-caldera.msav",
            ),
            MapFileSourceV1(
                url="https://cdn.example.test/maps/bridge-fight.MSAV",
                fileName="bridge-fight.MSAV",
            ),
        ),
    )
    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "maps" / "maps.load.command.v1.json", model.to_payload())


def test_generated_maps_remove_models_roundtrip_manually() -> None:
    request = MapsRemoveRequestV1(server="mini-pvp", fileName="ancient-caldera.msav")
    response = MapsRemoveResponseV1(
        server="mini-pvp",
        result="Successfully removed map Ancient Caldera (ancient-caldera.msav)",
    )

    assert request.to_payload() == {
        "messageType": "maps.remove.request",
        "messageVersion": 1,
        "server": "mini-pvp",
        "fileName": "ancient-caldera.msav",
    }
    assert response.to_payload() == {
        "messageType": "maps.remove.response",
        "messageVersion": 1,
        "server": "mini-pvp",
        "result": "Successfully removed map Ancient Caldera (ancient-caldera.msav)",
    }


def test_generated_maps_models_remain_strict() -> None:
    invalid_payload = {
        "messageType": "maps.list.request",
        "messageVersion": 1,
        "server_name": "mini-pvp",
    }

    try:
        MapsListRequestV1.from_payload(invalid_payload)
    except ValueError as error:
        assert "missing required fields" in str(error) or "unexpected fields" in str(error)
    else:
        raise AssertionError("Expected strict generated model parsing to reject non-canonical keys")


def test_generated_route_registry_matches_expected_messages() -> None:
    assert MAPS_LIST_REQUEST_V1.payloadType is MapsListRequestV1
    assert MAPS_LOAD_COMMAND_V1.payloadType is MapsLoadCommandV1
    assert MAPS_ROUTES_BY_MESSAGE[("maps.list.request", 1)].stream == "xcore:rpc:req:{server}"
    assert MAPS_ROUTES_BY_MESSAGE[("maps.remove.request", 1)].response is not None
    assert MAPS_ROUTES_BY_MESSAGE[("maps.list.request", 1)].bindings == {"server": "payload.server"}
    assert MAPS_ROUTES_BY_MESSAGE[("maps.remove.request", 1)].response.bindings == {"requester": "rpc.requester"}



def test_discord_submission_fixtures_roundtrip() -> None:
    for name, model in (("maps.load.command", MapsLoadCommandV1), ("maps.list.response", MapsListResponseV1)):
        payload = load_json(fixtures_root() / "valid" / "maps" / f"{name}.v1.discord-upload.json")
        assert model.from_payload(payload).to_payload() == payload


def test_upload_rpc_fixtures_and_constraints() -> None:
    import pytest
    from xcore_protocol.generated import MapsUploadRequestV1, MapsUploadResponseV1, MapDiscordUploadV1
    for name, model in (("request", MapsUploadRequestV1), ("response", MapsUploadResponseV1)):
        payload = load_json(fixtures_root() / "valid" / "maps" / f"maps.upload.{name}.v1.json")
        assert model.from_payload(payload).to_payload() == payload
    for payload in (
        {"uploadedBy": {"discordId": "1"}, "uploadedAt": -5},
        {"uploadedBy": {"discordId": ""}, "uploadedAt": 1},
        {"uploadedBy": {"discordId": "1", "discordUsername": ""}, "uploadedAt": 1},
    ):
        with pytest.raises(ValueError):
            MapDiscordUploadV1.from_payload(payload)


@pytest.mark.parametrize(
    "fixture_path",
    sorted((fixtures_root() / "valid" / "maps").glob("maps.inspect.*.json")),
    ids=lambda path: path.name,
)
def test_inspect_models_roundtrip(fixture_path: Path) -> None:
    from xcore_protocol.generated import MapsInspectRequestV1, MapsInspectResponseV1

    payload = load_json(fixture_path)
    model_type = MapsInspectRequestV1 if payload["messageType"].endswith("request") else MapsInspectResponseV1
    model = model_type.from_payload(payload)
    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages/maps" / f"{model.MESSAGE_TYPE}.v1.json", model.to_payload())


def test_inspect_models_keep_finding_codes_open_and_enums_strict() -> None:
    from xcore_protocol.generated import MapInspectFindingV1, MapInspectFindingV1Severity, MapsInspectResponseV1

    finding = MapInspectFindingV1(code="future-check-v2", severity=MapInspectFindingV1Severity.INFO)
    assert finding.code == "future-check-v2"
    assert finding.severity is MapInspectFindingV1Severity.INFO
    with pytest.raises(ValueError):
        MapInspectFindingV1(code="future_check", severity=MapInspectFindingV1Severity.INFO)
    with pytest.raises(ValueError):
        MapsInspectResponseV1.from_payload({"messageType": "maps.inspect.response", "messageVersion": 1, "server": "mini-pvp", "status": "pending", "findings": []})


def test_inspect_route_resolves_spec_streams() -> None:
    from xcore_protocol.generated import MAPS_INSPECT_REQUEST_V1, MapsInspectRequestV1, MapsInspectResponseV1, ROUTES_BY_MESSAGE

    route = MAPS_INSPECT_REQUEST_V1
    assert ROUTES_BY_MESSAGE[("maps.inspect.request", 1)] is route
    assert route.payloadType is MapsInspectRequestV1
    assert route.kind == "rpc-request"
    assert route.stream.format(server="mini-pvp") == "xcore:rpc:req:mini-pvp"
    assert route.bindings == {"server": "payload.server"}
    assert route.targetScope == "server"
    assert route.ttlMs == 60000 and not route.replayable
    assert route.owner == "maps"
    assert route.response.messageType == "maps.inspect.response"
    assert route.response.messageVersion == 1
    assert route.response.payloadType is MapsInspectResponseV1
    assert route.response.stream.format(requester="discord") == "xcore:rpc:resp:discord"
    assert route.response.bindings == {"requester": "rpc.requester"}
