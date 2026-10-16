from __future__ import annotations

from pathlib import Path

import pytest

from xcore_protocol.generated import (
    CASES_EVIDENCE_REQUEST_V1,
    CASES_NOTICE_COMMAND_V1,
    CASES_REPORT_SUBMITTED_V1,
    ROUTES_BY_MESSAGE,
    CaseChatLineV1,
    CaseChatLineV1Role,
    CasesEvidenceRequestV1,
    CasesEvidenceResponseV1,
    CasesNoticeCommandV1,
    CasesReportSubmittedV1,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


MODELS = {
    model.MESSAGE_TYPE: model
    for model in (
        CasesReportSubmittedV1,
        CasesNoticeCommandV1,
        CasesEvidenceRequestV1,
        CasesEvidenceResponseV1,
    )
}


@pytest.mark.parametrize(
    "fixture_path",
    sorted((fixtures_root() / "valid" / "cases").glob("*.json")),
    ids=lambda path: path.name,
)
def test_generated_cases_roundtrip_matches_fixture(fixture_path: Path) -> None:
    payload = load_json(fixture_path)
    model = MODELS[payload["messageType"]].from_payload(payload)
    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "cases" / f"{model.MESSAGE_TYPE}.v1.json",
        model.to_payload(),
    )


def test_report_uses_typed_evidence_and_open_reason() -> None:
    payload = load_json(fixtures_root() / "valid/cases/cases.report.submitted.v1.json")
    report = CasesReportSubmittedV1.from_payload(payload)
    assert report.chat[0].sentAt == 1791892800000
    assert report.chat[0].role is CaseChatLineV1Role.REPORTER
    assert report.blockSummary.topBlocks[0].block == "copper-wall"
    assert report.blockSummary.area.x2 == 20
    assert report.reporter.ip is None
    assert report.target.ip is None
    payload["reason"] = "future-reason"
    assert CasesReportSubmittedV1.from_payload(payload).reason == "future-reason"


def test_generated_cases_models_remain_strict() -> None:
    with pytest.raises(ValueError, match="unexpected fields"):
        CasesNoticeCommandV1.from_payload({
            "messageType": "cases.notice.command", "messageVersion": 1,
            "server": "mini-pvp", "noticeId": "42", "playerUuid": "uuid", "text": "Hello",
        })
    with pytest.raises(ValueError):
        CasesEvidenceRequestV1(server="mini-pvp", playerUuid="uuid", chatLines=0)
    with pytest.raises(ValueError):
        CaseChatLineV1.from_payload({"sentAt": 1, "playerName": "Alice", "role": "unknown", "text": "Hello"})


def test_cases_routes_resolve_spec_streams() -> None:
    report = CASES_REPORT_SUBMITTED_V1
    assert report.payloadType is CasesReportSubmittedV1
    assert report.kind == "event"
    assert report.stream == "xcore:evt:cases:report"
    assert report.targetScope == "broadcast"
    assert report.ttlMs == 604800000
    assert report.replayable and report.idempotentConsumerRecommended
    assert report.owner == "cases"
    assert report.bindings == {}

    notice = CASES_NOTICE_COMMAND_V1
    assert notice.payloadType is CasesNoticeCommandV1
    assert notice.kind == "command"
    assert notice.stream.format(server="mini-pvp") == "xcore:cmd:cases-notice:mini-pvp"
    assert notice.bindings == {"server": "payload.server"}
    assert notice.targetScope == "server"
    assert notice.ttlMs == 120000 and not notice.replayable

    evidence = CASES_EVIDENCE_REQUEST_V1
    assert evidence.payloadType is CasesEvidenceRequestV1
    assert evidence.kind == "rpc-request"
    assert evidence.stream.format(server="mini-pvp") == "xcore:rpc:req:mini-pvp"
    assert evidence.bindings == {"server": "payload.server"}
    assert evidence.ttlMs == 15000 and not evidence.replayable
    assert evidence.response.payloadType is CasesEvidenceResponseV1
    assert evidence.response.messageType == "cases.evidence.response"
    assert evidence.response.messageVersion == 1
    assert evidence.response.stream.format(requester="discord") == "xcore:rpc:resp:discord"
    assert evidence.response.bindings == {"requester": "rpc.requester"}
    for route in (report, notice, evidence):
        assert ROUTES_BY_MESSAGE[(route.messageType, 1)] is route
