from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from datetime import timedelta
from typing import Any

import discord

from ..i18n import normalize_locale, resolve_dm_locale, t
from ._privacy import log_failure
from .access import conflicted
from .model import Case, CaseKind, CaseRace, Party

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class Outcome:
    """One message about a case to one person, over every channel that reaches them.

    ``kind`` is the ``case_notices`` kind the game renders (5.2). ``message_key`` is the bot's
    own text for Discord, formatted with ``number`` and ``params``. ``user_thread`` also posts
    it in the recipient's own private thread. ``player_language`` and ``server`` are what was
    known when the person submitted: they are used only when the player record has nothing
    newer (the language of the client, the server the player is on right now).
    """

    recipient: Party
    kind: str
    message_key: str
    params: dict[str, Any] = field(default_factory=dict)
    player_language: str | None = None
    server: str | None = None
    user_thread: bool = False


class CaseNotifier:
    def __init__(
        self,
        bot,
        settings,
        *,
        find_player: Callable[[str], Awaitable[Any]] | None = None,
    ) -> None:
        self.bot = bot
        self.settings = settings
        # ``uuid -> PlayerRecord | None``: the in-game language and the server to wake up.
        self.find_player = find_player

    async def send(self, service, case: Case, outcome: Outcome) -> None:  # noqa: C901
        """Deliver once per channel; what was delivered is recorded on the case, so a repeat
        after a failure sends only what is still missing."""
        party = outcome.recipient
        params = {"number": case.number, **outcome.params}
        delivery_key = f"{outcome.kind}:{party.player_uuid or party.discord_id}"
        # Progress uses an array of receipts: UUIDs and ids are never Mongo field names.
        current = await service.get(case.number)
        if current.kind == CaseKind.COMPLAINT and (
            conflicted(current, party.discord_id or "")
            or current.subject
            and party.player_uuid
            and party.player_uuid == current.subject.player_uuid
        ):
            return
        receipt = next(
            (
                row
                for row in current.progress.get("notification_receipts", [])
                if row["key"] == delivery_key
            ),
            {},
        )
        player = None
        if party.player_uuid and self.find_player is not None:
            player = await self.find_player(party.player_uuid)
        # Registration is game-only; current links are used for outcome DMs.
        discord_id = party.discord_id
        if not discord_id and outcome.kind != "report_received":
            discord_id = getattr(player, "discord_id", None)
        if current.kind == CaseKind.COMPLAINT and conflicted(current, discord_id or ""):
            return
        errors = []
        if party.player_uuid and not receipt.get("notice"):
            try:
                await self._notice(service, case, outcome, params, player)
                receipt = {**receipt, "notice": True}
                await self._receipt(service, case.number, delivery_key, receipt)
            except Exception as error:  # noqa: BLE001 -- collected; raised together so delivery is retried
                errors.append(error)
        if not discord_id or not discord_id.isdigit():
            if errors:
                raise ExceptionGroup("Outcome delivery is incomplete", errors)
            return
        # Without a preference: the game language as the player record has it now, then the
        # one recorded with the submission, then the language the case was opened in.
        # An unusable value (the in-game "auto") does not hide the next one.
        locale = await resolve_dm_locale(
            int(discord_id),
            service.store,
            normalize_locale(getattr(player, "language", None))
            or normalize_locale(outcome.player_language)
            or (case.origin.locale if party == case.opener else None),
        )
        text = t(locale, outcome.message_key, **params)
        # Each person has a thread of their own; nobody's outcome is posted in another's.
        thread_id = current.user_threads.get(party.discord_id)
        if outcome.user_thread and not receipt.get("thread"):
            required = (
                case.origin.source == "discord"
                and case.opener.discord_id == party.discord_id
                or any(
                    p.get("source") == "discord"
                    and p.get("discord_id") == party.discord_id
                    for p in case.co_openers
                )
            )
            try:
                if thread_id:
                    thread = await service.threads.channel(thread_id)
                    if thread.archived or thread.locked:
                        await thread.edit(archived=False, locked=False)
                    if not await service.threads._has_content(thread, text):
                        await thread.send(
                            text, allowed_mentions=discord.AllowedMentions.none()
                        )
                    receipt = {**receipt, "thread": True}
                    await self._receipt(service, case.number, delivery_key, receipt)
                elif required:
                    raise CaseRace("Reporter's owned thread is missing")
            except Exception as error:  # noqa: BLE001 -- collected; raised together so delivery is retried
                errors.append(error)
        if not receipt.get("dm") and not receipt.get("dm_unavailable"):
            try:
                user = self.bot.get_user(int(discord_id)) or await self.bot.fetch_user(
                    int(discord_id)
                )
                await user.send(text, allowed_mentions=discord.AllowedMentions.none())
            except discord.HTTPException as error:
                if error.status in {403, 404}:
                    # A terminal refusal is recorded as unavailable, never as delivered.
                    receipt = {**receipt, "dm_unavailable": True}
                    await self._receipt(service, case.number, delivery_key, receipt)
                else:
                    errors.append(error)
            except Exception as error:  # noqa: BLE001 -- collected; raised together so delivery is retried
                errors.append(error)
            else:
                receipt = {**receipt, "dm": True}
                await self._receipt(service, case.number, delivery_key, receipt)
        if errors:
            raise ExceptionGroup("Outcome delivery is incomplete", errors)

    async def _notice(
        self, service, case: Case, outcome: Outcome, params: dict[str, Any], player
    ) -> None:
        party = outcome.recipient
        now = service.now()
        notice_id = f"{case.number}:{party.player_uuid}:{outcome.kind}"
        await service.store.insert_notice(
            {
                "_id": notice_id,
                "case_number": case.number,
                "player_uuid": party.player_uuid,
                "kind": outcome.kind,
                "params": {key: str(value) for key, value in params.items()},
                "created_at": now,
                "expires_at": now + timedelta(days=self.settings.cases_notice_ttl_days),
                "delivered_at": None,
            }
        )
        # Where the player is now, then where they submitted from (9.1 step 11).
        server = (
            getattr(player, "online_server", None)
            or outcome.server
            or case.origin.server
        )
        if not server:
            return
        try:
            await service.transport.publish_case_notice(
                notice_id=notice_id, player_uuid=party.player_uuid, server=server
            )
        except Exception:  # noqa: BLE001 -- best-effort wake-up; join delivery remains
            log_failure(
                logger,
                case.kind,
                "Notice wake-up failed for case #%s; join delivery remains available",
                case.number,
            )

    async def _receipt(
        self, service, number: int, key: str, receipt: dict[str, Any]
    ) -> None:
        current = await service.get(number)
        receipts = [
            row
            for row in current.progress.get("notification_receipts", [])
            if row["key"] != key
        ]
        receipts.append({"key": key, **receipt})
        await service.checkpoint(
            number, set_fields={"progress.notification_receipts": receipts}
        )
