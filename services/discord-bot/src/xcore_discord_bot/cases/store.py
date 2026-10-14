from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from pymongo import ASCENDING, DESCENDING, ReturnDocument
from pymongo.errors import DuplicateKeyError

from ..i18n import (
    LocalePref,
    auto_pref_needs_update,
    from_discord_locale,
    pick_interaction_locale,
)
from ..mongo_store import MongoStore
from .model import Case, CaseRace, check_transition

# Old Ticketeer tickets stay below this number, so the two sequences never collide (O13).
CASE_NUMBER_SEED = 2000


class CaseStore:
    def __init__(self, store: MongoStore) -> None:
        self._store = store
        self._prefs: dict[int, LocalePref] = {}
        # Write-through copies of the active cases. A button that opens a form has to answer
        # with the modal before any I/O; the copy only picks the form, every decision is taken
        # again from MongoDB on submit.
        self._active: dict[int, Case] = {}

    @property
    def database(self):
        return self._store.database

    async def ensure_indexes(self) -> None:
        cases = self.database["cases"]
        await cases.create_index("origin.submission_id", unique=True)
        await cases.create_index(
            [("kind", ASCENDING), ("dedup_key", ASCENDING)],
            unique=True,
            partialFilterExpression={"active": True, "dedup_key": {"$type": "string"}},
        )
        await cases.create_index(
            [("kind", ASCENDING), ("status", ASCENDING), ("updated_at", DESCENDING)]
        )
        for field in ("subject.player_uuid", "opener.player_uuid", "opener.discord_id"):
            await cases.create_index([(field, ASCENDING), ("created_at", DESCENDING)])
        # How many cases of a kind were opened lately (the report burst breaker).
        await cases.create_index([("kind", ASCENDING), ("created_at", DESCENDING)])
        await cases.create_index("discord.thread_id", unique=True, sparse=True)
        await cases.create_index("discord.user_thread_id", sparse=True)
        await cases.create_index("discord.reporter_threads.thread_id", sparse=True)
        await cases.create_index("co_openers.submission_id", sparse=True)
        await cases.create_index("co_openers.discord_id", sparse=True)
        await self.database["case_notices"].create_index(
            [("player_uuid", ASCENDING), ("delivered_at", ASCENDING)]
        )
        await self.database["case_notices"].create_index(
            "expires_at", expireAfterSeconds=0
        )
        await self.database["case_report_limits"].create_index(
            "expires_at", expireAfterSeconds=0
        )
        await self.database["moderation_audit"].create_index(
            "details.extra.case", sparse=True
        )
        await self.database["counters"].update_one(
            {"_id": "case_number"},
            {"$setOnInsert": {"seq": CASE_NUMBER_SEED}},
            upsert=True,
        )

    async def next_case_number(self) -> int:
        row = await self.database["counters"].find_one_and_update(
            {"_id": "case_number"},
            {"$inc": {"seq": 1}},
            upsert=True,
            return_document=ReturnDocument.AFTER,
        )
        return int(row["seq"])

    def _seen(self, row: dict[str, Any] | None) -> Case | None:
        if row is None:
            return None
        case = Case.from_doc(row)
        if case.active:
            self._active[case.number] = case
        else:
            self._active.pop(case.number, None)
        return case

    def peek(self, number: int) -> Case | None:
        """The last known state of an active case, without I/O. Never a basis for a decision."""
        return self._active.get(number)

    async def load_active(self) -> None:
        self._active = {}
        async for row in self.database["cases"].find({"active": True}):
            self._seen(row)

    async def insert(self, case: Case) -> None:
        doc = case.to_doc()
        await self.database["cases"].insert_one(doc)
        self._seen(doc)

    async def get(self, number: int) -> Case | None:
        row = await self.database["cases"].find_one({"_id": number})
        if row is None:
            self._active.pop(number, None)
        return self._seen(row)

    async def by_submission(self, submission_id: str) -> Case | None:
        row = await self.database["cases"].find_one(
            {
                "$or": [
                    {"origin.submission_id": submission_id},
                    {"co_openers.submission_id": submission_id},
                ]
            }
        )
        return self._seen(row)

    async def by_dedup(self, kind, dedup_key: str) -> Case | None:
        row = await self.database["cases"].find_one(
            {"kind": str(kind), "dedup_key": dedup_key, "active": True}
        )
        return self._seen(row)

    async def by_thread(self, thread_id: int | str) -> Case | None:
        row = await self.database["cases"].find_one(
            {
                "$or": [
                    {"discord.thread_id": str(thread_id)},
                    {"discord.user_thread_id": str(thread_id)},
                    {"discord.reporter_threads.thread_id": str(thread_id)},
                ]
            }
        )
        return self._seen(row)

    async def list_cases(
        self, query: dict[str, Any] | None = None, *, limit: int = 100
    ) -> list[Case]:
        cursor = (
            self.database["cases"].find(query or {}).sort("_id", ASCENDING).limit(limit)
        )
        return [self._seen(row) async for row in cursor]

    async def count_open(self, discord_id: str, kind) -> int:
        return await self.database["cases"].count_documents(
            {
                "kind": str(kind),
                "active": True,
                "$or": [
                    {"opener.discord_id": discord_id},
                    {"co_openers.discord_id": discord_id},
                ],
            }
        )

    async def latest_submission_at(self, discord_id: str, kind) -> datetime | None:
        from .model import utc

        cursor = (
            self.database["cases"]
            .find(
                {
                    "kind": str(kind),
                    "$or": [
                        {"opener.discord_id": discord_id},
                        {"co_openers.discord_id": discord_id},
                    ],
                }
            )
            .sort("updated_at", DESCENDING)
        )
        timestamps = []
        async for row in cursor:
            if row["opener"].get("discord_id") == discord_id:
                timestamps.append(utc(row["created_at"]))
            timestamps.extend(
                utc(p["joined_at"])
                for p in row.get("co_openers", [])
                if p.get("discord_id") == discord_id
            )
        return max(timestamps) if timestamps else None

    async def cas(
        self,
        case: Case,
        *,
        set_fields: dict[str, Any] | None = None,
        unset_fields: tuple[str, ...] = (),
        event: dict[str, Any] | None = None,
        push_fields: dict[str, Any] | None = None,
        action_token: str | None = None,
    ) -> Case:
        fields = dict(set_fields or {})
        immutable = {
            "_id",
            "revision",
            "events",
            "kind",
            "origin",
            "opener",
            "created_at",
        }
        if any(key.split(".")[0] in immutable for key in fields):
            raise ValueError("Immutable or append-only case fields")
        if "status" in fields:
            from .model import ACTIVE_STATUSES, CaseStatus

            status = CaseStatus(fields["status"])
            check_transition(case.status, status)
            fields["status"] = status.value
            if status in ACTIVE_STATUSES:
                fields["active"] = True
            else:
                unset_fields += ("active",)
        fields["updated_at"] = datetime.now(UTC)
        query: dict[str, Any] = {"_id": case.number, "revision": case.revision}
        query["pending_action.token" if action_token else "pending_action"] = (
            action_token if action_token else {"$exists": False}
        )
        update: dict[str, Any] = {"$set": fields, "$inc": {"revision": 1}}
        if unset_fields:
            if any(key.split(".")[0] in immutable for key in unset_fields):
                raise ValueError("Immutable or append-only case fields")
            update["$unset"] = {key: "" for key in unset_fields}
        pushes = dict(push_fields or {})
        if event is not None:
            pushes["events"] = event
        if pushes:
            update["$push"] = pushes
        row = await self.database["cases"].find_one_and_update(
            query, update, return_document=ReturnDocument.AFTER
        )
        if row is None:
            raise CaseRace()
        return self._seen(row)

    async def audited_numbers(self, numbers: list[int]) -> set[int]:
        """Which of these cases a sanction audit record refers to (reconcile duty 3)."""
        if not numbers:
            return set()
        cursor = self.database["moderation_audit"].find(
            {"details.extra.case": {"$in": [str(number) for number in numbers]}}
        )
        return {int(row["details"]["extra"]["case"]) async for row in cursor}

    async def audits_for(
        self,
        case: Case,
        *,
        action_token: str | None = None,
        since: datetime | None = None,
    ) -> list[dict[str, Any]]:
        query: dict[str, Any] = {
            "details.extra.case": str(case.number),
            "occurred_at": {"$gte": max(case.created_at, since or case.created_at)},
        }
        if action_token is not None:
            query["details.extra.case_action_token"] = action_token
        cursor = (
            self.database["moderation_audit"]
            .find(query)
            .sort("occurred_at", DESCENDING)
            .limit(20)
        )
        return [row async for row in cursor]

    async def insert_notice(self, notice: dict[str, Any]) -> bool:
        try:
            await self.database["case_notices"].insert_one(notice)
        except DuplicateKeyError:
            return False
        return True

    async def get_panel(self) -> dict[str, Any] | None:
        return await self.database["case_panels"].find_one({"_id": "main"})

    async def save_panel(
        self, *, channel_id: str, message_id: str, rendered_hash: str
    ) -> None:
        await self.database["case_panels"].update_one(
            {"_id": "main"},
            {
                "$set": {
                    "channel_id": channel_id,
                    "message_id": message_id,
                    "rendered_hash": rendered_hash,
                }
            },
            upsert=True,
        )

    async def load_locale_prefs(self) -> None:
        self._prefs = {
            int(row["_id"]): LocalePref(row["locale"], row["source"])
            async for row in self.database["discord_user_prefs"].find({})
        }

    def interaction_locale_now(self, interaction) -> str:
        """A warm preference cache lets a modal be the first response without I/O."""
        return pick_interaction_locale(
            self._prefs.get(interaction.user.id), interaction.locale
        )

    async def get_locale_pref(self, discord_id: int) -> LocalePref | None:
        row = await self.database["discord_user_prefs"].find_one(
            {"_id": str(discord_id)}
        )
        pref = LocalePref(row["locale"], row["source"]) if row else None
        if pref:
            self._prefs[discord_id] = pref
        return pref

    async def set_locale_pref(
        self, discord_id: int, locale: str, *, source: str = "manual"
    ) -> LocalePref:
        if locale not in {"en", "ru", "uk"} or source not in {"auto", "manual"}:
            raise ValueError("Invalid locale preference")
        query: dict[str, Any] = {"_id": str(discord_id)}
        if source == "auto":
            query["source"] = {"$ne": "manual"}
        try:
            row = await self.database["discord_user_prefs"].find_one_and_update(
                query,
                {
                    "$set": {
                        "locale": locale,
                        "source": source,
                        "updated_at": datetime.now(UTC),
                    }
                },
                upsert=True,
                return_document=ReturnDocument.AFTER,
            )
            pref = LocalePref(row["locale"], row["source"])
        except DuplicateKeyError:
            # A concurrent manual selection wins over an auto upsert.
            pref = await self.get_locale_pref(discord_id)
            if pref is None:
                raise
        self._prefs[discord_id] = pref
        return pref

    async def remember_interaction(self, interaction) -> str:
        """Refresh the ``auto`` row (8.6) and return the locale to answer in.

        The warm cache decides whether anything has to be written: a manual choice is never
        overwritten and an unchanged locale costs no write.
        """
        user_id = int(interaction.user.id)
        locale = from_discord_locale(getattr(interaction, "locale", None))
        pref = self._prefs.get(user_id)
        if auto_pref_needs_update(pref, locale):
            pref = await self.set_locale_pref(user_id, locale, source="auto")
        return pick_interaction_locale(pref, getattr(interaction, "locale", None))
