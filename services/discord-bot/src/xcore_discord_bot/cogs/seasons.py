from __future__ import annotations

import logging
from datetime import UTC, datetime
from typing import TYPE_CHECKING, Literal

import discord
from discord import Interaction, app_commands
from discord.ext import commands
from xcore_protocol.generated.shared import SeasonPrizeV1Kind

from ..badges import badge_choice_label, grantable_badges
from ..redis_bus import RpcRejected
from ..rpc.mindustry_rpc import NoLiveServerError
from ..season_embeds import (
    LADDER_NAMES,
    build_season_info_embed,
    build_season_prizes_embed,
    build_season_top_embed,
    ladder_name,
    season_title,
    timestamp,
)
from ..ui_helpers import ensure_requester_action_allowed
from ..utils import parse_duration
from .checks import head_admin_check

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot

logger = logging.getLogger(__name__)

TOP_SIZE = 10
MAX_REASON = 200
MAX_PRIZE_VALUE = 200
MAX_NOTE = 300


def parse_end_at(text: str) -> datetime:
    """`2026-07-01` or `2026-07-01 18:00`, read as UTC."""
    value = text.strip()
    for pattern in ("%Y-%m-%d %H:%M", "%Y-%m-%dT%H:%M", "%Y-%m-%d"):
        try:
            return datetime.strptime(value, pattern).replace(tzinfo=UTC)
        except ValueError:
            continue
    raise ValueError("Use a date like 2026-07-01 or 2026-07-01 18:00 (UTC).")


def parse_places(text: str) -> tuple[int, int]:
    """`1` or `1-3`."""
    value = text.strip()
    try:
        first, _, last = value.partition("-")
        place_from = int(first)
        place_to = int(last) if last else place_from
    except ValueError:
        place_from = place_to = 0
    if place_from < 1 or place_to < place_from:
        raise ValueError("Use a place like 1, or a range like 1-3.")
    return place_from, place_to


async def _autocomplete_badge(
    interaction: Interaction, current: str
) -> list[app_commands.Choice[str]]:
    """Badge ids for a `badge` prize; a `custom` prize is free text and gets no suggestions."""
    if getattr(interaction.namespace, "kind", None) != "badge":
        return []
    needle = current.strip().lower()
    return [
        app_commands.Choice(name=badge_choice_label(badge), value=badge.id)
        for badge in grantable_badges()
        if not needle or needle in badge.id or needle in badge.label.lower()
    ][:25]


async def _autocomplete_ladder(
    interaction: Interaction, current: str
) -> list[app_commands.Choice[str]]:
    bot = interaction.client
    try:
        known = await bot.container.ratings.ladders()
    except Exception:
        logger.exception("Cannot list ladders for autocomplete")
        known = []
    ladders = sorted(set(known) | set(LADDER_NAMES))
    needle = current.strip().lower()
    return [
        app_commands.Choice(name=ladder_name(ladder), value=ladder)
        for ladder in ladders
        if not needle or needle in ladder.lower() or needle in ladder_name(ladder).lower()
    ][:25]


class SeasonEndConfirmView(discord.ui.View):
    def __init__(self, cog: SeasonsCog, *, requester_id: int, ladder: str, reason: str | None):
        super().__init__(timeout=60)
        self._cog = cog
        self._requester_id = requester_id
        self._ladder = ladder
        self._reason = reason
        self.message: discord.Message | None = None

    @discord.ui.button(label="End season now", style=discord.ButtonStyle.danger)
    async def _confirm(self, interaction: Interaction, button: discord.ui.Button) -> None:
        if not await ensure_requester_action_allowed(
            interaction,
            requester_id=self._requester_id,
            denied_message="Only the admin who ran the command can confirm this.",
        ):
            return
        self.stop()
        await interaction.response.edit_message(content="Ending the season…", view=None)
        message = await self._cog.run_admin(
            interaction,
            lambda ratings: ratings.end_season_now(
                ladder=self._ladder,
                discord_id=str(interaction.user.id),
                actor_name=interaction.user.display_name,
                reason=self._reason,
            ),
            describe=lambda response: (
                f"**{ladder_name(self._ladder)}**: season "
                f"`{response.season.season}` was ended."
            ),
        )
        await interaction.edit_original_response(content=message)

    @discord.ui.button(label="Cancel", style=discord.ButtonStyle.secondary)
    async def _cancel(self, interaction: Interaction, button: discord.ui.Button) -> None:
        if not await ensure_requester_action_allowed(
            interaction,
            requester_id=self._requester_id,
            denied_message="Only the admin who ran the command can cancel this.",
        ):
            return
        self.stop()
        await interaction.response.edit_message(
            content="Cancelled. The season was not changed.", view=None
        )

    async def on_timeout(self) -> None:
        if self.message is not None:
            try:
                await self.message.edit(content="Timed out. The season was not changed.", view=None)
            except discord.HTTPException:
                pass


