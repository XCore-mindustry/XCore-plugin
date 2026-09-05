package org.xcore.plugin.ui.menu;

import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

final class PlayerProfileFlows {

    static final String ROUTE_PLAYER = "player.profile";
    static final String ROUTE_PLAYERS = "player.players";

    private PlayerProfileFlows() {
    }

    static final class PlayerFlow extends BaseMenuFlow<PlayerState> {
        private final PlayerMenu menu;
        private final GameDataRepository gameDataRepository;
        private final AuditHistoryMenu auditHistoryMenu;

        PlayerFlow(PlayerMenu menu, GameDataRepository gameDataRepository, AuditHistoryMenu auditHistoryMenu) {
            super(ROUTE_PLAYER, PlayerState.class);
            this.menu = menu;
            this.gameDataRepository = gameDataRepository;
            this.auditHistoryMenu = auditHistoryMenu;

            action("settings", ctx -> ctx.openRoute(MenuRoute.of(PlayerSettingsFlows.ROUTE_SETTINGS).withParam("targetUuid", ctx.state().targetUuid)));
            action("audit-history", ctx -> auditHistoryMenu.history(ctx.session().data.uuid, resolveTargetData(ctx)));
            action("audit-actions", ctx -> auditHistoryMenu.actions(ctx.session().data.uuid, resolveTargetData(ctx)));
            action("players", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_PLAYERS).withParam("page", "1")));
        }

        @Override
        public PlayerState createState(Session session, MenuRoute route, PlayerState currentState) {
            String targetUuid = route.param("targetUuid");
            if (currentState != null && Objects.equals(currentState.targetUuid, targetUuid)) {
                return currentState;
            }
            return new PlayerState(targetUuid);
        }

        @Override
        public MenuScreen render(MenuRenderContext<PlayerState> context) {
            Session session = context.session();
            PlayerState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);

            if (targetData == null) {
                session.locale().send("error-player-not-found");
                return errorScreen(session, "player-menu-player-title", "error-player-not-found");
            }

            Localization local = context.locale();
            boolean isOwner = session.data.uuid.equals(targetData.uuid);

            String customNickname = targetData.customNickname == null || Objects.equals(targetData.customNickname, "")
                    ? targetData.nickname : targetData.customNickname;
            String description = targetData.description == null || Objects.equals(targetData.description, "")
                    ? local.t("no-description") : targetData.description;
            String activeBadge = PlayerSettingsFlows.activeBadgeName(local, targetData);
            String systemBadge = PlayerSettingsFlows.systemBadgeName(local, targetData);
            String accountCreated = menu.formatTime(targetData.createdModelTime, session);
            String playTime = menu.formatPlayTime(targetData.totalPlayTime, local);
            String rankName = local.t("hexed-ranks-" + targetData.hexedRank().name());
            String hexedProgress = formatHexedProgress(local, targetData);
            NumberFormat numberFormat = NumberFormat.getIntegerInstance(local.getLocale());
            Integer hexedTop = session.playerDataRepository != null
                    ? session.playerDataRepository.findTopRank(org.xcore.plugin.model.enums.TopCategory.HEXED, targetData)
                    : null;
            String hexedTopRank = hexedTop != null ? "#" + numberFormat.format(hexedTop) : "-";
            PlayerStatsOverview statsOverview = gameDataRepository.aggregatePlayerStatsOverview(targetData.uuid);
            var overallStats = statsOverview.overall();

            var grid = new MenuGrid();
            List<MenuButton> actions = new ArrayList<>();
            if (isOwner || session.player.admin) {
                actions.add(MenuButton.of(local.t("player-menu-settings"), "settings"));
            }
            if (session.player.admin) {
                actions.add(MenuButton.of(local.t("audit-menu-open"), "audit-history"));
                actions.add(MenuButton.of(local.t("audit-menu-actions-open"), "audit-actions"));
            }
            if (!actions.isEmpty()) {
                grid.row(actions.toArray(new MenuButton[0]));
            }
            grid.row(MenuButton.of(local.t("player-menu-players"), "players"));
            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("player-menu-player-title"),
                    local.t("player-menu-player-content", args(
                            "nickname", targetData.nickname,
                            "customNickname", customNickname,
                            "activeBadge", activeBadge,
                            "systemBadge", systemBadge,
                            "description", description,
                            "pid", targetData.pid,
                            "accountCreated", accountCreated,
                            "totalPlayTime", playTime,
                            "hexedRankTag", targetData.hexedRank().tag,
                            "hexedRankName", rankName,
                            "hexedProgress", hexedProgress,
                            "hexedTopRank", hexedTopRank,
                            "pvpRating", numberFormat.format(targetData.pvpRating),
                            "gamesPlayed", numberFormat.format(overallStats.gamesPlayed()),
                            "gamesWon", numberFormat.format(overallStats.gamesWon()),
                            "winRate", numberFormat.format(overallStats.winRatePercent()),
                            "blocksBuilt", numberFormat.format(overallStats.blocksBuilt()),
                            "blocksDeconstructed", numberFormat.format(overallStats.blocksDeconstructed()),
                            "blocksDestroyed", numberFormat.format(overallStats.blocksDestroyed()),
                            "pvpSummary", formatPvpSummary(local, statsOverview.pvp(), numberFormat),
                            "survivalSummary", formatSurvivalSummary(local, statsOverview.survival(), numberFormat),
                            "hexedSummary", formatHexedSummary(local, statsOverview.hexed(), numberFormat),
                            "admin", targetData.admin ? local.t("yes") : local.t("no"),
                            "hexedPoints", numberFormat.format(targetData.hexedPoints)
                    )),
                    grid.build()
            );
        }

        private PlayerData resolveTargetData(MenuRenderContext<PlayerState> context) {
            return context.session().playerDataRepository.findByUuid(context.state().targetUuid);
        }
    }

    static final class PlayersFlow extends BaseMenuFlow<PlayersState> {
        private final PlayerMenu menu;
        private final SessionService sessionService;
        private final PlayerDisplayService playerDisplayService;

        PlayersFlow(PlayerMenu menu, SessionService sessionService, PlayerDisplayService playerDisplayService) {
            super(ROUTE_PLAYERS, PlayersState.class);
            this.menu = menu;
            this.sessionService = sessionService;
            this.playerDisplayService = playerDisplayService;

            action("admin-filter", ctx -> {
                ctx.session().setNextStatus("admin");
                ctx.state().page = 1;
                ctx.render();
            });
            action("prev", ctx -> {
                ctx.state().page = Math.max(1, ctx.state().page - 1);
                ctx.render();
            });
            action("next", ctx -> {
                ctx.state().page = ctx.state().page + 1;
                ctx.render();
            });
            actionPrefix("select:", (ctx, indexStr) -> {
                int index = Integer.parseInt(indexStr);
                List<Session> onlinePlayers = getFilteredOnlinePlayers(ctx.session(), sessionService, playerDisplayService);
                int perPage = menu.secretsConfig.pagination.eventsPerPage;
                var pagination = CustomGatherers.calculatePagination(onlinePlayers.size(), perPage);
                int validPage = pagination.totalPages() <= 0 ? 1 : pagination.clampPage(ctx.state().page);
                int skip = (validPage - 1) * perPage;
                List<Session> players = onlinePlayers.stream()
                        .skip(skip)
                        .limit(perPage)
                        .toList();
                if (index >= 0 && index < players.size()) {
                    Session onlinePlayer = players.get(index);
                    ctx.openRoute(MenuRoute.of(ROUTE_PLAYER).withParam("targetUuid", onlinePlayer.data.uuid));
                }
            });
        }

        @Override
        public PlayersState createState(Session session, MenuRoute route, PlayersState currentState) {
            int page = route.intParam("page", 1);
            if (currentState != null && currentState.page == page) {
                return currentState;
            }
            return new PlayersState(page);
        }

        @Override
        public MenuScreen render(MenuRenderContext<PlayersState> context) {
            Session session = context.session();
            PlayersState state = context.state();
            Localization local = context.locale();

            List<Session> onlinePlayers = getFilteredOnlinePlayers(session, sessionService, playerDisplayService);
            int totalPlayers = onlinePlayers.size();
            int perPage = menu.secretsConfig.pagination.eventsPerPage;
            var pagination = CustomGatherers.calculatePagination(totalPlayers, perPage);
            int totalPages = Math.max(1, pagination.totalPages());
            int validPage = pagination.totalPages() <= 0 ? 1 : pagination.clampPage(state.page);
            int skip = (validPage - 1) * perPage;
            List<Session> players = onlinePlayers.stream()
                    .skip(skip)
                    .limit(perPage)
                    .toList();

            String menuContent;
            if (totalPlayers == 0) {
                menuContent = local.t("player-menu-players-empty");
            } else {
                menuContent = local.t("player-menu-players-content", args(
                        "page", validPage,
                        "total", totalPages
                ));
            }

            var grid = new MenuGrid();

            StatusEnum adminStatus = session.sortStatus.getOrDefault("admin", StatusEnum.Neutral);
            String adminLabel;
            if (adminStatus == StatusEnum.Active) {
                adminLabel = local.t("admin-active");
            } else if (adminStatus == StatusEnum.Inactive) {
                adminLabel = local.t("admin-inactive");
            } else {
                adminLabel = local.t("admin-neutral");
            }
            grid.row(MenuButton.of(adminLabel, "admin-filter"));

            List<MenuButton> paginationRow = new ArrayList<>();
            if (validPage > 1) {
                paginationRow.add(MenuButton.of(local.t("previous"), "prev"));
            }
            if (validPage < totalPages) {
                paginationRow.add(MenuButton.of(local.t("next"), "next"));
            }
            if (!paginationRow.isEmpty()) {
                grid.row(paginationRow.toArray(new MenuButton[0]));
            }

            for (int i = 0; i < players.size(); i++) {
                Session onlinePlayer = players.get(i);
                String label = local.t("player-menu-players-row", args(
                        "nickname", playerDisplayService.resolveBaseName(onlinePlayer.data, onlinePlayer.player),
                        "pid", onlinePlayer.data.pid
                ));
                grid.row(MenuButton.of(label, "select:" + i));
            }

            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("player-menu-players-title"),
                    menuContent,
                    grid.build()
            );
        }
    }

    static final class PlayerState {
        public String targetUuid;

        public PlayerState() {
        }

        public PlayerState(String targetUuid) {
            this.targetUuid = targetUuid;
        }
    }

    static final class PlayersState {
        public int page;

        public PlayersState() {
        }

        public PlayersState(int page) {
            this.page = page;
        }
    }

    private static MenuScreen errorScreen(Session session, String titleKey, String messageKey) {
        Localization local = session.locale();
        return MenuScreen.normal(
                local.t(titleKey),
                local.t(messageKey),
                new MenuGrid().row(MenuButton.of(local.t("close"), "close")).build()
        );
    }

    private static String formatPvpSummary(Localization local, ModeStatsSummary stats, NumberFormat numberFormat) {
        if (!stats.hasData()) {
            return local.t("player-menu-player-no-mode-stats");
        }
        return local.t("player-menu-player-pvp-summary", args(
                "gamesPlayed", numberFormat.format(stats.gamesPlayed()),
                "gamesWon", numberFormat.format(stats.gamesWon()),
                "winRate", numberFormat.format(stats.winRatePercent())
        ));
    }

    private static String formatSurvivalSummary(Localization local, ModeStatsSummary stats, NumberFormat numberFormat) {
        if (!stats.hasData()) {
            return local.t("player-menu-player-no-mode-stats");
        }
        return local.t("player-menu-player-survival-summary", args(
                "gamesPlayed", numberFormat.format(stats.gamesPlayed()),
                "bestWave", numberFormat.format(stats.bestWave()),
                "averageWave", numberFormat.format(stats.averageWave())
        ));
    }

    private static String formatHexedSummary(Localization local, ModeStatsSummary stats, NumberFormat numberFormat) {
        if (!stats.hasData()) {
            return local.t("player-menu-player-no-mode-stats");
        }
        return local.t("player-menu-player-hexed-summary", args(
                "gamesPlayed", numberFormat.format(stats.gamesPlayed()),
                "gamesWon", numberFormat.format(stats.gamesWon()),
                "bestPlacement", numberFormat.format(stats.bestPlacement()),
                "top3Finishes", numberFormat.format(stats.top3Finishes())
        ));
    }

    private static String formatHexedProgress(Localization local, PlayerData targetData) {
        var rank = targetData.hexedRank();
        if (!rank.hasNext()) {
            return local.t("player-menu-player-max-rank");
        }

        int currentPoints = targetData.hexedPoints;
        int requiredPoints = rank.next.requirements.wins();
        int remainingWins = requiredPoints - currentPoints;
        String nextRankName = local.t("hexed-ranks-" + rank.next.name());
        return local.t("player-menu-player-hexed-progress", args(
                "currentPoints", currentPoints,
                "requiredPoints", remainingWins,
                "nextRankName", nextRankName
        ));
    }

    private static List<Session> getFilteredOnlinePlayers(Session viewerSession,
                                                           SessionService sessionService,
                                                           PlayerDisplayService playerDisplayService) {
        return sessionService.streamCached()
                .filter(onlineSession -> onlineSession != null && onlineSession.data != null && onlineSession.player != null)
                .filter(onlineSession -> matchesAdminFilter(viewerSession, onlineSession))
                .sorted(Comparator
                        .comparing((Session onlineSession) -> playerDisplayService.resolveBaseName(onlineSession.data, onlineSession.player), String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(onlineSession -> onlineSession.data.pid))
                .toList();
    }

    private static boolean matchesAdminFilter(Session viewerSession, Session onlineSession) {
        var adminFilter = viewerSession.sortStatus.get("admin");
        boolean isAdmin = onlineSession.player.admin || onlineSession.data.admin;

        if (adminFilter == null) {
            return true;
        }

        return switch (adminFilter) {
            case Active -> isAdmin;
            case Inactive -> !isAdmin;
            case Neutral -> true;
        };
    }
}
