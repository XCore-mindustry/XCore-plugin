import logging
from unittest.mock import AsyncMock, MagicMock

import pytest
from bot_fakes import make_bot

from xcore_discord_bot.daemons.admin_sync_daemon import AdminReconcileResult
from xcore_discord_bot.daemons.staff_sync_daemon import StaffSyncResult


async def test_constructor_and_close_use_the_injected_dependencies():
    bot = make_bot()
    assert bot.container.settings is bot.settings
    bot.store.connect.assert_not_called()
    bot.bus.connect.assert_not_called()

    await bot.close()

    bot.store.close.assert_awaited_once()
    bot.bus.close.assert_awaited_once()
    assert bot.is_closed()


@pytest.mark.parametrize("staff_sync", [False, True])
async def test_setup_and_ready_use_the_same_reconcile_daemon(staff_sync, caplog):
    caplog.set_level(logging.INFO)
    bot = make_bot()
    container = bot.container
    container.admin_sync_daemon.start = MagicMock()
    container.admin_sync_daemon.reconcile = AsyncMock(
        return_value=AdminReconcileResult(discord_admins=1)
    )
    if staff_sync:
        container.staff_sync_daemon = MagicMock()
        container.staff_sync_daemon.reconcile = AsyncMock(
            return_value=StaffSyncResult(1, 0)
        )
        container.staff_sync_daemon.stop = AsyncMock()
    container.punishments.ensure_indexes = AsyncMock()
    container.presence_daemon.start = MagicMock()
    container.rating_merge_daemon.start = MagicMock()
    container.stream_supervisor.start = MagicMock()
    bot._sync_application_commands = AsyncMock()
    bot.change_presence = AsyncMock()

    try:
        await bot.setup_hook()
        await bot.on_ready()

        bot.store.connect.assert_awaited_once()
        bot.bus.connect.assert_awaited_once()
        container.punishments.ensure_indexes.assert_awaited_once()
        bot.change_presence.assert_awaited_once()
        bot._sync_application_commands.assert_awaited_once()
        if staff_sync:
            container.staff_sync_daemon.start.assert_called_once()
            container.staff_sync_daemon.reconcile.assert_awaited_once()
            container.admin_sync_daemon.start.assert_not_called()
            container.admin_sync_daemon.reconcile.assert_not_awaited()
        else:
            container.admin_sync_daemon.start.assert_called_once()
            container.admin_sync_daemon.reconcile.assert_awaited_once()
            assert "applied=0 revoked=0 discord_admins=1" in caplog.text
        assert "Startup admin reconcile failed" not in caplog.text
    finally:
        await bot.close()
