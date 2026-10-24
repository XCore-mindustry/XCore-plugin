"""Linked-account badge requests and the explicit, post-acceptance B8 dependency."""

from __future__ import annotations

import logging
from dataclasses import replace
from typing import Literal

import discord

from ...badges import BadgeDef, get_badge, normalize_badge_id
from ...dto import Actor, PlayerRecord
from ...i18n import t
from ...staff_actions import BadgeGrantUnconfirmed, perform_badge_grant
from ..cards import base_card, safe_text
from ..modals import OpenCaseModal, error_text
from ..model import AccessDenied, ActionFailed, Case, CaseKind, Party, Resolution
from ..notify import Outcome
from ..registry import Action, Flow, FormField, PanelContext, PanelEntry, flow_registry
from ._choice import PanelChoiceView

logger = logging.getLogger(__name__)


def requestable_badges(settings) -> tuple[BadgeDef, ...]:
    """Canonical, unique allowlist entries; system and ungrantable entries never escape."""
    found = {}
    for raw in settings.cases_badges_requestable.split(","):
        badge = get_badge(raw)
        if badge and badge.grantable and not badge.system:
            found[badge.id] = badge
    return tuple(found.values())


def _owned(player: PlayerRecord) -> set[str]:
    return {normalize_badge_id(value) for value in player.unlocked_badges}


def _name(player: PlayerRecord, locale: str) -> str:
    return safe_text(
        player.custom_nickname or player.nickname or player.username or "",
        secrets=(player.uuid or "", player.ip or "", player.last_ip or ""),
        limit=70,
    ) or t(locale, "cases-unknown")


async def _linked(service, discord_id: str) -> list[PlayerRecord]:
    # A list query is not a licence to trust stale or incorrectly scoped records.
    players = await service.players.find_players_by_discord_id(discord_id)
    unique = {}
    for player in players:
        if player.uuid and str(player.discord_id) == discord_id:
            unique[player.uuid] = player
    return list(unique.values())


async def _recipient(service, discord_id: str, uuid: str, locale: str) -> PlayerRecord:
    player = await service.players.find_player_by_uuid(uuid)
    if player is None or player.uuid != uuid or str(player.discord_id) != discord_id:
        raise ActionFailed(t(locale, "badges-link-needed"))
    return player


async def _submit(
    context: PanelContext, values: dict[str, str], uuid: str, badge_id: str
) -> Case:
    service, user = context.service, context.interaction.user
    # Redelivery must reach the core's idempotent resume before cooldown or ownership checks.
    existing = await service.store.by_submission(f"discord:{context.interaction.id}")
    if existing:
        if (
            existing.kind != CaseKind.BADGE
            or existing.opener.discord_id != str(user.id)
            or not existing.subject
            or existing.subject.player_uuid != uuid
            or existing.payload.get("badge") != badge_id
        ):
            raise AccessDenied()
        return await context.open(
            opener=existing.opener,
            subject=existing.subject,
            payload=existing.payload,
            dedup_key=existing.dedup_key,
        )
    player = await _recipient(service, str(user.id), uuid, context.locale)
    if badge_id not in {b.id for b in requestable_badges(service.settings)}:
        raise ActionFailed(t(context.locale, "badges-unavailable"))
    if badge_id in _owned(player):
        raise ActionFailed(t(context.locale, "badges-already-owned"))
    linked = await _linked(service, str(user.id))
    secrets = [p.uuid for p in linked if p.uuid]
    secrets.extend(value for p in linked for value in (p.ip, p.last_ip) if value)
    subject = _party_snapshot(player, user, context.locale)
    statement = safe_text(
        values.get("statement", ""), secrets=secrets, limit=1000
    ).strip()
    if not statement:
        raise ActionFailed(t(context.locale, "cases-form-invalid"))
    return await context.open(
        opener=subject,
        subject=subject,
        payload={
            "badge": badge_id,
            "statement": statement,
            "owned_badges": sorted(_owned(player)),
        },
        dedup_key=f"badge:{uuid}:{badge_id}",
    )


def _party_snapshot(player: PlayerRecord, user, locale: str) -> Party:

    return Party(
        player_uuid=player.uuid,
        player_pid=player.pid,
        player_name=_name(player, locale),
        discord_id=str(user.id),
        discord_name=safe_text(user.display_name, secrets=(player.uuid or "",)),
    )


