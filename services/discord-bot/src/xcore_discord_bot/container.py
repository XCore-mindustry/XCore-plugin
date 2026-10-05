from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING

from .daemons.admin_sync_daemon import AdminSyncDaemon
from .daemons.presence_daemon import PresenceDaemon
from .daemons.rating_merge_daemon import RatingMergeDaemon
from .daemons.stream_supervisor import StreamSupervisor
from .game_stats import GameStatsStore
from .mongo_store import MongoStore
from .rating_store import RatingStore
from .redis_bus import RedisBus
from .rpc.mindustry_rpc import MindustryRpcClient
from .services.map_service import MapService
from .services.moderation_service import ModerationService
from .services.player_service import PlayerService
from .services.rating_service import RatingService
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
    ratings: RatingStore
    game_stats: GameStatsStore
    rating_service: RatingService
    presence_daemon: PresenceDaemon
    admin_sync_daemon: AdminSyncDaemon
    rating_merge_daemon: RatingMergeDaemon
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

        ratings = RatingStore(store)
        rating_service = RatingService(
            store=ratings, rpc=rpc, timeout_ms=settings.rpc_timeout_ms
        )
        players = PlayerService(store=store, bus=bus, ratings=rating_service)
        moderation = ModerationService(store=store, bus=bus)
        maps = MapService(rpc=rpc)

        presence_daemon = PresenceDaemon(bot=bot)
        admin_sync_daemon = AdminSyncDaemon(
            bot=bot, store=store, bus=bus, settings=settings
        )
        rating_merge_daemon = RatingMergeDaemon(bot=bot, ratings=rating_service)
        stream_supervisor = StreamSupervisor(bot=bot)

        return cls(
            settings=settings,
            store=store,
            bus=bus,
            rpc=rpc,
            players=players,
            moderation=moderation,
            maps=maps,
            ratings=ratings,
            game_stats=GameStatsStore(store),
            rating_service=rating_service,
            presence_daemon=presence_daemon,
            admin_sync_daemon=admin_sync_daemon,
            rating_merge_daemon=rating_merge_daemon,
            stream_supervisor=stream_supervisor,
        )
