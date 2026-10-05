from __future__ import annotations

from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import Any

import pytest
from pymongo.errors import DuplicateKeyError

from xcore_discord_bot.rating_store import (
    STALE_POST_CLAIM,
    RatingStore,
    season_from_doc,
)


def _matches(doc: dict[str, Any], query: dict[str, Any]) -> bool:
    for key, want in query.items():
        if key == "$or":
            if not any(_matches(doc, option) for option in want):
                return False
            continue
        have = doc.get(key)
        if isinstance(want, dict):
            if "$in" in want and have not in want["$in"]:
                return False
            if "$gt" in want and not (have is not None and have > want["$gt"]):
                return False
            if "$lt" in want and not (have is not None and have < want["$lt"]):
                return False
        elif have != want:
            return False
    return True


class _Cursor:
    def __init__(self, docs: list[dict[str, Any]]) -> None:
        self._docs = docs

    def sort(self, key, direction=None):
        keys = key if isinstance(key, list) else [(key, direction)]
        for field, order in reversed(keys):
            self._docs.sort(key=lambda doc, f=field: doc.get(f), reverse=order == -1)
        return self

    def limit(self, count: int):
        self._docs = self._docs[:count]
        return self

    def __aiter__(self):
        async def generate():
            for doc in self._docs:
                yield doc

        return generate()


class _Collection:
    def __init__(self) -> None:
        self.docs: list[dict[str, Any]] = []

    def find(self, query: dict[str, Any]):
        return _Cursor([dict(doc) for doc in self.docs if _matches(doc, query)])

    async def find_one(self, query: dict[str, Any]):
        return next((dict(doc) for doc in self.docs if _matches(doc, query)), None)

    async def count_documents(self, query: dict[str, Any]) -> int:
        return sum(1 for doc in self.docs if _matches(doc, query))

    async def distinct(self, field: str):
        return list({doc[field] for doc in self.docs if field in doc})

    async def insert_one(self, doc: dict[str, Any]):
        if any(existing["_id"] == doc["_id"] for existing in self.docs):
            raise DuplicateKeyError("duplicate")
        self.docs.append(dict(doc))

    async def update_one(self, query, update, upsert=False):
        target = next((doc for doc in self.docs if _matches(doc, query)), None)
        if target is None:
            if not upsert:
                return SimpleNamespace(modified_count=0)
            target = {"_id": query["_id"]}
            target.update(update.get("$setOnInsert", {}))
            self.docs.append(target)
        target.update(update.get("$set", {}))
        for key in update.get("$unset", {}):
            target.pop(key, None)
        return SimpleNamespace(modified_count=1)

    async def delete_one(self, query):
        self.docs = [doc for doc in self.docs if not _matches(doc, query)]


class _Database:
    def __init__(self) -> None:
        self.collections: dict[str, _Collection] = {}

    def __getitem__(self, name: str) -> _Collection:
        return self.collections.setdefault(name, _Collection())


def _store() -> tuple[RatingStore, _Database]:
    db = _Database()
    return RatingStore(SimpleNamespace(database=db)), db


def _season(ladder: str, number: int, status: str, **extra: Any) -> dict[str, Any]:
    return {
        "_id": f"{ladder}:{number}",
        "ladder": ladder,
        "number": number,
        "name": f"S{number}",
        "status": status,
        "starts_at": datetime(2026, 1, 1, tzinfo=UTC),
        "ends_at": datetime(2026, 4, 1, tzinfo=UTC),
        "matches": 5,
        **extra,
    }


def test_season_from_doc_reads_podium_and_summary() -> None:
    season = season_from_doc(
        _season(
            "minipvp",
            2,
            "ARCHIVED",
            summary={"participants": 12, "matches": 40},
            podium=[
                {
                    "place": 1,
                    "uuid": "u1",
                    "pid": 7,
                    "nickname": "Ann",
                    "rating": 1500,
                    "league": "GOLD",
                    "matches": 10,
                    "wins": 8,
                    "discord_id": "42",
                },
                {"place": 2, "uuid": "u2", "pid": 0, "rating": 1400},
                {"place": 3, "uuid": "u3", "pid": -12, "rating": 1300},
                # Written before PIDs could be negative: -1 stood for "no profile".
                {"place": 4, "uuid": "u4", "pid": -1, "rating": 1200},
                {"place": 5, "uuid": "u5", "pid": -1, "signed_pid": True},
                {"place": 6, "uuid": "u6", "signed_pid": True},
            ],
        )
    )

    assert season.id == "minipvp:2"
    assert season.participants == 12
    assert season.podium[0].discord_id == "42"
    assert season.podium[0].pid == 7
    assert [entry.pid for entry in season.podium[1:]] == [0, -12, None, -1, None]
    assert season.podium[1].nickname == "Unknown"
    assert season.podium[1].discord_id is None


@pytest.mark.asyncio
async def test_current_season_prefers_the_running_one() -> None:
    store, db = _store()
    db["rating_seasons"].docs += [
        _season("minipvp", 1, "ARCHIVED"),
        _season("minipvp", 2, "ACTIVE"),
        _season("hexed", 1, "ARCHIVED"),
    ]

    assert (await store.current_season("minipvp")).number == 2
    # while the successor is still being opened, the newest one stands in
    assert (await store.current_season("hexed")).number == 1
    assert await store.current_season("nothing") is None
    assert await store.ladders() == ["hexed", "minipvp"]
    assert [s.number for s in await store.list_seasons("minipvp")] == [2, 1]