async def _badges(context: PanelContext, player: PlayerRecord) -> None:
    choices = tuple(
        b
        for b in requestable_badges(context.service.settings)
        if b.id not in _owned(player)
    )
    if not choices:
        await context.interaction.followup.send(
            t(context.locale, "badges-no-options"), ephemeral=True
        )
        return

    async def chosen(interaction, badge_id):
        # The select is the private short-lived step. Its first response is a modal;
        # validation requiring I/O is deferred to submit, never before send_modal.
        modal_context = PanelContext(
            context.service, interaction, CaseKind.BADGE, context.locale
        )

        async def submit(current, values):
            return await _submit(current, values, player.uuid, badge_id)

        await interaction.response.send_modal(
            OpenCaseModal(
                modal_context,
                title_key="badges-title",
                fields=(FormField("statement", "badges-statement", paragraph=True),),
                submit=submit,
                files=True,
            )
        )

    await context.interaction.followup.send(
        t(
            context.locale,
            "badges-select-badge",
            account=_name(player, context.locale),
            pid=player.pid,
        ),
        view=PanelChoiceView(
            context,
            [
                discord.SelectOption(
                    label=t(context.locale, "badges-name-" + b.id)[:100],
                    value=b.id,
                )
                for b in choices
            ],
            chosen,
            previous_label=t(context.locale, "badges-previous"),
            next_label=t(context.locale, "badges-next"),
        ),
        ephemeral=True,
    )


async def panel_handler(context: PanelContext) -> None:
    players = await _linked(context.service, str(context.interaction.user.id))
    if not players:
        raise ActionFailed(t(context.locale, "badges-link-needed"))
    if len(players) == 1:
        await _badges(context, players[0])
        return
    # Opaque page-local indices: UUIDs are never displayed or put into component values.
    accounts = {str(i): player.uuid for i, player in enumerate(players)}

    async def chosen(interaction, value):
        await interaction.response.defer(ephemeral=True, thinking=True)
        locale = await context.service.store.remember_interaction(interaction)
        current = PanelContext(context.service, interaction, CaseKind.BADGE, locale)
        try:
            player = await _recipient(
                context.service, str(interaction.user.id), accounts[value], locale
            )
            await _badges(current, player)
        except Exception as error:
            if not isinstance(error, ValueError):
                logger.exception("Badge account selection failed")
            await interaction.followup.send(error_text(locale, error), ephemeral=True)

    await context.interaction.followup.send(
        t(context.locale, "badges-select-account"),
        view=PanelChoiceView(
            context,
            [
                discord.SelectOption(
                    label=t(
                        context.locale,
                        "badges-account",
                        account=_name(p, context.locale),
                        pid=p.pid,
                    )[:100],
                    value=str(i),
                )
                for i, p in enumerate(players)
            ],
            chosen,
            previous_label=t(context.locale, "badges-previous"),
            next_label=t(context.locale, "badges-next"),
        ),
        ephemeral=True,
    )


def card_builder(case: Case, locale: str) -> discord.Embed:
    embed = base_card(case, locale)
    secrets = tuple(
        p.player_uuid for p in (case.opener, case.subject) if p and p.player_uuid
    )
    badge = get_badge(str(case.payload.get("badge", "")))
    embed.add_field(
        name=t(locale, "badges-badge"),
        value=t(locale, "badges-name-" + badge.id)
        if badge
        else t(locale, "cases-unknown"),
    )
    owned = [get_badge(str(value)) for value in case.payload.get("owned_badges", [])]
    embed.add_field(
        name=t(locale, "badges-owned"),
        value=", ".join(t(locale, "badges-name-" + b.id) for b in owned if b)
        or t(locale, "badges-none"),
        inline=False,
    )
    embed.add_field(
        name=t(locale, "badges-statement"),
        value=safe_text(case.payload.get("statement", ""), secrets=secrets)
        or t(locale, "badges-none"),
        inline=False,
    )
    return embed


