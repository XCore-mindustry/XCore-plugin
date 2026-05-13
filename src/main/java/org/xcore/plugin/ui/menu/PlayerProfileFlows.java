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
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

final class PlayerProfileFlows {

    static final String ROUTE_PLAYER = "player.profile";
    static final String ROUTE_PLAYERS = "player.players";

    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_SETTINGS = "settings";
    private static final String ACTION_AUDIT_HISTORY = "audit-history";
    private static final String ACTION_AUDIT_ACTIONS = "audit-actions";
    private static final String ACTION_PLAYERS = "players";
    private static final String ACTION_ADMIN_FILTER = "admin-filter";
    private static final String ACTION_PREV = "prev";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_SELECT_PREFIX = "select-";

    private PlayerProfileFlows() {
    }

    static final class PlayerFlow implements RoutedMenuFlow<PlayerState> {
        private final PlayerMenu menu;
        private final GameDataRepository gameDataRepository;
        private final AuditHistoryMenu auditHistoryMenu;

        PlayerFlow(PlayerMenu menu, GameDataRepository gameDataRepository, AuditHistoryMenu auditHistoryMenu) {
            this.menu = menu;
            this.gameDataRepository = gameDataRepository;
            this.auditHistoryMenu = auditHistoryMenu;
        }

        @Override
        public String routeId() {
            return ROUTE_PLAYER;
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
        public Class<PlayerState> stateType() {
            return PlayerState.class;
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
            PlayerStatsOverview statsOverview = gameDataRepository.aggregatePlayerStatsOverview(targetData.uuid);
            var overallStats = statsOverview.overall();
            NumberFormat numberFormat = NumberFormat.getIntegerInstance(local.getLocale());

            List<List<MenuButton>> rows = new ArrayList<>();
            List<MenuButton> actions = new ArrayList<>();
            if (isOwner || session.player.admin) {
                actions.add(MenuButton.of(local.t("player-menu-settings"), ACTION_SETTINGS));
            }
            if (session.player.admin) {
                actions.add(MenuButton.of(local.t("audit-menu-open"), ACTION_AUDIT_HISTORY));
                actions.add(MenuButton.of(local.t("audit-menu-actions-open"), ACTION_AUDIT_ACTIONS));
            }
            if (!actions.isEmpty()) {
                rows.add(actions);
            }
            rows.add(List.of(MenuButton.of(local.t("player-menu-players"), ACTION_PLAYERS)));

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack()) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

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
                            "pvpRating", numberFormat.format(targetData.pvpRating),
                            "gamesPlayed", numberFormat.format(overallStats.gamesPlayed()),
                            "gamesWon", numberFormat.format(overallStats.gamesWon()),
                            "winRate", numberFormat.format(overallStats.winRatePercent()),
                            "blocksBuilt", numberFormat.format(overallStats.blocksBuilt()),
                            "blocksDeconstructed", numberFormat.format(overallStats.blocksDeconstructed()),
                            "blocksDestroyed", numberFormat.format(overallStats.blocksDestroyed()),
                            "unitsProduced", numberFormat.format(overallStats.unitsProduced()),
                            "unitsDestroyed", numberFormat.format(overallStats.unitsDestroyed()),
                            "pvpSummary", formatPvpSummary(local, statsOverview.pvp(), numberFormat),
                            "survivalSummary", formatSurvivalSummary(local, statsOverview.survival(), numberFormat),
                            "hexedSummary", formatHexedSummary(local, statsOverview.hexed(), numberFormat),
                            "admin", targetData.admin ? local.t("yes") : local.t("no"),
                            "hexedPoints", numberFormat.format(targetData.hexedPoints)
                    )),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<PlayerState> context, String actionId) {
            Session session = context.session();
            PlayerState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);
            if (targetData == null) {
                return;
            }

            switch (actionId) {
                case ACTION_SETTINGS -> context.openRoute(MenuRoute.of(PlayerSettingsFlows.ROUTE_SETTINGS).withParam("targetUuid", state.targetUuid));
                case ACTION_AUDIT_HISTORY -> auditHistoryMenu.history(session.data.uuid, targetData);
                case ACTION_AUDIT_ACTIONS -> auditHistoryMenu.actions(session.data.uuid, targetData);
                case ACTION_PLAYERS -> context.openRoute(MenuRoute.of(ROUTE_PLAYERS).withParam("page", "1"));
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
            }
        }
    }

    static final class PlayersFlow implements RoutedMenuFlow<PlayersState> {
        private final PlayerMenu menu;
        private final SessionService sessionService;
        private final PlayerDisplayService playerDisplayService;

        PlayersFlow(PlayerMenu menu, SessionService sessionService, PlayerDisplayService playerDisplayService) {
            this.menu = menu;
            this.sessionService = sessionService;
            this.playerDisplayService = playerDisplayService;
        }

        @Override
        public String routeId() {
            return ROUTE_PLAYERS;
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
        public Class<PlayersState> stateType() {
            return PlayersState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<PlayersState> context) {
            Session session = context.session();
            PlayersState state = context.state();
            Localization local = context.locale();

            List<Session> onlinePlayers = getFilteredOnlinePlayers(session, sessionService, playerDisplayService);
            int totalPlayers = onlinePlayers.size();
            int perPage = menu.globalConfig.eventsPerPage;
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

            List<List<MenuButton>> rows = new ArrayList<>();

            StatusEnum adminStatus = session.sortStatus.getOrDefault("admin", StatusEnum.Neutral);
            String adminLabel;
            if (adminStatus == StatusEnum.Active) {
                adminLabel = local.t("admin-active");
            } else if (adminStatus == StatusEnum.Inactive) {
                adminLabel = local.t("admin-inactive");
            } else {
                adminLabel = local.t("admin-neutral");
            }
            rows.add(List.of(MenuButton.of(adminLabel, ACTION_ADMIN_FILTER)));

            List<MenuButton> paginationRow = new ArrayList<>();
            if (validPage > 1) {
                paginationRow.add(MenuButton.of(local.t("previous"), ACTION_PREV));
            }
            if (validPage < totalPages) {
                paginationRow.add(MenuButton.of(local.t("next"), ACTION_NEXT));
            }
            if (!paginationRow.isEmpty()) {
                rows.add(paginationRow);
            }

            for (int i = 0; i < players.size(); i++) {
                Session onlinePlayer = players.get(i);
                String label = local.t("player-menu-players-row", args(
                        "nickname", playerDisplayService.resolveBaseName(onlinePlayer.data, onlinePlayer.player),
                        "pid", onlinePlayer.data.pid
                ));
                rows.add(List.of(MenuButton.of(label, ACTION_SELECT_PREFIX + i)));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack()) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("player-menu-players-title"),
                    menuContent,
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<PlayersState> context, String actionId) {
            Session session = context.session();
            PlayersState state = context.state();

            switch (actionId) {
                case ACTION_ADMIN_FILTER -> {
                    session.setNextStatus("admin");
                    state.page = 1;
                    context.render();
                }
                case ACTION_PREV -> {
                    state.page = Math.max(1, state.page - 1);
                    context.render();
                }
                case ACTION_NEXT -> {
                    state.page = state.page + 1;
                    context.render();
                }
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                    if (actionId.startsWith(ACTION_SELECT_PREFIX)) {
                        int index = Integer.parseInt(actionId.substring(ACTION_SELECT_PREFIX.length()));
                        List<Session> onlinePlayers = getFilteredOnlinePlayers(session, sessionService, playerDisplayService);
                        int perPage = menu.globalConfig.eventsPerPage;
                        var pagination = CustomGatherers.calculatePagination(onlinePlayers.size(), perPage);
                        int validPage = pagination.totalPages() <= 0 ? 1 : pagination.clampPage(state.page);
                        int skip = (validPage - 1) * perPage;
                        List<Session> players = onlinePlayers.stream()
                                .skip(skip)
                                .limit(perPage)
                                .toList();
                        if (index >= 0 && index < players.size()) {
                            Session onlinePlayer = players.get(index);
                            context.openRoute(MenuRoute.of(ROUTE_PLAYER).withParam("targetUuid", onlinePlayer.data.uuid));
                        }
                    }
                }
            }
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
                List.of(List.of(MenuButton.of(local.t("close"), ACTION_CLOSE)))
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
        String nextRankName = local.t("hexed-ranks-" + rank.next.name());
        return local.t("player-menu-player-hexed-progress", args(
                "currentPoints", currentPoints,
                "requiredPoints", requiredPoints,
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
