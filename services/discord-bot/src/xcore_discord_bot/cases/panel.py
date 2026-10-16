from __future__ import annotations

import asyncio
import hashlib
import json

import discord

from ..i18n import SUPPORTED, t
from .views import panel_view


class LanguageSelect(discord.ui.Select):
    def __init__(self, store, requester_id: int, locale: str) -> None:
        super().__init__(
            placeholder=t(locale, "language-select-placeholder"),
            options=[
                discord.SelectOption(
                    label=t(locale, "language-name-" + code), value=code
                )
                for code in SUPPORTED
            ],
        )
        self.store, self.requester_id = store, requester_id

    async def callback(self, interaction: discord.Interaction) -> None:
        locale = self.store.interaction_locale_now(interaction)
        if interaction.user.id != self.requester_id:
            await interaction.response.send_message(
                t(locale, "error-not-allowed"), ephemeral=True
            )
            return
        await interaction.response.defer()
        selected = self.values[0]
        await self.store.set_locale_pref(interaction.user.id, selected, source="manual")
        await interaction.edit_original_response(
            content=t(
                selected,
                "language-changed",
                language=t(selected, "language-name-" + selected),
            ),
            view=None,
        )
        self.view.stop()


class LanguageView(discord.ui.View):
    def __init__(self, store, requester_id: int, locale: str) -> None:
        super().__init__(timeout=60)
        self.add_item(LanguageSelect(store, requester_id, locale))


class CasePanel:
    """The one public message: the short text in every language and a button per flow."""

    def __init__(self, service, bot) -> None:
        self.service, self.bot = service, bot
        self._lock = asyncio.Lock()

    def render(self):
        content = "\n\n".join(t(locale, "cases-panel-text") for locale in SUPPORTED)
        view = panel_view(self.service.registry)
        fingerprint = json.dumps(
            {
                "content": content,
                "buttons": [
                    (item.item.custom_id, item.item.label, str(item.item.emoji))
                    for item in view.children
                ],
            },
            sort_keys=True,
        )
        return content, view, hashlib.sha256(fingerprint.encode()).hexdigest()

    async def refresh(self) -> bool:
        """Edit a published panel in place when flags or texts changed. No Discord call
        otherwise, and nothing is published that an administrator did not publish."""
        stored = await self.service.store.get_panel()
        channel_id = self.service.settings.cases_panel_channel_id
        if (
            not stored
            or not channel_id
            or stored["channel_id"] == str(channel_id)
            and stored["rendered_hash"] == self.render()[2]
        ):
            return False
        await self.publish()
        return True

    async def _find(self, channel, content: str):
        """A panel whose id was never stored: the send succeeded and the write did not."""
        async for candidate in channel.history(limit=100):
            if candidate.author.id != self.bot.user.id:
                continue
            ids = [
                getattr(component, "custom_id", None)
                for row in candidate.components
                for component in getattr(row, "children", ())
            ]
            if candidate.content == content or any(
                value and value.startswith("xc:1:panel:") for value in ids
            ):
                return candidate
        return None

    async def publish(self) -> int:
        async with self._lock:
            channel_id = self.service.settings.cases_panel_channel_id
            if not channel_id:
                raise ValueError("CASES_PANEL_CHANNEL_ID is not configured")
            channel = await self.service.threads.channel(channel_id)
            if channel.type != discord.ChannelType.text:
                raise ValueError("CASES_PANEL_CHANNEL_ID must be a text channel")
            content, view, rendered_hash = self.render()
            stored = await self.service.store.get_panel()
            if stored and stored["channel_id"] != str(channel_id):
                # Keep the old message and its history, but remove its interactive projection.
                try:
                    old_channel = await self.service.threads.channel(
                        stored["channel_id"]
                    )
                    old = await old_channel.fetch_message(int(stored["message_id"]))
                    await old.edit(view=None)
                except discord.NotFound:
                    pass
                stored = None
            message = None
            if stored:
                try:
                    message = await channel.fetch_message(int(stored["message_id"]))
                except discord.NotFound:
                    pass
            if message is None:
                message = await self._find(channel, content)
                stored = None
            if message is None:
                message = await channel.send(
                    content, view=view, allowed_mentions=discord.AllowedMentions.none()
                )
            elif not stored or stored["rendered_hash"] != rendered_hash:
                await message.edit(
                    content=content,
                    view=view,
                    allowed_mentions=discord.AllowedMentions.none(),
                )
            await self.service.store.save_panel(
                channel_id=str(channel_id),
                message_id=str(message.id),
                rendered_hash=rendered_hash,
            )
            return message.id
