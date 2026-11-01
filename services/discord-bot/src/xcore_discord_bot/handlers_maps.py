from __future__ import annotations

import logging
import secrets
from typing import TYPE_CHECKING

import discord
from discord import Interaction

from .command_helpers import claim_mutation, reply_locale
from .i18n import t
from .map_list import map_list_pages, map_text
from .moderation_views import MapRemoveConfirmView
from .server_views import MapsListView
from .staff_actions import (
    Actor,
    InvalidMapUpload,
    MapUploadFailed,
    MapUploadFile,
    MapUploadTimeout,
    perform_map_upload,
    prepare_map_upload,
)

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


logger = logging.getLogger(__name__)


async def cmd_maps(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    server: str,
    creator: discord.abc.User | None = None,
) -> None:
    locale = reply_locale(bot, interaction)
    await interaction.response.defer()
    try:
        maps = await bot.container.rpc.list_maps(
            server=server, timeout_ms=bot.settings.rpc_timeout_ms
        )
    except TimeoutError:
        await interaction.followup.send(t(locale, "maps-list-timeout"))
        return

    if creator is not None:
        maps = [
            item for item in maps if item.get("creator_discord_id") == str(creator.id)
        ]
    if not maps:
        await interaction.followup.send(
            t(
                locale,
                "maps-list-empty-creator" if creator else "maps-list-empty",
                server=server,
            )
        )
        return

    default_sort_mode = "reputation"

    async def fetch_page(page: int, sort_mode: str) -> tuple[discord.Embed, bool]:
        pages = map_list_pages(maps, sort_mode, locale)
        chunk = pages[page]
        embed = discord.Embed(
            title=t(locale, "maps-list-title", server=server),
            description="\n".join(chunk),
            color=discord.Color.green(),
        )
        footer = t(
            locale,
            "maps-list-footer",
            sort=t(locale, f"maps-list-sort-{sort_mode}"),
            page=page + 1,
            pages=len(pages),
            total=len(maps),
            shown=len(chunk),
        )
        if any(item.get("uploaded_by_discord_id") for item in maps):
            footer += " • " + t(locale, "maps-list-legend")
        embed.set_footer(text=footer)
        return embed, page + 1 < len(pages)

    first_embed, has_next = await fetch_page(0, default_sort_mode)
    view = MapsListView(
        page=0,
        has_prev=False,
        has_next=has_next,
        sort_mode=default_sort_mode,
        fetch_page=fetch_page,
        locale=locale,
    )
    sent = await interaction.followup.send(embed=first_embed, view=view)
    view.bot_message = sent


async def cmd_remove_map(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    server: str,
    file_name: str,
) -> None:
    normalized = file_name.strip()
    if not normalized:
        await interaction.response.send_message(
            "Map file name must not be empty.", ephemeral=True
        )
        return

    request_nonce = secrets.token_hex(6)
    view = MapRemoveConfirmView(
        requester_id=interaction.user.id,
        server=server,
        file_name=normalized,
        request_nonce=request_nonce,
        perform_remove_map=lambda **kwargs: perform_remove_map(bot, **kwargs),
    )
    await interaction.response.send_message(
        f"Are you sure you want to remove map file `{normalized}` from `{server}`?",
        view=view,
    )
    view.message = await interaction.original_response()


async def perform_remove_map(
    bot: XCoreDiscordBot,
    *,
    server: str,
    file_name: str,
    request_nonce: str,
) -> str:
    claim_key = f"remove-map:{server}:{file_name}:{request_nonce}"
    if not await bot.bus.claim_idempotency(claim_key, ttl_seconds=600):
        return "This map removal was already processed."

    try:
        result = await bot.container.rpc.remove_map(
            server=server,
            file_name=file_name,
            timeout_ms=bot.settings.rpc_timeout_ms,
        )
    except TimeoutError:
        return "No response from target server (timeout)."

    return f"🗑️ `{server}` remove-map `{file_name}`: {result}"


async def cmd_upload_map(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    server: str,
    attachments: list[discord.Attachment | None],
    creator: discord.abc.User | None = None,
    creators: list[discord.abc.User | None] | None = None,
) -> None:
    def identity(user: discord.abc.User) -> Actor:
        return Actor(
            str(user.id),
            user.display_name or user.name,
            handle=getattr(user, "name", None),
        )

    uploader = identity(interaction.user)
    files = [
        MapUploadFile(
            attachment.url,
            attachment.filename,
            creator=identity(creators[index])
            if creators and index < len(creators) and creators[index] is not None
            else None,
        )
        for index, attachment in enumerate(attachments)
        if attachment is not None and attachment.filename.lower().endswith(".msav")
    ]
    creator_actor = identity(creator) if creator is not None else None
    try:
        prepare_map_upload(server, files, uploader, creator_actor)
    except InvalidMapUpload as error:
        await interaction.response.send_message(str(error), ephemeral=True)
        return
    if not await claim_mutation(
        bot, interaction, operation="upload-map", scope=f"{server}:{len(files)}"
    ):
        return

    await interaction.response.send_message(
        f"Uploading {len(files)} map(s) to `{map_text(server, inline_code=True)}`…",
        allowed_mentions=discord.AllowedMentions.none(),
    )
    try:
        response = await perform_map_upload(bot, server, files, uploader, creator_actor)
    except MapUploadTimeout as error:
        await interaction.edit_original_response(
            content=str(error),
            allowed_mentions=discord.AllowedMentions.none(),
        )
        return
    except MapUploadFailed as error:
        logger.exception("Map upload confirmation failed for %s", server)
        await interaction.edit_original_response(
            content=str(error),
            allowed_mentions=discord.AllowedMentions.none(),
        )
        return
    labels = {
        "installed": "✅ Installed; attribution saved",
        "download_failed": "❌ Download or file write failed",
        "invalid_map": "❌ Invalid map file",
        "superseded": "⚠️ Replaced by a newer upload",
        "metadata_failed": "⚠️ Installed; attribution was not saved",
        "rejected": "❌ Rejected filename",
    }
    rows = [f"Maps on `{map_text(server, inline_code=True)}`:"]
    for result in response.results:
        rows.append(
            f"{labels[result.status.value]} — `{map_text(result.fileName, 100, inline_code=True)}`"
        )
    await interaction.edit_original_response(
        content="\n".join(rows), allowed_mentions=discord.AllowedMentions.none()
    )
