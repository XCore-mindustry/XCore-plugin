from datetime import UTC, datetime
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from backends import connected_store
from bot_fakes import make_bot

from xcore_discord_bot.cogs.info import InfoCog
from xcore_discord_bot.server_views import PaginatorView


@pytest.mark.parametrize(
    ("mode", "title", "record_index", "entry_text"),
    [
        (
            "target",
            "History",
            0,
            "Actor: `Moderator`\nReason: `Incoming sanction`",
        ),
        (
            "actor",
            "Actions",
            1,
            "Target: `Other`\nReason: `Outgoing sanction`",
        ),
    ],
)
async def test_audit_slash_command_renders_matching_history(
    mode: str, title: str, record_index: int, entry_text: str
) -> None:
    store, db = await connected_store()
    bot = make_bot(store=store)
    await db["players"].insert_one(
        {"pid": 123, "nickname": "Vortex", "uuid": "uuid-123", "discord_id": "77"}
    )
    audit_ids = []
    for pid, name, actor_id, actor_name, reason in [
        (123, "Vortex", "88", "Moderator", "Incoming sanction"),
        (456, "Other", "77", "Former nickname", "Outgoing sanction"),
        (789, "Vortex", "99", "Vortex", "Unrelated sanction"),
    ]:
        audit_ids.append(
            await bot.container.audit.append_moderation_audit(
                action="BAN",
                target_uuid=f"uuid-{pid}",
                target_pid=pid,
                target_name=name,
                target_ip=None,
                actor_discord_id=actor_id,
                actor_name=actor_name,
                reason=reason,
                occurred_at=datetime(2026, 1, 1, tzinfo=UTC),
            )
        )
    interaction = SimpleNamespace(
        response=SimpleNamespace(send_message=AsyncMock()),
        original_response=AsyncMock(),
    )
    cog = InfoCog(bot)

    await getattr(cog, f"cmd_audit_{mode}").callback(cog, interaction, 123)

    interaction.response.send_message.assert_awaited_once()
    sent = interaction.response.send_message.await_args.kwargs
    assert sent["ephemeral"] is True
    assert isinstance(sent["view"], PaginatorView)
    embed = sent["embed"]
    assert embed.title == f"{title} • Vortex"
    (entry,) = embed.fields
    assert entry.value == f"{entry_text}\nAudit ID: `{audit_ids[record_index]}`"
    assert embed.footer.text == "Page 1/1 • total entries: 1 • entries on page: 1"
