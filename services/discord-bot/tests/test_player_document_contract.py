from __future__ import annotations

import json
from pathlib import Path

import pytest

from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.store_mappers import player_record_from_doc

# The players document as XCore-plugin writes it. PlayerDataDocumentContractTest in the plugin
# checks its own copy against the real codec; change both copies together.
FIXTURE = Path(__file__).with_name("fixtures") / "player_document.json"
PLUGIN_COPY = (
    Path(__file__).resolve().parents[2]
    / "XCore-plugin/src/test/resources/contract/player_document.json"
)


def test_maps_every_field_the_plugin_writes() -> None:
    record = player_record_from_doc(json.loads(FIXTURE.read_text()))

    # Every value differs from PlayerRecord's default, so a field read under the
    # wrong key shows up as a default here.
    assert record == PlayerRecord(
        pid=42,
        nickname="[red]Contract",
        uuid="uuid-contract",
        username="contract_user",
        ip="203.0.113.7",
        custom_nickname="Custom",
        description="About me",
        language="uk",
        translator_language="en",
        total_play_time=15,
        hexed_rank=3,
        hexed_points=9,
        leaderboard=False,
        unlocked_badges=("translator",),
        active_badge="translator",
        blocked_private_uuids=("uuid-blocked",),
        discord_id="123456789012345678",
        discord_username="contract#1",
        discord_linked_at=1700000000001,
        online=True,
        online_since=1700000000002,
        online_server="survival",
        created_at=1700000000003,
        updated_at=1700000000004,
    )


# shortcut: compares the copies only in the xcore workspace checkout, not in this repo's CI;
# move the fixture to a shared package if the copies ever drift unnoticed.
@pytest.mark.skipif(not PLUGIN_COPY.exists(), reason="XCore-plugin is not checked out")
def test_fixture_matches_the_plugin_copy() -> None:
    assert json.loads(FIXTURE.read_text()) == json.loads(PLUGIN_COPY.read_text())
