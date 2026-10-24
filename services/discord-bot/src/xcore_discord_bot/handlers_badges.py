from __future__ import annotations

from typing import TYPE_CHECKING

from discord import Interaction

from .badges import get_badge
from .command_helpers import claim_mutation, get_player_or_reply, require_player_uuid
from .dto import Actor
from .staff_actions import StaffActionError, perform_badge_grant

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


async def cmd_badge_grant(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
    badge_id: str,
) -> None:
    await _cmd_badge_change(
        bot,
        interaction,
        player_id=player_id,
        badge_id=badge_id,
        grant=True,
    )


async def cmd_badge_revoke(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
    badge_id: str,
) -> None:
    await _cmd_badge_change(
        bot,
        interaction,
        player_id=player_id,
        badge_id=badge_id,
        grant=False,
    )


async def _cmd_badge_change(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    *,
    player_id: int,
    badge_id: str,
    grant: bool,
) -> None:
    player = await get_player_or_reply(bot, interaction, player_id)
    if player is None:
        return

    operation = "badge-grant" if grant else "badge-revoke"
    if not await claim_mutation(
        bot,
        interaction,
        operation=operation,
        scope=f"{player_id}:{badge_id.strip().lower()}",
    ):
        return

    uuid_value = await require_player_uuid(
        interaction,
        player,
        action="change badge",
    )
    if uuid_value is None:
        return

    if grant:
        try:
            result = await perform_badge_grant(
                bot,
                player,
                badge_id,
                Actor(
                    str(getattr(interaction.user, "id", "")) or None,
                    interaction.user.display_name,
                ),
            )
        except StaffActionError as error:
            await interaction.response.send_message(str(error), ephemeral=True)
            return
        await interaction.response.send_message(result.message)
        return

    badge = get_badge(badge_id)
    if badge is None:
        await interaction.response.send_message(
            f"Badge `{badge_id.strip() or badge_id}` was not found.",
            ephemeral=True,
        )
        return

    if badge.system or not badge.grantable:
        await interaction.response.send_message(
            f"Badge `{badge.id}` cannot be granted or revoked manually.",
            ephemeral=True,
        )
        return

    changed = await bot.store.revoke_badge(uuid=uuid_value, badge_id=badge.id)

    if changed:
        updated_player = await bot.store.find_player_by_uuid(uuid_value)
        if updated_player is not None:
            await bot.bus.publish_player_badge_inventory_changed(
                uuid_value=uuid_value,
                active_badge=updated_player.active_badge,
                unlocked_badges=updated_player.unlocked_badges,
            )

    player_name = player.nickname
    message = (
        f"Revoked badge `{badge.id}` from `{player_name}`"
        if changed
        else f"Player does not have badge `{badge.id}`"
    )

    await interaction.response.send_message(message)
