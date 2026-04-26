from __future__ import annotations

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
                filename="ancient-caldera.msav",
            ),
            MapFileSourceV1(
                url="https://cdn.example.test/maps/bridge-fight.MSAV",
                filename="bridge-fight.MSAV",
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
