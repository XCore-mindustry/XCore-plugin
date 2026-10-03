from __future__ import annotations

from xcore_protocol.generated import (
    ActorRefV1ActorType,
    RATING_ACCOUNTS_MERGE_REQUEST_V1,
    RATING_SEASON_ENDED_V1,
    RATING_SEASON_ENDING_SOON_V1,
    RATING_SEASON_RESCHEDULE_REQUEST_V1,
    RATING_SEASON_RESCHEDULED_V1,
    RATING_SEASON_STARTED_V1,
    RatingAccountsMergeRequestV1,
    RatingAccountsMergeResponseV1,
    RatingSeasonEndedV1,
    RatingSeasonEndingSoonV1,
    RatingSeasonRescheduleRequestV1,
    RatingSeasonRescheduleRequestV1Operation,
    RatingSeasonRescheduleResponseV1,
    RatingSeasonRescheduledV1,
    RatingSeasonStartedV1,
    ROUTES_BY_MESSAGE,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def _fixture(name: str) -> dict:
    return load_json(fixtures_root() / "valid" / "rating" / f"{name}.v1.json")


def _assert_roundtrip(model_type, name: str):
    payload = _fixture(name)
    model = model_type.from_payload(payload)
    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "rating" / f"{name}.v1.json", model.to_payload())
    return model


def test_generated_season_started_roundtrip() -> None:
    model = _assert_roundtrip(RatingSeasonStartedV1, "rating.season.started")

    assert model.season.season == 4
    assert model.previousSeason == 3


def test_generated_season_ending_soon_roundtrip() -> None:
    model = _assert_roundtrip(RatingSeasonEndingSoonV1, "rating.season.ending-soon")

    assert model.notice == "7d"


def test_generated_season_ended_roundtrip_keeps_podium_order_and_optional_discord() -> None:
    model = _assert_roundtrip(RatingSeasonEndedV1, "rating.season.ended")

    assert [entry.place for entry in model.podium] == [1, 2]
    assert model.podium[0].discord is not None
    assert model.podium[0].discord.discordId == "111111111111111111"
    assert model.podium[1].discord is None
    assert model.summary.participants == 312


def test_generated_season_rescheduled_roundtrip() -> None:
    model = _assert_roundtrip(RatingSeasonRescheduledV1, "rating.season.rescheduled")

    assert model.actor.actorType is ActorRefV1ActorType.DISCORD
    assert model.reason == "Tournament week"


def test_generated_reschedule_rpc_roundtrip() -> None:
    request = _assert_roundtrip(RatingSeasonRescheduleRequestV1, "rating.season.reschedule.request")
    response = _assert_roundtrip(RatingSeasonRescheduleResponseV1, "rating.season.reschedule.response")

    assert request.operation is RatingSeasonRescheduleRequestV1Operation.EXTEND
    assert request.extendSeconds == 1209600
    assert request.endsAt is None
    assert response.ended is False


def test_generated_accounts_merge_rpc_roundtrip() -> None:
    request = _assert_roundtrip(RatingAccountsMergeRequestV1, "rating.accounts.merge.request")
    response = _assert_roundtrip(RatingAccountsMergeResponseV1, "rating.accounts.merge.response")

    assert (request.sourceUuid, request.targetUuid) == ("uuid-old", "uuid-new")
    assert response.standingsMerged == 3


def test_rating_routes_are_registered() -> None:
    events = [
        (RATING_SEASON_STARTED_V1, "rating.season.started", RatingSeasonStartedV1, "xcore:evt:rating:season-started"),
        (
            RATING_SEASON_ENDING_SOON_V1,
            "rating.season.ending-soon",
            RatingSeasonEndingSoonV1,
            "xcore:evt:rating:season-ending-soon",
        ),
        (RATING_SEASON_ENDED_V1, "rating.season.ended", RatingSeasonEndedV1, "xcore:evt:rating:season-ended"),
        (
            RATING_SEASON_RESCHEDULED_V1,
            "rating.season.rescheduled",
            RatingSeasonRescheduledV1,
            "xcore:evt:rating:season-rescheduled",
        ),
    ]
    for route, message_type, payload_type, stream in events:
        assert ROUTES_BY_MESSAGE[(message_type, 1)] is route
        assert route.payloadType is payload_type
        assert route.kind == "event"
        assert route.stream == stream
        assert route.replayable is True

    rpc = [
        (
            RATING_SEASON_RESCHEDULE_REQUEST_V1,
            "rating.season.reschedule.request",
            RatingSeasonRescheduleRequestV1,
            RatingSeasonRescheduleResponseV1,
        ),
        (
            RATING_ACCOUNTS_MERGE_REQUEST_V1,
            "rating.accounts.merge.request",
            RatingAccountsMergeRequestV1,
            RatingAccountsMergeResponseV1,
        ),
    ]
    for route, message_type, request_type, response_type in rpc:
        assert ROUTES_BY_MESSAGE[(message_type, 1)] is route
        assert route.payloadType is request_type
        assert route.kind == "rpc-request"
        assert route.stream == "xcore:rpc:req:{server}"
        assert route.bindings == {"server": "payload.server"}
        assert route.response is not None
        assert route.response.payloadType is response_type