class SeasonsCog(commands.Cog):
    season_group = app_commands.Group(
        name="season",
        description="Rating seasons",
    )

    prize_group = app_commands.Group(
        name="prize",
        description="Season prizes (head admin)",
        parent=season_group,
    )

    def __init__(self, bot: XCoreDiscordBot) -> None:
        self.bot = bot

    # ----------------------------------------------------------------- reading

    @season_group.command(name="info", description="Show the current rating season")
    @app_commands.describe(ladder="Ladder (all of them when omitted)")
    @app_commands.autocomplete(ladder=_autocomplete_ladder)
    async def cmd_info(self, interaction: Interaction, ladder: str | None = None) -> None:
        # The reads below can outlast Discord's three seconds for a first answer.
        await interaction.response.defer(thinking=True)
        ratings = self.bot.container.ratings
        ladders = [ladder] if ladder else await ratings.ladders()
        now = datetime.now(UTC)
        embeds: list[discord.Embed] = []
        for ladder_id in ladders:
            season = await ratings.current_season(ladder_id)
            if season is None:
                continue
            embeds.append(
                build_season_info_embed(
                    season,
                    participants=await ratings.count(ladder_id, season.number),
                    now=now,
                )
            )
        if not embeds:
            await interaction.followup.send("No rating seasons yet.")
            return
        await interaction.followup.send(embeds=embeds[:10])

    @season_group.command(name="top", description="Show a season's leaderboard")
    @app_commands.describe(
        ladder="Ladder", season="Season number (the current one when omitted)"
    )
    @app_commands.autocomplete(ladder=_autocomplete_ladder)
    async def cmd_top(
        self,
        interaction: Interaction,
        ladder: str,
        season: app_commands.Range[int, 1] | None = None,
    ) -> None:
        await interaction.response.defer(thinking=True)
        ratings = self.bot.container.ratings
        found = (
            await ratings.find_season(ladder, season)
            if season is not None
            else await ratings.current_season(ladder)
        )
        if found is None:
            await interaction.followup.send(f"No such season for {ladder_name(ladder)}.")
            return
        standings = await ratings.top(ladder, found.number, TOP_SIZE)
        embed = build_season_top_embed(
            found,
            standings,
            participants=await ratings.count(ladder, found.number),
        )
        await interaction.followup.send(embed=embed)

    # ---------------------------------------------------------- administration

    async def run_admin(self, interaction: Interaction, call, *, describe) -> str:
        """Runs a season change on a game server and words the result for the admin."""
        try:
            response = await call(self.bot.container.rating_service)
        except RpcRejected as error:
            return f"❌ The server refused: {error.error_message}"
        except NoLiveServerError:
            return "❌ No Mindustry server is online to carry this out."
        except TimeoutError:
            return "❌ The servers did not answer in time. Check `/season info` before retrying."
        except Exception:
            logger.exception("Season administration failed")
            return "❌ Something went wrong. Check the logs."
        return "✅ " + describe(response)

    async def _admin_reply(self, interaction: Interaction, call, *, describe) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        message = await self.run_admin(interaction, call, describe=describe)
        await interaction.followup.send(message, ephemeral=True)

    @staticmethod
    def _clean_reason(reason: str | None) -> str | None:
        return (reason or "").strip()[:MAX_REASON] or None

    @season_group.command(name="extend", description="Move the season end later (head admin)")
    @app_commands.describe(
        ladder="Ladder",
        duration="How much later, e.g. 3d, 2w, 12h",
        reason="Why (shown in the announcement)",
    )
    @app_commands.autocomplete(ladder=_autocomplete_ladder)
    @head_admin_check()
    async def cmd_extend(
        self,
        interaction: Interaction,
        ladder: str,
        duration: str,
        reason: str | None = None,
    ) -> None:
        try:
            by = parse_duration(duration, default_unit="d")
        except ValueError as error:
            await interaction.response.send_message(str(error), ephemeral=True)
            return
        await self._admin_reply(
            interaction,
            lambda ratings: ratings.extend_season(
                ladder=ladder,
                by=by,
                discord_id=str(interaction.user.id),
                actor_name=interaction.user.display_name,
                reason=self._clean_reason(reason),
            ),
            describe=lambda response: self._describe_moved(ladder, response),
        )

    @season_group.command(name="end-at", description="Set the exact season end (head admin)")
    @app_commands.describe(
        ladder="Ladder",
        when="UTC date and time, e.g. 2026-07-01 or 2026-07-01 18:00",
        reason="Why (shown in the announcement)",
    )
    @app_commands.autocomplete(ladder=_autocomplete_ladder)
    @head_admin_check()
    async def cmd_end_at(
        self,
        interaction: Interaction,
        ladder: str,
        when: str,
        reason: str | None = None,
    ) -> None:
        try:
            ends_at = parse_end_at(when)
        except ValueError as error:
            await interaction.response.send_message(str(error), ephemeral=True)
            return
        await self._admin_reply(
            interaction,
            lambda ratings: ratings.set_season_end(
                ladder=ladder,
                ends_at=ends_at,
                discord_id=str(interaction.user.id),
                actor_name=interaction.user.display_name,
                reason=self._clean_reason(reason),
            ),
            describe=lambda response: self._describe_moved(ladder, response),
        )

    @season_group.command(name="end-now", description="End the season right now (head admin)")
    @app_commands.describe(ladder="Ladder", reason="Why (shown in the announcement)")
    @app_commands.autocomplete(ladder=_autocomplete_ladder)
    @head_admin_check()
    async def cmd_end_now(
        self, interaction: Interaction, ladder: str, reason: str | None = None
    ) -> None:
        view = SeasonEndConfirmView(
            self,
            requester_id=interaction.user.id,
            ladder=ladder,
            reason=self._clean_reason(reason),
        )
        await interaction.response.send_message(
            f"End **{ladder_name(ladder)}** now? The season is archived, the podium is "
            "announced and the next season starts straight away.",
            view=view,
            ephemeral=True,
        )
        view.message = await interaction.original_response()

    # ------------------------------------------------------------------ prizes

    @prize_group.command(name="set", description="Add a prize to the running season")
    @app_commands.describe(
        ladder="Ladder",
        places="A place or a range, e.g. 1 or 1-3",
        kind="badge unlocks a badge by itself; custom is handed over by an admin",
        value="Badge id, or the prize itself (e.g. Discord Nitro, 1 month)",
        description="Text shown to players instead of the value",
    )
    @app_commands.autocomplete(ladder=_autocomplete_ladder, value=_autocomplete_badge)
    @head_admin_check()
    async def cmd_prize_set(
        self,
        interaction: Interaction,
        ladder: str,
        places: str,
        kind: Literal["badge", "custom"],
        value: app_commands.Range[str, 1, MAX_PRIZE_VALUE],
        description: app_commands.Range[str, 1, MAX_PRIZE_VALUE] | None = None,
    ) -> None:
        try:
            place_from, place_to = parse_places(places)
        except ValueError as error:
            await interaction.response.send_message(str(error), ephemeral=True)
            return
        await self._admin_reply(
            interaction,
            lambda ratings: ratings.add_prize(
                ladder=ladder,
                place_from=place_from,
                place_to=place_to,
                kind=SeasonPrizeV1Kind(kind),
                value=value.strip(),
                description=(description or "").strip() or None,
                discord_id=str(interaction.user.id),
                actor_name=interaction.user.display_name,
            ),
            describe=lambda response: (
                f"Prize added. **{ladder_name(ladder)}** now has "
                f"{len(response.prizes)} prize(s) this season."
            ),
        )

    @prize_group.command(name="clear", description="Remove prizes from the running season")
    @app_commands.describe(ladder="Ladder", places="A place or a range, e.g. 1 or 1-3")
    @app_commands.autocomplete(ladder=_autocomplete_ladder)
    @head_admin_check()
    async def cmd_prize_clear(
        self, interaction: Interaction, ladder: str, places: str
    ) -> None:
        try:
            place_from, place_to = parse_places(places)
        except ValueError as error:
            await interaction.response.send_message(str(error), ephemeral=True)
            return
        await self._admin_reply(
            interaction,
            lambda ratings: ratings.clear_prizes(
                ladder=ladder,
                place_from=place_from,
                place_to=place_to,
                discord_id=str(interaction.user.id),
                actor_name=interaction.user.display_name,
            ),
            describe=lambda response: (
                f"Prizes within places {places.strip()} removed. "
                f"**{ladder_name(ladder)}** has {len(response.prizes)} prize(s) left."
            ),
        )

    @prize_group.command(name="list", description="Show a season's prizes and who got them")
    @app_commands.describe(
        ladder="Ladder", season="Season number (the current one when omitted)"
    )
    @app_commands.autocomplete(ladder=_autocomplete_ladder)
    @head_admin_check()
    async def cmd_prize_list(
        self,
        interaction: Interaction,
        ladder: str,
        season: app_commands.Range[int, 1] | None = None,
    ) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        ratings = self.bot.container.ratings
        found = (
            await ratings.find_season(ladder, season)
            if season is not None
            else await ratings.current_season(ladder)
        )
        if found is None:
            await interaction.followup.send(
                f"No such season for {ladder_name(ladder)}.", ephemeral=True
            )
            return
        grants = await ratings.prize_grants(ladder, found.number)
        names = await ratings.nicknames([grant.player_uuid for grant in grants])
        await interaction.followup.send(
            embed=build_season_prizes_embed(found, grants, names=names), ephemeral=True
        )

    @prize_group.command(
        name="delivered", description="Record that a place's prizes were handed over"
    )
    @app_commands.describe(
        ladder="Ladder",
        season="Season number",
        place="Place on the podium",
        note="For example, how it was sent",
        player="PID of the winner, to settle only their prize (default: the whole place)",
    )
    @app_commands.autocomplete(ladder=_autocomplete_ladder)
    @head_admin_check()
    async def cmd_prize_delivered(
        self,
        interaction: Interaction,
        ladder: str,
        season: app_commands.Range[int, 1],
        place: app_commands.Range[int, 1],
        note: app_commands.Range[str, 1, MAX_NOTE] | None = None,
        player: app_commands.Range[int, 1] | None = None,
    ) -> None:
        await self._admin_reply(
            interaction,
            lambda ratings: ratings.mark_prize_delivered(
                ladder=ladder,
                season=season,
                place=place,
                discord_id=str(interaction.user.id),
                actor_name=interaction.user.display_name,
                note=(note or "").strip() or None,
                player_pid=player,
            ),
            describe=lambda response: (
                f"Marked {response.updated} prize(s) of place {place}"
                f"{f' for player #{player}' if player else ''} in "
                f"**{ladder_name(ladder)}** season {season} as delivered."
            ),
        )

    @staticmethod
    def _describe_moved(ladder: str, response) -> str:
        ref = response.season
        ends_at = datetime.fromisoformat(ref.endsAt)
        title = season_title(ladder, ref.name, ref.season)
        if response.ended:
            return f"**{title}** has ended."
        return f"**{title}** now ends {timestamp(ends_at)} ({timestamp(ends_at, 'R')})."