@pytest.mark.asyncio
async def test_top_orders_by_rating_then_uuid_and_names_players() -> None:
    store, db = _store()
    db["rating_standings"].docs += [
        {"ladder": "minipvp", "season": 1, "player_uuid": "b", "rating": 1200, "matches": 3, "wins": 1},
        {"ladder": "minipvp", "season": 1, "player_uuid": "a", "rating": 1200, "matches": 4, "wins": 2},
        {"ladder": "minipvp", "season": 1, "player_uuid": "c", "rating": 1500, "matches": 9, "wins": 7},
        {"ladder": "minipvp", "season": 2, "player_uuid": "z", "rating": 9999},
    ]
    db["players"].docs += [
        {"uuid": "c", "nickname": "Cat", "pid": 3},
        {"uuid": "a", "nickname": "Ann", "pid": 1},
    ]

    top = await store.top("minipvp", 1, limit=2)

    assert [(row.rank, row.uuid, row.nickname, row.pid) for row in top] == [
        (1, "c", "Cat", 3),
        (2, "a", "Ann", 1),
    ]
    assert await store.count("minipvp", 1) == 3


@pytest.mark.asyncio
async def test_placings_rank_the_player_on_each_ladder_current_season() -> None:
    store, db = _store()
    db["rating_seasons"].docs += [
        _season("minipvp", 1, "ARCHIVED"),
        _season("minipvp", 2, "ACTIVE"),
        _season("hexed", 1, "ACTIVE"),
    ]
    db["rating_standings"].docs += [
        {"ladder": "minipvp", "season": 2, "player_uuid": "me", "rating": 1100, "matches": 2, "wins": 1},
        {"ladder": "minipvp", "season": 2, "player_uuid": "x", "rating": 1300},
        {"ladder": "minipvp", "season": 2, "player_uuid": "a", "rating": 1100},
        {"ladder": "minipvp", "season": 1, "player_uuid": "me", "rating": 1900},
        {"ladder": "hexed", "season": 1, "player_uuid": "other", "rating": 1000},
    ]

    placings = await store.placings("me")

    assert len(placings) == 1
    assert placings[0].season.ladder == "minipvp"
    assert placings[0].rating == 1100
    # one above by rating, one tied but ahead by uuid
    assert placings[0].rank == 3
    assert placings[0].participants == 3
    # a standing written before peaks were kept has peaked at least where it stands
    assert placings[0].peak_rating == 1100


@pytest.mark.asyncio
async def test_post_claims_are_exclusive_until_released() -> None:
    store, db = _store()

    assert await store.claim_post("minipvp:1", "ended") is True
    assert await store.claim_post("minipvp:1", "ended") is False
    await store.record_post("minipvp:1", "ended", 99)
    assert db["discord_season_posts"].docs[0]["message_id"] == 99

    await store.release_post("minipvp:1", "ended")
    assert await store.claim_post("minipvp:1", "ended") is True


@pytest.mark.asyncio
async def test_a_claim_left_without_a_message_is_taken_over_once_it_is_stale() -> None:
    store, db = _store()
    assert await store.claim_post("minipvp:1", "ended") is True
    claim = db["discord_season_posts"].docs[0]

    # Fresh: its poster may still be sending.
    assert await store.claim_post("minipvp:1", "ended") is False

    # The poster was stopped before it posted or released the claim.
    claim["claimed_at"] = datetime.now(UTC) - STALE_POST_CLAIM - timedelta(seconds=1)
    assert await store.claim_post("minipvp:1", "ended") is True
    assert await store.claim_post("minipvp:1", "ended") is False


@pytest.mark.asyncio
async def test_a_posted_announcement_is_never_taken_over() -> None:
    store, db = _store()
    assert await store.claim_post("minipvp:1", "ended") is True
    await store.record_post("minipvp:1", "ended", 99)
    db["discord_season_posts"].docs[0]["claimed_at"] = datetime.now(UTC) - timedelta(days=1)

    assert await store.claim_post("minipvp:1", "ended") is False


@pytest.mark.asyncio
async def test_a_refused_merge_stays_on_record_but_out_of_the_queue() -> None:
    store, db = _store()
    await store.add_pending_merge("s", "t")

    await store.fail_pending_merge("s", "t", "nope")
    assert await store.pending_merges() == []
    assert db["rating_merge_pending"].docs[0]["error"] == "nope"

    # Queued again by hand: it waits for a server once more.
    await store.add_pending_merge("s", "t")
    assert await store.pending_merges() == [("s", "t")]


@pytest.mark.asyncio
async def test_pending_merges_round_trip_without_duplicates() -> None:
    store, _ = _store()

    await store.add_pending_merge("s", "t")
    await store.add_pending_merge("s", "t")
    assert await store.pending_merges() == [("s", "t")]

    await store.clear_pending_merge("s", "t")
    assert await store.pending_merges() == []
