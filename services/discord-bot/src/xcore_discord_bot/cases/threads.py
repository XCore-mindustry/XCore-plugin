from __future__ import annotations

import logging
from datetime import timedelta

import discord

from ..i18n import t
from .access import conflicted
from .cards import (
    card_locale,
    case_secrets,
    evidence_messages,
    party_text,
    safe_text,
    sanitize_card,
    thread_title,
)
from .model import AccessDenied, Case, CaseKind, CaseStatus
from .views import case_view

logger = logging.getLogger(__name__)
FORUM_TAGS = (
    "Report",
    "Appeal",
    "Badge",
    "Complaint",
    "Open",
    "Taken",
    "Resolved",
    "Rejected",
)
KIND_TAGS = ("Report", "Appeal", "Badge", "Complaint")
# A post or thread a failed attempt left behind is recent; older archives are not walked.
ADOPT_ARCHIVED_LIMIT = 200
WEEK_MINUTES = 10080
# Messages a person writes in a row get one notice, linking to the first of them.
USER_MESSAGE_BURST = timedelta(minutes=30)
STATUS_TAGS = {
    CaseStatus.CREATING: "Open",
    CaseStatus.OPEN: "Open",
    CaseStatus.TAKEN: "Taken",
    CaseStatus.RESOLVED: "Resolved",
    CaseStatus.REJECTED: "Rejected",
}


def tags_by_name(forum, names: tuple[str, ...]) -> list:
    tags = {tag.name: tag for tag in forum.available_tags}
    for name in names:
        if name not in tags:
            logger.warning("Case forum %s has no tag %s", forum.id, name)
    return [tags[name] for name in names if name in tags]


