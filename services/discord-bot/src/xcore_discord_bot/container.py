from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING

from .bus.accounts import AccountsBus
from .bus.cases import CasesBus
from .bus.chat import ChatBus
from .bus.maps import MapsBus
from .bus.moderation import ModerationBus
from .bus.rating import RatingBus
from .bus.security import SecurityBus
from .bus.sentinel import SentinelBus
from .cases import flows  # noqa: F401 -- importing modules registers flow definitions
from .cases.notify import CaseNotifier
from .cases.panel import CasePanel
from .cases.registry import flow_registry
from .cases.service import CaseService
from .cases.store import CaseStore
from .cases.threads import CaseThreads
from .daemons.admin_sync_daemon import AdminSyncDaemon
from .daemons.cases_reconcile_daemon import CasesReconcileDaemon
from .daemons.presence_daemon import PresenceDaemon
from .daemons.rating_merge_daemon import RatingMergeDaemon
from .daemons.staff_sync_daemon import StaffSyncDaemon
from .daemons.stream_supervisor import StreamSupervisor
from .game_stats import GameStatsStore
from .mongo_store import MongoStore
from .rating_store import RatingStore
from .redis_bus import RedisBus
from .rpc.mindustry_rpc import MindustryRpcClient
from .services.player_service import PlayerService
from .services.rating_service import RatingService
from .settings import Settings

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


@dataclass(slots=True)
class ServiceContainer:
    """The services built on the bot's store and bus; the bot owns those two itself."""

    settings: Settings
    accounts_bus: AccountsBus
    cases_bus: CasesBus
    chat_bus: ChatBus
    maps_bus: MapsBus
    moderation_bus: ModerationBus
    rating_bus: RatingBus
    security_bus: SecurityBus
    sentinel_bus: SentinelBus
    rpc: MindustryRpcClient
    players: PlayerService
    ratings: RatingStore
    game_stats: GameStatsStore
    rating_service: RatingService
    presence_daemon: PresenceDaemon
    admin_sync_daemon: AdminSyncDaemon
    rating_merge_daemon: RatingMergeDaemon
    stream_supervisor: StreamSupervisor
    cases: CaseService
    case_panel: CasePanel
    cases_reconcile_daemon: CasesReconcileDaemon
    # Only roles mode syncs game roles; legacy mode reconciles admins instead.
    staff_sync_daemon: StaffSyncDaemon | None = None

    @classmethod
    def create(
        cls,
        settings: Settings,
        bot: XCoreDiscordBot,
        *,
        store: MongoStore,
        bus: RedisBus,
    ) -> ServiceContainer:
        accounts_bus = AccountsBus(bus)
        cases_bus = CasesBus(bus)
        chat_bus = ChatBus(bus)
        maps_bus = MapsBus(bus)
        moderation_bus = ModerationBus(bus)
        rating_bus = RatingBus(bus)
        security_bus = SecurityBus(bus)
        sentinel_bus = SentinelBus(bus)
        rpc = MindustryRpcClient(
            cases=cases_bus,
            maps=maps_bus,
            rating=rating_bus,
            security=security_bus,
            sentinel=sentinel_bus,
        )

        ratings = RatingStore(store)
        rating_service = RatingService(
            store=ratings, rpc=rpc, timeout_ms=settings.rpc_timeout_ms
        )
        players = PlayerService(
            store=store,
            ratings=rating_service,
            settings=settings,
            rpc=rpc,
            accounts=accounts_bus,
            moderation=moderation_bus,
        )

        presence_daemon = PresenceDaemon(bot=bot)
        admin_sync_daemon = AdminSyncDaemon(
            bot=bot, store=store, accounts=accounts_bus, settings=settings
        )
        rating_merge_daemon = RatingMergeDaemon(bot=bot, ratings=rating_service)
        stream_supervisor = StreamSupervisor(bot=bot)
        case_store = CaseStore(store)

        cases = CaseService(
            store=case_store,
            settings=settings,
            registry=flow_registry.copy(),
            threads=CaseThreads(bot, settings),
            notifier=CaseNotifier(bot, settings, find_player=store.find_player_by_uuid),
            claims=bus,
            player_store=store,
        )
        case_panel = CasePanel(cases, bot)

        return cls(
            settings=settings,
            accounts_bus=accounts_bus,
            cases_bus=cases_bus,
            chat_bus=chat_bus,
            maps_bus=maps_bus,
            moderation_bus=moderation_bus,
            rating_bus=rating_bus,
            security_bus=security_bus,
            sentinel_bus=sentinel_bus,
            rpc=rpc,
            players=players,
            ratings=ratings,
            game_stats=GameStatsStore(store),
            rating_service=rating_service,
            presence_daemon=presence_daemon,
            admin_sync_daemon=admin_sync_daemon,
            rating_merge_daemon=rating_merge_daemon,
            stream_supervisor=stream_supervisor,
            cases=cases,
            case_panel=case_panel,
            cases_reconcile_daemon=CasesReconcileDaemon(bot, cases, case_panel),
            staff_sync_daemon=(
                StaffSyncDaemon(bot, store, rpc, settings)
                if settings.permissions_mode == "roles"
                else None
            ),
        )