async def grant(context):
    async def action(claimed: Case) -> Resolution:
        badge = get_badge(str(claimed.payload.get("badge", "")))
        subject = claimed.subject
        if (
            not badge
            or badge.system
            or not badge.grantable
            or badge.id
            not in {b.id for b in requestable_badges(context.service.settings)}
        ):
            raise ActionFailed(t(context.locale, "badges-unavailable"))
        if (
            not subject
            or not subject.player_uuid
            or claimed.dedup_key != f"badge:{subject.player_uuid}:{badge.id}"
        ):
            raise ActionFailed(t(context.locale, "cases-error-player"))
        player = await context.service.players.find_player_by_uuid(subject.player_uuid)
        if player is None or player.uuid != subject.player_uuid:
            raise ActionFailed(t(context.locale, "cases-error-player"))
        try:
            result = await perform_badge_grant(
                context.service.bot,
                player,
                badge.id,
                context.actor,
                case_number=claimed.number,
                case_action_token=claimed.pending_action["token"],
            )
        except BadgeGrantUnconfirmed as error:
            raise ActionFailed(t(context.locale, "badges-grant-unconfirmed")) from error
        if not result.audit_id:
            raise ActionFailed(t(context.locale, "badges-grant-unconfirmed"))
        return Resolution("granted", audit_id=result.audit_id)

    return await context.run(action)


def outcomes(case: Case) -> tuple[Outcome, ...]:
    if (
        not case.resolution
        or case.resolution.code not in {"granted", "rejected"}
        or not case.subject
    ):
        return ()
    badge = get_badge(str(case.payload.get("badge", "")))
    if badge is None:
        return ()
    # The stable recipient account and the original Discord opener, not a later link owner.
    recipient = replace(case.subject, discord_id=case.opener.discord_id)
    granted = case.resolution.code == "granted"
    return (
        Outcome(
            recipient,
            "badge_granted" if granted else "badge_rejected",
            "badges-outcome-granted" if granted else "badges-outcome-rejected",
            {"badge": badge.id},
            user_thread=True,
        ),
    )


async def grant_map_maker(
    service, discord_id: str | int, *, actor: Actor, request_id: str | None = None
) -> Literal["complete", "link_needed", "pending_accounts"]:
    """After durable map acceptance, grant to every current linked account and add the role.

    Fresh membership and inventory checks keep partial work pending; retries republish owned
    inventory. Configured role failures propagate. The map flow owns pending persistence and
    its 30-day retry window. Badge grants never prove a map acceptance.
    """
    discord_id = str(discord_id)
    if not discord_id.isdigit() or int(discord_id) <= 0:
        raise ValueError("A valid map author's Discord ID is required")
    badge = get_badge("map-maker")
    if badge is None or badge.system or not badge.grantable:
        raise ValueError("Map Maker is not grantable")
    role_id = service.settings.discord_map_maker_role_id
    players = await _linked(service, discord_id)
    if not players:
        return "link_needed"
    pending = False
    for snapshot in players:
        try:
            player = await _recipient(service, discord_id, snapshot.uuid, "en")
        except ActionFailed:
            pending = True
            continue
        result = await perform_badge_grant(
            service.bot, player, badge.id, actor, request_id=request_id
        )
        # Without a case number the slash-compatible helper does not verify a false
        # modification result. Confirm it ourselves; missing data is explicit pending.
        fresh = await service.players.find_player_by_uuid(player.uuid)
        if (
            fresh is None
            or fresh.uuid != player.uuid
            or str(fresh.discord_id) != discord_id
            or badge.id not in _owned(fresh)
        ):
            pending = True
        else:
            if not result.changed:
                # Re-publish fresh inventory on retry after a prior delivery failure.
                await service.bot.bus.publish_player_badge_inventory_changed(
                    uuid_value=fresh.uuid,
                    active_badge=fresh.active_badge,
                    unlocked_badges=fresh.unlocked_badges,
                )
    if pending:
        return "pending_accounts"
    # Include a final membership read: newly linked accounts must also be serviced on retry.
    current = {p.uuid for p in await _linked(service, discord_id)}
    if current != {p.uuid for p in players}:
        return "pending_accounts" if current else "link_needed"
    if role_id:
        guild = service.bot.get_guild(service.settings.discord_guild_id)
        if guild is None:
            raise LookupError("Map Maker guild is unavailable")
        role = guild.get_role(role_id)
        if role is None:
            raise LookupError("Configured Map Maker role is unavailable")
        member = guild.get_member(int(discord_id)) or await guild.fetch_member(
            int(discord_id)
        )
        if member is None:
            raise LookupError("Map author is unavailable in the configured guild")
        if role_id not in {r.id for r in member.roles}:
            await member.add_roles(role)
    return "complete"


BADGE_FLOW = flow_registry.register(
    Flow(
        kind=CaseKind.BADGE,
        card_builder=card_builder,
        panel=PanelEntry("badges-panel", emoji="🏅", handler=panel_handler),
        actions=(
            Action(
                "grant", "cases-button-grant", grant, style=discord.ButtonStyle.success
            ),
        ),
        outcomes=outcomes,
    )
)