class CaseThreads:
    def __init__(self, bot, settings) -> None:
        self.bot = bot
        self.settings = settings

    async def channel(self, channel_id: int | str):
        channel = self.bot.get_channel(int(channel_id))
        return (
            channel
            if channel is not None
            else await self.bot.fetch_channel(int(channel_id))
        )

    def forum_id(self, kind: CaseKind) -> int:
        if kind == CaseKind.MAP:
            return self.settings.discord_maps_forum_id
        if kind == CaseKind.COMPLAINT:
            return self.settings.cases_senior_forum_id
        return self.settings.cases_staff_forum_id

    def user_parent_id(self, kind: CaseKind) -> int:
        if kind == CaseKind.COMPLAINT:
            return (
                self.settings.cases_complaint_thread_channel_id
                or self.settings.cases_panel_channel_id
            )
        return self.settings.cases_panel_channel_id

    def user_thread_parents(self) -> set[int]:
        return {
            self.settings.cases_panel_channel_id,
            self.settings.cases_complaint_thread_channel_id,
        } - {0}

    def owns_parent(self, parent_id: int) -> bool:
        """Whether a thread under this channel can belong to a case at all."""
        return parent_id in self.user_thread_parents() | (
            {
                self.settings.cases_staff_forum_id,
                self.settings.cases_senior_forum_id,
                self.settings.discord_maps_forum_id,
            }
            - {0}
        )

    def _mine(self, thread, number: int) -> bool:
        return (
            thread.name.startswith(f"#{number} ")
            and thread.owner_id == self.bot.user.id
        )

    async def find_post(self, case: Case):
        forum = await self.channel(self.forum_id(case.kind))
        for thread in forum.threads:
            if self._mine(thread, case.number):
                return thread
        async for thread in forum.archived_threads(limit=ADOPT_ARCHIVED_LIMIT):
            if self._mine(thread, case.number):
                return thread
        return None

    def _card(self, service, case):
        locale = card_locale(case, self.settings)
        return sanitize_card(
            service.registry.require(case.kind).card_builder(case, locale), case
        )

    async def ensure_post(
        self, service, case: Case, *, adopt: bool = True
    ) -> dict[str, str]:
        """The case post and its card. ``adopt`` looks for a post an earlier attempt created."""
        forum = await self.channel(
            case.discord.get("forum_id") or self.forum_id(case.kind)
        )
        thread = (
            await self.channel(case.discord["thread_id"])
            if case.discord.get("thread_id")
            else await self.find_post(case)
            if case.kind != CaseKind.MAP and adopt
            else None
        )
        if case.kind == CaseKind.MAP and thread is None:
            raise ValueError("A map case requires the author's existing thread")
        if thread is not None:
            if thread.archived or thread.locked:
                await thread.edit(archived=False, locked=False)
            if case.kind == CaseKind.MAP:
                message = await self._find_card(thread, case.number) if adopt else None
                if message is None:
                    message = await thread.send(
                        embed=self._card(service, case),
                        view=case_view(
                            service.registry, case, card_locale(case, self.settings)
                        ),
                        allowed_mentions=discord.AllowedMentions.none(),
                    )
            else:
                message = await thread.fetch_message(thread.id)
        else:
            created = await forum.create_thread(
                name=thread_title(case, self.settings.cases_staff_locale),
                embed=self._card(service, case),
                view=case_view(
                    service.registry, case, self.settings.cases_staff_locale
                ),
                applied_tags=tags_by_name(forum, (case.kind.value.title(), "Open")),
                auto_archive_duration=WEEK_MINUTES,
                allowed_mentions=discord.AllowedMentions.none(),
            )
            thread, message = created.thread, created.message
        return {
            "forum_id": str(forum.id),
            "thread_id": str(thread.id),
            "card_message_id": str(message.id),
        }

    async def _find_card(self, thread, number: int):
        # Only map creation retries use this. Search forward from the starter so a card
        # sent before a lost ID write is still adopted after arbitrarily much discussion.
        async for message in thread.history(
            limit=None, oldest_first=True, after=discord.Object(id=thread.id)
        ):
            if message.author.id == self.bot.user.id and any(
                e.footer.text == f"#{number}" for e in message.embeds
            ):
                return message
        return None

    async def _has_content(self, thread, content: str) -> bool:
        async for message in thread.history(limit=ADOPT_ARCHIVED_LIMIT):
            if message.author.id == self.bot.user.id and message.content == content:
                return True
        return False

    async def post_evidence(self, service, case: Case) -> None:
        thread = await self.channel(case.discord["thread_id"])
        if thread.archived:
            await thread.edit(archived=False)
        for content in evidence_messages(case, card_locale(case, self.settings)):
            if not await self._has_content(thread, content):
                message = await thread.send(
                    content, allowed_mentions=discord.AllowedMentions.none()
                )
                await service.checkpoint(
                    case.number,
                    event_type="evidence_posted",
                    data={"message_id": str(message.id)},
                )
        if case.kind == CaseKind.COMPLAINT:
            await service.checkpoint(
                case.number,
                set_fields={"progress.complaint_statement_post": str(thread.id)},
            )

    async def post_note(self, case: Case, content: str) -> None:
        """A line for staff under the card. The same text is posted once, so a hook that
        runs again after a failure adds nothing."""
        content = safe_text(content, secrets=case_secrets(case), limit=1900)
        thread = await self.channel(case.discord["thread_id"])
        if thread.archived:
            await thread.edit(archived=False)
        if not await self._has_content(thread, content):
            await thread.send(content, allowed_mentions=discord.AllowedMentions.none())

    async def upload_attachments(self, service, number: int, attachments) -> None:
        """Call from a flow's submit hook while the attachment URLs are still valid."""
        case = await service.get(number)
        thread = await self.channel(case.discord["thread_id"])
        for attachment in attachments[:5]:
            if any(
                a.get("source_id") == str(attachment.id)
                for a in case.evidence.get("attachments", [])
            ):
                continue
            message = await thread.send(
                file=await attachment.to_file(),
                allowed_mentions=discord.AllowedMentions.none(),
            )
            current = await service.get(number)
            await service.store.cas(
                current,
                push_fields={
                    "evidence.attachments": {
                        "source_id": str(attachment.id),
                        "message_id": str(message.id),
                        "file_name": attachment.filename,
                        "size": attachment.size,
                    }
                },
            )
            case = await service.get(number)

    async def _private_thread(
        self, case: Case, thread_id: str | None, *, locale: str, adopt: bool
    ):
        """One private thread of a case. ``adopt`` looks for one an earlier attempt created
        and did not record; a thread that already belongs to someone is never adopted."""
        if thread_id:
            return await self.channel(thread_id)
        parent = await self.channel(self.user_parent_id(case.kind))
        recorded = set(case.user_threads.values())

        def orphan(thread) -> bool:
            return self._mine(thread, case.number) and str(thread.id) not in recorded

        if adopt:
            for candidate in parent.threads:
                if orphan(candidate):
                    return candidate
            async for candidate in parent.archived_threads(
                private=True, limit=ADOPT_ARCHIVED_LIMIT
            ):
                if orphan(candidate):
                    return candidate
        return await parent.create_thread(
            name=f"#{case.number} · {t(locale, 'cases-kind-' + case.kind.value)}"[:100],
            type=discord.ChannelType.private_thread,
            invitable=False,
            auto_archive_duration=WEEK_MINUTES,
        )

    async def _welcome(self, thread, case: Case, discord_id: str, locale: str) -> None:
        if thread.archived or thread.locked:
            await thread.edit(archived=False, locked=False)
        if case.kind != CaseKind.COMPLAINT or not conflicted(case, discord_id):
            await thread.add_user(discord.Object(id=int(discord_id)))
        content = t(locale, "cases-user-thread-note", number=case.number)
        if not await self._has_content(thread, content):
            await thread.send(content, allowed_mentions=discord.AllowedMentions.none())

    @staticmethod
    def _new_membership(case: Case, thread_id: str) -> dict:
        """Record a new thread's empty invitation set atomically with its owner id."""
        rows = list(case.discord.get("managed_memberships", []))
        if any(row["thread_id"] == thread_id for row in rows):
            return {}
        return {
            "discord.managed_memberships": [
                *rows,
                {"thread_id": thread_id, "staff_ids": [], "done": True},
            ]
        }

    async def ensure_user_thread(
        self, service, case: Case, *, adopt: bool = True
    ) -> dict[str, str]:
        """The opener's private thread. ``adopt`` looks for one an earlier attempt created."""
        if not case.opener.discord_id:
            raise ValueError("Discord-origin case has no opener account")
        thread = await self._private_thread(
            case,
            case.discord.get("user_thread_id"),
            locale=case.origin.locale,
            adopt=adopt,
        )
        # Save the id before membership and the note: a failure resumes on this thread.
        fields = {"discord.user_thread_id": str(thread.id)}
        if not case.discord.get("user_thread_id"):
            fields.update(self._new_membership(case, str(thread.id)))
        await service.checkpoint(case.number, set_fields=fields)
        await self._welcome(thread, case, case.opener.discord_id, case.origin.locale)
        return {"user_thread_id": str(thread.id)}

    async def ensure_reporter_thread(self, service, case: Case, joined: dict) -> Case:
        """The private thread of a Discord reporter who joined a report someone else opened.

        It belongs to that reporter alone: the opener and the other reporters are not in it.
        The row in ``discord.reporter_threads`` is written first without a thread id (a later
        attempt then looks for the thread before creating one), then with the id, then ``done``.
        """
        discord_id = str(joined["discord_id"])

        async def save(row: dict, *, new_thread: bool = False) -> Case:
            current = await service.get(case.number)
            rows = [
                r
                for r in current.discord.get("reporter_threads", [])
                if r["discord_id"] != discord_id
            ]
            fields = {"discord.reporter_threads": [*rows, row]}
            if new_thread:
                fields.update(self._new_membership(current, row["thread_id"]))
            return await service.checkpoint(case.number, set_fields=fields)

        row = next(
            (
                r
                for r in case.discord.get("reporter_threads", [])
                if r["discord_id"] == discord_id
            ),
            None,
        )
        if row is None:
            row = {"discord_id": discord_id, "thread_id": None}
            case = await save(row)
            adopt = False
        else:
            adopt = True
        thread = await self._private_thread(
            case, row["thread_id"], locale=joined["locale"], adopt=adopt
        )
        new_thread = not row.get("thread_id")
        row = {**row, "thread_id": str(thread.id)}
        case = await save(row, new_thread=new_thread)
        await self._welcome(thread, case, discord_id, joined["locale"])
        return await save({**row, "done": True})

    async def exclude_subject(self, case: Case) -> None:
        """Remove explicit invitations; inherited Discord permissions cannot be revoked here."""
        if (
            case.kind != CaseKind.COMPLAINT
            or not case.subject
            or not case.subject.discord_id
        ):
            return
        for thread_id in case.user_threads.values():
            thread = await self.channel(thread_id)
            if thread.archived or thread.locked:
                await thread.edit(archived=False, locked=False)
            await thread.remove_user(discord.Object(id=int(case.subject.discord_id)))

    async def validate_private_threads(self, service, case: Case) -> Case:
        """Forget missing threads even when no gateway deletion event was received."""
        errors = []
        for thread_id in case.user_threads.values():
            try:
                await self.channel(thread_id)
            except discord.NotFound:
                await service.thread_deleted(int(thread_id))
            except Exception as error:  # noqa: BLE001 -- collected; the caller records the step for a retry
                errors.append(error)
        if errors:
            raise ExceptionGroup("Private thread lookup failed", errors)
        return await service.get(case.number)

    async def reconcile_memberships(self, service, case: Case) -> None:
        """Owner plus current authorized assignee; remember invitations before adding.

        This manages explicit invitations only. Discord Administrator/Manage Threads
        access comes from the parent permissions, not these membership records.
        """
        await self.exclude_subject(case)
        if not case.user_threads:
            return
        staff = (case.assignee or {}).get("discord_id")
        errors = []
        if staff:
            try:
                guild = self.bot.get_guild(service.settings.discord_guild_id)
                if guild is None:
                    raise RuntimeError(
                        "Case guild is unavailable for membership authorization"
                    )
                member = guild.get_member(int(staff)) or await guild.fetch_member(
                    int(staff)
                )
                await service.authorize(case, member, "take")
            except (AccessDenied, discord.NotFound):
                staff = None
            except Exception as error:  # noqa: BLE001 -- collected; the caller records the step for a retry
                staff = None
                errors.append(error)
        legacy = case.progress.get("user_thread_staff", [])
        for owner, thread_id in case.user_threads.items():
            try:
                current = await service.get(case.number)
                rows = list(current.discord.get("managed_memberships", []))
                managed = next((r for r in rows if r["thread_id"] == thread_id), None)
                invited = set(managed["staff_ids"] if managed else legacy)
                wanted = {staff} if staff and staff != owner else set()
                complete = bool(managed and managed.get("done") and invited == wanted)
                thread = await self.channel(thread_id)
                # Mutating membership on an archived thread requires reopening it.
                if (thread.archived or thread.locked) and (
                    invited != wanted or wanted and not complete
                ):
                    await thread.edit(archived=False, locked=False)
                for old in invited - wanted:
                    if old != owner:
                        await thread.remove_user(discord.Object(id=int(old)))
                # Keep the intent even if an add succeeds but its acknowledgement is lost.
                updated = {
                    "thread_id": thread_id,
                    "staff_ids": sorted(wanted),
                    "done": complete or not wanted,
                }
                if managed != updated:
                    case = await service.checkpoint(
                        case.number,
                        set_fields={
                            "discord.managed_memberships": [
                                r for r in rows if r["thread_id"] != thread_id
                            ]
                            + [updated]
                        },
                    )
                if not complete and wanted:
                    for new in wanted:
                        await thread.add_user(discord.Object(id=int(new)))
                    current = await service.get(case.number)
                    rows = current.discord.get("managed_memberships", [])
                    await service.checkpoint(
                        case.number,
                        set_fields={
                            "discord.managed_memberships": [
                                r for r in rows if r["thread_id"] != thread_id
                            ]
                            + [{**updated, "done": True}]
                        },
                    )
                if not case.active and (not thread.archived or not thread.locked):
                    await thread.edit(archived=True, locked=True)
            except Exception as error:  # noqa: BLE001 -- collected; the caller records the step for a retry
                errors.append(error)
        if errors:
            raise ExceptionGroup("Private thread membership repair failed", errors)

    async def render(self, service, case: Case) -> None:
        """Project the stored case onto Discord: card, buttons, tags, archive and lock."""
        await self.reconcile_memberships(service, case)
        if not case.discord.get("thread_id") or not case.discord.get("card_message_id"):
            await self._render_user_thread(case)
            return
        thread = await self.channel(case.discord["thread_id"])
        if thread.archived or thread.locked:
            # An archived thread refuses message edits; a closed one is archived again below.
            await thread.edit(archived=False, locked=False)
        locale = card_locale(case, self.settings)
        message = await thread.fetch_message(int(case.discord["card_message_id"]))
        await message.edit(
            embed=self._card(service, case),
            view=case_view(service.registry, case, locale),
            allowed_mentions=discord.AllowedMentions.none(),
        )
        forum = await self.channel(case.discord["forum_id"])
        if case.kind == CaseKind.MAP:
            managed = (
                self.settings.maps_tag_waiting,
                self.settings.maps_tag_accepted,
                self.settings.maps_tag_rejected,
            )
            names = (
                managed[1]
                if case.status == CaseStatus.RESOLVED
                else managed[2]
                if case.status == CaseStatus.REJECTED
                else managed[0],
            )
            # The author's public post is never archived; a rejected one is locked (9.5).
            closing = {"locked": True} if case.status == CaseStatus.REJECTED else {}
        else:
            managed = KIND_TAGS + tuple(STATUS_TAGS.values())
            names = (case.kind.value.title(), STATUS_TAGS.get(case.status, "Rejected"))
            closing = {} if case.active else {"archived": True, "locked": True}
        kept = [tag for tag in thread.applied_tags if tag.name not in managed]
        if case.kind == CaseKind.MAP:
            # Reserve one of Discord's five slots for the review status.
            kept = kept[:4]
        tags = (kept + tags_by_name(forum, names))[:5]
        changes: dict = dict(closing)
        if [tag.name for tag in tags] != [tag.name for tag in thread.applied_tags]:
            changes["applied_tags"] = tags
        if changes:
            await thread.edit(**changes)
        await self._render_user_thread(case)

    async def _render_user_thread(self, case: Case) -> None:
        closed = not case.active
        for thread_id in case.user_threads.values():
            user_thread = await self.channel(thread_id)
            if user_thread.archived != closed or user_thread.locked != closed:
                await user_thread.edit(archived=closed, locked=closed)

    async def announce_user_message(self, service, message) -> None:
        """Point staff at what a person wrote in their own thread (8.4); nothing is copied."""
        if (
            message.author.bot
            or getattr(message.channel, "parent_id", None)
            not in self.user_thread_parents()
        ):
            return
        case = await service.store.by_thread(message.channel.id)
        if (
            case is None
            or not service.registry.enabled(case.kind)
            or case.user_threads.get(str(message.author.id)) != str(message.channel.id)
            or not case.active
            or not case.discord.get("thread_id")
        ):
            return
        author = next(
            (
                party
                for party in case.reporters
                if party.discord_id == str(message.author.id)
            ),
            case.opener,
        )
        name = party_text(
            author,
            self.settings.cases_staff_locale,
            secrets=case_secrets(case) if case.kind == CaseKind.COMPLAINT else (),
        )

        def notice(url: str) -> str:
            # Nothing pings (AllowedMentions.none()), so the mention can stay readable.
            return safe_text(
                t(
                    self.settings.cases_staff_locale,
                    "cases-user-message",
                    name=name,
                    url=url,
                ),
                secrets=case_secrets(case),
                limit=1900,
                keep_mentions=True,
            )

        thread = await self.channel(case.discord["thread_id"])
        async for last in thread.history(limit=1):
            if (
                last.author.id == self.bot.user.id
                and last.content.startswith(notice(""))
                and discord.utils.utcnow() - last.created_at < USER_MESSAGE_BURST
            ):
                return
        if thread.archived:
            await thread.edit(archived=False)
        await thread.send(
            notice(message.jump_url), allowed_mentions=discord.AllowedMentions.none()
        )
