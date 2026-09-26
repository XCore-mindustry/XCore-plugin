from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING

from .daemons.admin_sync_daemon import AdminSyncDaemon
from .daemons.presence_daemon import PresenceDaemon
from .daemons.stream_supervisor import StreamSupervisor
from .mongo_store import MongoStore
from .redis_bus import RedisBus
from .rpc.mindustry_rpc import MindustryRpcClient
from .services.map_service import MapService
from .services.moderation_service import ModerationService
from .services.player_service import PlayerService
from .settings import Settings

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


@dataclass(slots=True)
class ServiceContainer:
    settings: Settings
    store: MongoStore
    bus: RedisBus
    rpc: MindustryRpcClient
    players: PlayerService
    moderation: ModerationService
    maps: MapService
    presence_daemon: PresenceDaemon
    admin_sync_daemon: AdminSyncDaemon
    stream_supervisor: StreamSupervisor

    @classmethod
    def create(
        cls,
        settings: Settings,
        bot: XCoreDiscordBot,
        *,
        store: MongoStore | None = None,
        bus: RedisBus | None = None,
    ) -> ServiceContainer:
        bus = bus if bus is not None else RedisBus(settings)
        store = store if store is not None else MongoStore(settings)
        rpc = MindustryRpcClient(bus)

        players = PlayerService(store=store, bus=bus)
        moderation = ModerationService(store=store, bus=bus)
        maps = MapService(rpc=rpc)

        presence_daemon = PresenceDaemon(bot=bot)
        admin_sync_daemon = AdminSyncDaemon(
            bot=bot, store=store, bus=bus, settings=settings
        )
        stream_supervisor = StreamSupervisor(bot=bot)

        return cls(
            settings=settings,
            store=store,
            bus=bus,
            rpc=rpc,
            players=players,
            moderation=moderation,
            maps=maps,
            presence_daemon=presence_daemon,
            admin_sync_daemon=admin_sync_daemon,
            stream_supervisor=stream_supervisor,
        )
