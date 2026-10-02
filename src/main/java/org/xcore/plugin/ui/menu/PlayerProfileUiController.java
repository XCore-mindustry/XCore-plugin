package org.xcore.plugin.ui.menu;

import arc.util.Strings;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.gamemode.hexed.HexedRanks.HexedRank;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ospx.flubundle.Bundle.args;

/**
 * Modern reactive Player Profile and Stats UI controller for Mindustry v160 using xcore-ui.
 *
 * <p>Consolidates player profile inspection, gamemode telemetry (MiniPvP, Survival, Legacy Hexed),
 * combat &amp; construction efficiency, and live server player roster into a unified Elm/MVI dialog.
 */
public class PlayerProfileUiController implements UiController<PlayerProfileUiController.ProfileModel, PlayerProfileUiController.ProfileEvent> {

    public static final int PLAYERS_PER_PAGE = 5;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    public enum Tab {
        OVERVIEW,
        STATS,
        PLAYERS
    }

    public enum AdminFilter {
        ALL,
        ADMINS_ONLY,
        NON_ADMINS;

        public AdminFilter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public record OnlinePlayerRow(
            String uuid,
            int pid,
            String nickname,
            String customNickname,
            String activeBadge,
            String badgeSymbolColorMode,
            boolean isAdmin,
            String playerColorHex
    ) {}

    public record UiMetrics(
            float dialogWidth,
            float contentWidth,
            float cardWidth,
            float cardInnerWidth,
            float paneMaxHeight,
            boolean isMobile
    ) {
        public static UiMetrics of(boolean isMobile) {
            if (isMobile) {
                float dw = 680f;
                float pad = 8f;
                float cw = dw - pad * 2f;
                float cardW = cw - 26f;
                float cardInnerW = cardW - 20f;
                return new UiMetrics(dw, cw, cardW, cardInnerW, 250f, true);
            } else {
                float dw = 740f;
                float pad = 12f;
                float cw = dw - pad * 2f;
                float cardW = cw - 26f;
                float cardInnerW = cardW - 24f;
                return new UiMetrics(dw, cw, cardW, cardInnerW, 440f, false);
            }
        }
    }

    public record ProfileModel(
            // Viewer identity
            String viewerUuid,

            // Target Player Identity
            String targetUuid,
            int pid,
            String nickname,
            String customNickname,
            String description,
            boolean isTargetAdmin,
            boolean isTargetOnline,
            long createdModelTime,
            int totalPlayTime,
            String activeBadge,
            String badgeSymbolColorMode,
            Set<String> unlockedBadges,
            String playerColorHex,

            // Gamemode Progression & Stats
            int pvpRating,
            int legacyPvpRating,
            int pvpMatches,
            int pvpWins,
            int hexedPoints,
            HexedRank hexedRank,
            Integer hexedTopRank,
            PlayerStatsOverview stats,
            boolean isStatsLoading,

            // Viewer & Context
            boolean isSelf,
            boolean isViewerAdmin,
            Tab tab,
            boolean isMobile,
            boolean viewingFromPlayersTab,

            // Players Tab State
            AdminFilter adminFilter,
            int playersPage,
            int totalOnlineCount,
            List<OnlinePlayerRow> onlinePlayers,
            String feedbackMessage
    ) {
        public ProfileModel withTab(Tab newTab) {
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, pvpRating, legacyPvpRating, pvpMatches, pvpWins, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, newTab, isMobile,
                    viewingFromPlayersTab, adminFilter, playersPage, totalOnlineCount, onlinePlayers, "");
        }

        public ProfileModel withStats(PlayerStatsOverview newStats, Integer newHexedTopRank) {
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, pvpRating, legacyPvpRating, pvpMatches, pvpWins, hexedPoints, hexedRank, newHexedTopRank,
                    newStats, false, isSelf, isViewerAdmin, tab, isMobile,
                    viewingFromPlayersTab, adminFilter, playersPage, totalOnlineCount, onlinePlayers, feedbackMessage);
        }

        public ProfileModel withTarget(PlayerData target, boolean isOnline, String colorHex,
                                      PlayerStatsOverview newStats, Integer newTopRank, boolean fromPlayersTab) {
            Objects.requireNonNull(target, "target");
            return new ProfileModel(
                    viewerUuid,
                    target.uuid,
                    target.pid,
                    target.nickname != null ? target.nickname : "",
                    target.customNickname != null ? target.customNickname : "",
                    target.description != null ? target.description : "",
                    target.admin,
                    isOnline,
                    target.createdModelTime,
                    target.totalPlayTime,
                    target.activeBadge != null ? target.activeBadge : "",
                    target.badgeSymbolColorMode != null ? target.badgeSymbolColorMode : "default",
                    target.unlockedBadges != null ? target.unlockedBadges : Set.of(),
                    colorHex != null ? colorHex : "FFD37F",
                    target.pvpRating,
                    target.legacyPvpRating,
                    target.pvpMatches,
                    target.pvpWins,
                    target.hexedPoints,
                    target.hexedRank(),
                    newTopRank,
                    newStats,
                    newStats == null,
                    Objects.equals(viewerUuid, target.uuid),
                    isViewerAdmin,
                    Tab.OVERVIEW,
                    isMobile,
                    fromPlayersTab,
                    adminFilter,
                    playersPage,
                    totalOnlineCount,
                    onlinePlayers,
                    ""
            );
        }

        public ProfileModel withPlayersPage(int newPage) {
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, pvpRating, legacyPvpRating, pvpMatches, pvpWins, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, tab, isMobile,
                    viewingFromPlayersTab, adminFilter, newPage, totalOnlineCount, onlinePlayers, feedbackMessage);
        }

        public ProfileModel withAdminFilter(AdminFilter filter, List<OnlinePlayerRow> players) {
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, pvpRating, legacyPvpRating, pvpMatches, pvpWins, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, tab, isMobile,
                    viewingFromPlayersTab, filter, 1, totalOnlineCount, players, feedbackMessage);
        }

        public ProfileModel withRefreshedPlayers(List<OnlinePlayerRow> players, int totalCount) {
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, pvpRating, legacyPvpRating, pvpMatches, pvpWins, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, tab, isMobile,
                    viewingFromPlayersTab, adminFilter, 1, totalCount, players, feedbackMessage);
        }

        public ProfileModel withRefreshedPlayersPreservingPage(List<OnlinePlayerRow> players, int totalCount) {
            int maxPage = Math.max(1, (int) Math.ceil((double) players.size() / PLAYERS_PER_PAGE));
            int page = Math.clamp(playersPage, 1, maxPage);
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, pvpRating, legacyPvpRating, pvpMatches, pvpWins, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, tab, isMobile,
                    viewingFromPlayersTab, adminFilter, page, totalCount, players, feedbackMessage);
        }

        public ProfileModel withFeedback(String msg) {
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, pvpRating, legacyPvpRating, pvpMatches, pvpWins, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, tab, isMobile,
                    viewingFromPlayersTab, adminFilter, playersPage, totalOnlineCount, onlinePlayers, msg);
        }
    }

    public sealed interface ProfileEvent {
        record SelectTab(Tab tab) implements ProfileEvent {}
        record StatsLoaded(String targetUuid, PlayerStatsOverview stats, Integer hexedTopRank) implements ProfileEvent {}
        record InspectPlayer(String targetUuid) implements ProfileEvent {}
        record BackToPlayers() implements ProfileEvent {}
        record Back() implements ProfileEvent {}
        record CycleAdminFilter() implements ProfileEvent {}
        record ChangePlayersPage(int page) implements ProfileEvent {}
        record RefreshPlayers() implements ProfileEvent {}
        record OpenSettings() implements ProfileEvent {}
        record OpenAuditHistory() implements ProfileEvent {}
        record OpenAuditActions() implements ProfileEvent {}
        record Close() implements ProfileEvent {}
    }

    private final PlayerMenu playerMenu;
    private final AuditHistoryMenu auditHistoryMenu;
    private final SessionService sessionService;
    private final PlayerDisplayService playerDisplayService;
    private final PlayerDataRepository playerDataRepository;
    private final GameDataRepository gameDataRepository;
    private final Async async;
    private final Session session;
    private final PlayerData initialTargetData;

    public PlayerProfileUiController(PlayerMenu playerMenu,
                                     AuditHistoryMenu auditHistoryMenu,
                                     SessionService sessionService,
                                     PlayerDisplayService playerDisplayService,
                                     PlayerDataRepository playerDataRepository,
                                     GameDataRepository gameDataRepository,
                                     Async async,
                                     Session session,
                                     PlayerData targetData) {
        this.playerMenu = playerMenu;
        this.auditHistoryMenu = auditHistoryMenu;
        this.sessionService = sessionService;
        this.playerDisplayService = playerDisplayService;
        this.playerDataRepository = playerDataRepository;
        this.gameDataRepository = gameDataRepository;
        this.async = async;
        this.session = session;
        this.initialTargetData = targetData;
    }

    public ProfileModel createInitialModel(Tab tab, PlayerStatsOverview stats, Integer hexedTop) {
        return createModel(session, initialTargetData, tab, stats, hexedTop, sessionService, playerDisplayService);
    }

    public static ProfileModel createModel(Session session,
                                           PlayerData targetData,
                                           Tab initialTab,
                                           PlayerStatsOverview stats,
                                           Integer hexedTop,
                                           SessionService sessionService,
                                           PlayerDisplayService playerDisplayService) {
        Objects.requireNonNull(targetData, "targetData");
        boolean isMobile = session != null && session.player != null && session.player.con != null && session.player.con.mobile;
        String viewerUuid = session != null && session.data != null ? session.data.uuid : "";
        boolean isSelf = session != null && session.data != null && Objects.equals(session.data.uuid, targetData.uuid);
        boolean isViewerAdmin = session != null && session.player != null && session.player.admin;

        boolean isOnline = false;
        if (sessionService != null) {
            Session s = sessionService.get(targetData.uuid);
            isOnline = s != null && s.player != null;
        }

        String playerColorHex = PlayerSettingsUiController.resolvePlayerColorHex(session, targetData);

        List<OnlinePlayerRow> onlinePlayers = resolveOnlinePlayers(session, sessionService, playerDisplayService, AdminFilter.ALL);
        int totalOnline = sessionService != null ? sessionService.getCachedCount() : onlinePlayers.size();

        return new ProfileModel(
                viewerUuid,
                targetData.uuid,
                targetData.pid,
                targetData.nickname != null ? targetData.nickname : "",
                targetData.customNickname != null ? targetData.customNickname : "",
                targetData.description != null ? targetData.description : "",
                targetData.admin,
                isOnline,
                targetData.createdModelTime,
                targetData.totalPlayTime,
                targetData.activeBadge != null ? targetData.activeBadge : "",
                targetData.badgeSymbolColorMode != null ? targetData.badgeSymbolColorMode : "default",
                targetData.unlockedBadges != null ? targetData.unlockedBadges : Set.of(),
                playerColorHex,
                targetData.pvpRating,
                targetData.legacyPvpRating,
                targetData.pvpMatches,
                targetData.pvpWins,
                targetData.hexedPoints,
                targetData.hexedRank(),
                hexedTop,
                stats,
                stats == null,
                isSelf,
                isViewerAdmin,
                initialTab != null ? initialTab : Tab.OVERVIEW,
                isMobile,
                false,
                AdminFilter.ALL,
                1,
                totalOnline,
                onlinePlayers,
                ""
        );
    }

    public static List<OnlinePlayerRow> resolveOnlinePlayers(Session viewerSession,
                                                            SessionService sessionService,
                                                            PlayerDisplayService playerDisplayService,
                                                            AdminFilter filter) {
        if (sessionService == null) return List.of();
        return sessionService.streamCached()
                .filter(s -> s != null && s.data != null && s.player != null)
                .filter(s -> matchesFilter(s, filter))
                .sorted(Comparator
                        .comparing((Session s) -> playerDisplayService != null
                                ? playerDisplayService.resolveBaseName(s.data, s.player)
                                : (s.data.nickname != null ? s.data.nickname : ""), String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(s -> s.data.pid))
                .map(s -> new OnlinePlayerRow(
                        s.data.uuid,
                        s.data.pid,
                        s.data.nickname != null ? s.data.nickname : "",
                        s.data.customNickname != null ? s.data.customNickname : "",
                        s.data.activeBadge != null ? s.data.activeBadge : "",
                        s.data.badgeSymbolColorMode != null ? s.data.badgeSymbolColorMode : "default",
                        s.player.admin || s.data.admin,
                        PlayerSettingsUiController.resolvePlayerColorHex(s, s.data)
                ))
                .toList();
    }

    private static boolean matchesFilter(Session s, AdminFilter filter) {
        if (filter == null || filter == AdminFilter.ALL) return true;
        boolean isAdmin = s.player.admin || s.data.admin;
        return filter == AdminFilter.ADMINS_ONLY ? isAdmin : !isAdmin;
    }

    public static String renderHexedProgressBar(HexedRank rank, int currentPoints, int barWidth) {
        if (rank == null || !rank.hasNext()) {
            return "[gold]★ MAX RANK ACHIEVED ★[]";
        }
        int required = rank.next.requirements.wins();
        float pct = Math.clamp(required > 0 ? (float) currentPoints / required : 0f, 0f, 1f);
        int filled = Math.round(pct * barWidth);
        int empty = barWidth - filled;

        return "[accent]" + "■".repeat(Math.max(0, filled))
                + "[darkgray]" + "■".repeat(Math.max(0, empty))
                + "[] [white]" + Math.round(pct * 100f) + "%[]";
    }

    public static String renderRatingLeagueProgressBar(RatingLeague league, int currentRating, int barWidth) {
        if (league == null || !league.hasNext()) {
            return "[gold]★ MAX LEAGUE ACHIEVED ★[]";
        }
        RatingLeague next = league.next();
        int min = league.minimumRating();
        int max = next.minimumRating();
        int span = Math.max(1, max - min);
        float pct = Math.clamp((float) (currentRating - min) / span, 0f, 1f);
        int filled = Math.round(pct * barWidth);
        int empty = barWidth - filled;

        return "[sky]" + "■".repeat(Math.max(0, filled))
                + "[darkgray]" + "■".repeat(Math.max(0, empty))
                + "[] [white]" + Math.round(pct * 100f) + "%[]";
    }

    public static String renderBlockRatioBar(long built, long decon, long destroyed, int totalBars) {
        long sum = built + decon + destroyed;
        if (sum <= 0) {
            return "[darkgray]" + "■".repeat(totalBars) + "[]";
        }
        int builtBars = Math.round((float) built / sum * totalBars);
        int deconBars = Math.round((float) decon / sum * totalBars);
        int destroyedBars = totalBars - builtBars - deconBars;
        if (destroyedBars < 0) {
            if (builtBars > deconBars) builtBars += destroyedBars;
            else deconBars += destroyedBars;
            destroyedBars = 0;
        }
        return "[lime]" + "■".repeat(Math.max(0, builtBars))
                + "[orange]" + "■".repeat(Math.max(0, deconBars))
                + "[scarlet]" + "■".repeat(Math.max(0, destroyedBars)) + "[]";
    }

    public static String formatDuration(int totalMinutes, Localization local) {
        if (totalMinutes <= 0) {
            return local != null ? local.t("player-menu-time-minutes", args("value", 0)) : "0m";
        }

        int days = totalMinutes / (60 * 24);
        int hours = (totalMinutes / 60) % 24;
        int minutes = totalMinutes % 60;

        StringBuilder result = new StringBuilder();
        if (days > 0 && local != null) {
            result.append(local.t("player-menu-time-days", args("value", days)));
        } else if (days > 0) {
            result.append(days).append("d");
        }
        if (hours > 0) {
            if (!result.isEmpty()) result.append(' ');
            if (local != null) result.append(local.t("player-menu-time-hours", args("value", hours)));
            else result.append(hours).append("h");
        }
        if (minutes > 0 || result.isEmpty()) {
            if (!result.isEmpty()) result.append(' ');
            if (local != null) result.append(local.t("player-menu-time-minutes", args("value", minutes)));
            else result.append(minutes).append("m");
        }

        return result.toString();
    }

    public static String formatTimestamp(long millis) {
        if (millis <= 0) return "-";
        return DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(millis));
    }

    @Override
    public ProfileModel initialModel(Object context) {
        Tab tab = context instanceof Tab t ? t : Tab.OVERVIEW;
        return createInitialModel(tab, null, null);
    }

    @Override
    public UpdateResult<ProfileModel> update(ProfileModel model, ProfileEvent event, ControllerContext ctx) {
        return switch (event) {
            case ProfileEvent.SelectTab(Tab tab) ->
                    UpdateResult.rerender(model.withTab(tab));

            case ProfileEvent.StatsLoaded(String targetUuid, PlayerStatsOverview stats, Integer hexedTop) -> {
                if (Objects.equals(model.targetUuid(), targetUuid)) {
                    yield UpdateResult.rerender(model.withStats(stats, hexedTop));
                }
                yield UpdateResult.of(model);
            }

            case ProfileEvent.BackToPlayers() -> {
                List<OnlinePlayerRow> rows = resolveOnlinePlayers(session, sessionService, playerDisplayService, model.adminFilter());
                int count = sessionService != null ? sessionService.getCachedCount() : rows.size();
                yield UpdateResult.rerender(model.withRefreshedPlayersPreservingPage(rows, count).withTab(Tab.PLAYERS));
            }

            case ProfileEvent.Back() -> {
                if (session != null && session.hasHistory()) {
                    Runnable prev = session.popHistory();
                    if (prev != null) {
                        if (ctx != null) {
                            ctx.post(prev);
                        } else {
                            prev.run();
                        }
                        yield UpdateResult.close(model);
                    }
                }
                yield UpdateResult.of(model);
            }

            case ProfileEvent.CycleAdminFilter() -> {
                AdminFilter nextFilter = model.adminFilter().next();
                List<OnlinePlayerRow> rows = resolveOnlinePlayers(session, sessionService, playerDisplayService, nextFilter);
                yield UpdateResult.rerender(model.withAdminFilter(nextFilter, rows));
            }

            case ProfileEvent.ChangePlayersPage(int page) -> {
                int totalPages = Math.max(1, (int) Math.ceil((double) model.onlinePlayers().size() / PLAYERS_PER_PAGE));
                int clamped = Math.clamp(page, 1, totalPages);
                yield UpdateResult.rerender(model.withPlayersPage(clamped));
            }

            case ProfileEvent.RefreshPlayers() -> {
                List<OnlinePlayerRow> rows = resolveOnlinePlayers(session, sessionService, playerDisplayService, model.adminFilter());
                int count = sessionService != null ? sessionService.getCachedCount() : rows.size();
                yield UpdateResult.rerender(model.withRefreshedPlayers(rows, count));
            }

            case ProfileEvent.InspectPlayer(String targetUuid) -> {
                PlayerData target = null;
                boolean isOnline = false;
                if (sessionService != null) {
                    Session s = sessionService.get(targetUuid);
                    if (s != null && s.data != null) {
                        target = s.data;
                        isOnline = s.player != null;
                    }
                }
                if (target == null && initialTargetData != null && Objects.equals(initialTargetData.uuid, targetUuid)) {
                    target = initialTargetData;
                    isOnline = session != null && session.player != null && Objects.equals(session.data.uuid, targetUuid);
                }

                if (target == null) {
                    String err = session != null ? session.locale().t("error-player-not-found") : "Player not found";
                    yield UpdateResult.rerender(model.withFeedback("[scarlet]" + Iconc.warning + " " + err + "[]"));
                }

                String colorHex = PlayerSettingsUiController.resolvePlayerColorHex(session, target);
                ProfileModel newModel = model.withTarget(target, isOnline, colorHex, null, null, true);

                // Async fetch stats for the newly inspected player
                final PlayerData finalTarget = target;
                if (async != null && session != null && session.player != null) {
                    async.forPlayer(session.player, () -> {
                        Integer topRank = playerDataRepository != null
                                ? playerDataRepository.findTopRank(TopCategory.HEXED, finalTarget)
                                : null;
                        PlayerStatsOverview stats = gameDataRepository != null
                                ? gameDataRepository.aggregatePlayerStatsOverview(finalTarget.uuid)
                                : null;
                        return new StatsBundle(stats, topRank);
                    }, (player, bundle) -> {
                        var active = session.activeUiSession();
                        if (active != null && active.model() instanceof ProfileModel) {
                            @SuppressWarnings("unchecked")
                            var profileSession = (org.xcore.ui.runtime.UiSession<ProfileModel, ProfileEvent>) active;
                            profileSession.dispatch(new ProfileEvent.StatsLoaded(
                                    finalTarget.uuid, bundle.stats(), bundle.topRank()
                            ));
                        }
                    });
                }

                yield UpdateResult.rerender(newModel);
            }

            case ProfileEvent.OpenSettings() -> {
                if (!model.isSelf() && !model.isViewerAdmin()) {
                    yield UpdateResult.of(model);
                }
                PlayerData target = resolveCurrentTarget(model);
                if (target != null && playerMenu != null && session != null) {
                    playerMenu.openSettingsUi(session, target);
                }
                yield UpdateResult.close(model);
            }

            case ProfileEvent.OpenAuditHistory() -> {
                if (!model.isViewerAdmin()) {
                    yield UpdateResult.of(model);
                }
                PlayerData target = resolveCurrentTarget(model);
                if (target != null && auditHistoryMenu != null && session != null && session.data != null) {
                    final Tab returnTab = model.tab();
                    final PlayerStatsOverview cachedStats = model.stats();
                    final Integer cachedTop = model.hexedTopRank();

                    session.pushHistory(() -> {
                        playerMenu.openProfileUi(session, target, returnTab, cachedStats, cachedTop);
                    });
                    auditHistoryMenu.history(session.data.uuid, target);
                }
                yield UpdateResult.close(model);
            }

            case ProfileEvent.OpenAuditActions() -> {
                if (!model.isViewerAdmin()) {
                    yield UpdateResult.of(model);
                }
                PlayerData target = resolveCurrentTarget(model);
                if (target != null && auditHistoryMenu != null && session != null && session.data != null) {
                    final Tab returnTab = model.tab();
                    final PlayerStatsOverview cachedStats = model.stats();
                    final Integer cachedTop = model.hexedTopRank();

                    session.pushHistory(() -> {
                        playerMenu.openProfileUi(session, target, returnTab, cachedStats, cachedTop);
                    });
                    auditHistoryMenu.actions(session.data.uuid, target);
                }
                yield UpdateResult.close(model);
            }

            case ProfileEvent.Close() -> UpdateResult.close(model);
        };
    }

    private record StatsBundle(PlayerStatsOverview stats, Integer topRank) {}

    private PlayerData resolveCurrentTarget(ProfileModel model) {
        if (model == null || model.targetUuid() == null) return initialTargetData;
        if (initialTargetData != null && Objects.equals(initialTargetData.uuid, model.targetUuid())) {
            return initialTargetData;
        }
        if (sessionService != null) {
            Session s = sessionService.get(model.targetUuid());
            if (s != null && s.data != null) return s.data;
        }
        return initialTargetData;
    }

    @Override
    public ProfileEvent parseEvent(MenuResult result) {
        if (result == null || result.wasCancelled() || result.result == null) {
            return new ProfileEvent.Close();
        }
        String res = result.result;

        if ("action:close".equals(res)) return new ProfileEvent.Close();
        if ("action:back".equals(res)) return new ProfileEvent.Back();
        if ("action:settings".equals(res)) return new ProfileEvent.OpenSettings();
        if ("action:audit_history".equals(res)) return new ProfileEvent.OpenAuditHistory();
        if ("action:audit_actions".equals(res)) return new ProfileEvent.OpenAuditActions();
        if ("action:back_to_players".equals(res)) return new ProfileEvent.BackToPlayers();

        if ("action:tab:overview".equals(res)) return new ProfileEvent.SelectTab(Tab.OVERVIEW);
        if ("action:tab:stats".equals(res)) return new ProfileEvent.SelectTab(Tab.STATS);
        if ("action:tab:players".equals(res)) return new ProfileEvent.SelectTab(Tab.PLAYERS);

        if ("action:filter_cycle".equals(res)) return new ProfileEvent.CycleAdminFilter();
        if ("action:refresh_players".equals(res)) return new ProfileEvent.RefreshPlayers();

        if (res.startsWith("action:page:")) {
            try {
                int p = Integer.parseInt(res.substring("action:page:".length()));
                return new ProfileEvent.ChangePlayersPage(p);
            } catch (NumberFormatException ignored) {
                return new ProfileEvent.RefreshPlayers();
            }
        }

        if (res.startsWith("action:inspect:")) {
            return new ProfileEvent.InspectPlayer(res.substring("action:inspect:".length()));
        }

        return new ProfileEvent.Close();
    }

    @Override
    public VNode render(ProfileModel model) {
        UiMetrics metrics = UiMetrics.of(model.isMobile());
        Localization local = session != null ? session.locale() : null;

        return Ui.table(t -> {
            t.background("pane");
            t.margin(model.isMobile() ? 8f : 12f);
            t.layout(l -> l.width(metrics.dialogWidth()).pad(4f));

            // 1. Header with Accent Title, Online Status, Quick Actions, and Close Button
            t.add(Ui.table(h -> {
                h.layout(l -> l.width(metrics.contentWidth()).padBottom(4f));
                h.label(Text.raw("[orange]" + Iconc.players + "[] [white]"), l -> l.align("left"));
                String headerTitle = model.tab() == Tab.PLAYERS
                        ? (local != null ? local.t("player-menu-players-title") : "Online Players")
                        : (local != null ? local.t("player-stats-title") : "Player Profile");
                h.label(Text.raw(headerTitle), l -> l.align("left").growX());

                // Online status indicator and quick actions when viewing profile/stats
                if (model.tab() != Tab.PLAYERS) {
                    String statusText = model.isTargetOnline()
                            ? (local != null ? local.t("player-stats-status-online") : "[lime]● Online[]")
                            : (local != null ? local.t("player-stats-status-offline") : "[gray]○ Offline[]");
                    h.label(Text.raw(statusText + "  "), l -> l.align("right"));

                    // Settings quick button (if viewing self or viewer is admin)
                    if (model.isSelf() || model.isViewerAdmin()) {
                        h.button(Text.raw("[accent]" + Iconc.admin + " " + (local != null ? local.t("player-stats-btn-settings") : "Settings") + "[]"),
                                "action:settings", b -> b.style("cleart").layout(l -> l.padRight(4f)));
                    }

                    // Audit buttons for admins
                    if (model.isViewerAdmin()) {
                        h.button(Text.raw("[gray]" + (local != null ? local.t("player-stats-btn-audit") : "Audit") + "[]"),
                                "action:audit_history", b -> b.style("cleart").layout(l -> l.padRight(4f)));
                        h.button(Text.raw("[gray]" + (local != null ? local.t("audit-menu-actions-open") : "Actions") + "[]"),
                                "action:audit_actions", b -> b.style("cleart").layout(l -> l.padRight(4f)));
                    }
                }

                h.button(Text.raw(" [scarlet]" + Iconc.cancel + "[] "), "action:close", b -> b
                        .style("cleart")
                        .layout(l -> l.size(34f)));
            })).row();
            t.image("whiteui", l -> l.width(metrics.contentWidth()).height(3f).padBottom(6f).color("ffd37f")).row();

            // 2. Navigation Tabs Row (Overview / Stats / Online Players)
            t.add(Ui.table(tabs -> {
                tabs.layout(l -> l.width(metrics.contentWidth()).padBottom(6f));
                float tabHeight = model.isMobile() ? 34f : 36f;

                tabs.button(Text.raw(Iconc.admin + " " + (local != null ? local.t("player-stats-tab-overview") : "Overview")),
                        "action:tab:overview", b -> b
                                .style("togglet")
                                .checked(model.tab() == Tab.OVERVIEW)
                                .layout(l -> l.uniform().growX().height(tabHeight).padRight(4f)));

                tabs.button(Text.raw(Iconc.chartBar + " " + (local != null ? local.t("player-stats-tab-stats") : "Stats")),
                        "action:tab:stats", b -> b
                                .style("togglet")
                                .checked(model.tab() == Tab.STATS)
                                .layout(l -> l.uniform().growX().height(tabHeight).padRight(4f)));

                int onlineCount = model.totalOnlineCount();
                String countStr = " (" + onlineCount + ")";
                tabs.button(Text.raw(Iconc.players + " " + (local != null ? local.t("player-stats-tab-players", args("count", onlineCount)) : "Online" + countStr)),
                        "action:tab:players", b -> b
                                .style("togglet")
                                .checked(model.tab() == Tab.PLAYERS)
                                .layout(l -> l.uniform().growX().height(tabHeight)));
            })).row();
            t.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padBottom(6f).color("454545")).row();

            // 3. Scrollable Body
            t.pane(p -> {
                p.layout(l -> l.width(metrics.contentWidth()).maxHeight(metrics.paneMaxHeight()));
                p.table(body -> {
                    body.layout(l -> l.growX());

                    switch (model.tab()) {
                        case OVERVIEW -> renderOverviewTab(body, model, metrics, local);
                        case STATS -> renderStatsTab(body, model, metrics, local);
                        case PLAYERS -> renderPlayersTab(body, model, metrics, local);
                    }
                });
            }).row();

            // 4. Feedback Message
            if (model.feedbackMessage() != null && !model.feedbackMessage().isBlank()) {
                t.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padTop(4f).padBottom(4f).color("454545")).row();
                t.add(Ui.table(fb -> {
                    fb.layout(l -> l.width(metrics.contentWidth()).padTop(2f).padBottom(2f));
                    fb.label(Text.raw(model.feedbackMessage()), l -> l.align("center").growX());
                })).row();
            }

            // 5. Bottom Action Bar
            t.add(Ui.table(actions -> {
                actions.layout(l -> l.width(metrics.contentWidth()).padTop(6f));

                if (session != null && session.hasHistory()) {
                    actions.button(Text.raw("[lightgray]← " + (local != null ? local.t("back") : "Back") + "[]"),
                            "action:back", b -> b
                                    .style("cleart")
                                    .layout(l -> l.growX().uniform().height(42f).padRight(4f)));
                } else if (model.viewingFromPlayersTab() && model.tab() != Tab.PLAYERS) {
                    actions.button(Text.raw("[lightgray]" + (local != null ? local.t("player-stats-back-to-players") : "← Back to Online Players") + "[]"),
                            "action:back_to_players", b -> b
                                    .style("cleart")
                                    .layout(l -> l.growX().uniform().height(42f).padRight(4f)));
                }

                if (model.tab() == Tab.OVERVIEW) {
                    actions.button(Text.raw("[accent]" + (local != null ? local.t("player-stats-full-stats") : "Full Statistics →") + "[]"),
                            "action:tab:stats", b -> b
                                    .style("cleart")
                                    .layout(l -> l.growX().uniform().height(42f).padRight(4f)));
                } else if (model.tab() == Tab.STATS) {
                    actions.button(Text.raw("[accent]← " + (local != null ? local.t("player-stats-tab-overview") : "Overview") + "[]"),
                            "action:tab:overview", b -> b
                                    .style("cleart")
                                    .layout(l -> l.growX().uniform().height(42f).padRight(4f)));
                }

                actions.button(Text.t("close"), "action:close", b -> b
                        .style("cleart")
                        .layout(l -> l.growX().uniform().height(42f)));
            })).row();
        });
    }

    // =========================================================================
    // Tab 1: OVERVIEW
    // =========================================================================

    private void renderOverviewTab(Ui.TableBuilder body, ProfileModel model, UiMetrics metrics, Localization local) {
        // --- Card 1: Identity & Bio Card ---
        body.add(Ui.table(c -> {
            c.background("button");
            c.margin(10f);
            c.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

            // Name, badges and PID header
            c.add(Ui.table(top -> {
                top.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(4f));

                StringBuilder identity = new StringBuilder();
                if (model.isTargetAdmin()) {
                    identity.append("[scarlet]<" + Iconc.admin + " " + (local != null ? local.t("admin") : "Admin") + ">[] ");
                }
                if (model.activeBadge() != null && !model.activeBadge().isBlank()) {
                    Badge b = Badge.byId(model.activeBadge());
                    if (b != null) {
                        identity.append(PlayerSettingsUiController.renderBadgeTagExact(b, model.badgeSymbolColorMode(), model.playerColorHex())).append(" ");
                    }
                }

                String rawNick = model.customNickname() != null && !model.customNickname().isBlank()
                        ? model.customNickname()
                        : (model.nickname() != null && !model.nickname().isBlank() ? model.nickname() : "Player");
                if (rawNick.startsWith("[")) {
                    identity.append(rawNick);
                } else {
                    identity.append("[accent]").append(rawNick);
                }
                identity.append(" [gray]#").append(model.pid()).append("[]");

                top.label(Text.raw(identity.toString()), l -> l.align("left").growX());
            })).row();

            // Bio / Description
            c.add(Ui.table(descTable -> {
                descTable.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(6f));
                String desc = model.description() != null && !model.description().isBlank()
                        ? "[lightgray]\"" + model.description().trim() + "[lightgray]\"[]"
                        : (local != null ? local.t("player-stats-no-bio") : "[gray]No bio written yet.[]");
                descTable.labelWrap(Text.raw(desc), l -> l.align("left").width(metrics.cardInnerWidth()));
            })).row();

            // Info row: Joined, Play time
            c.add(Ui.table(info -> {
                info.layout(l -> l.width(metrics.cardInnerWidth()));
                String joinedLbl = local != null ? local.t("player-stats-account-created") : "[gray]Joined:[]";
                String playTimeLbl = local != null ? local.t("player-stats-play-time") : "[gray]Play time:[]";
                info.label(Text.raw(joinedLbl + " [white]" + formatTimestamp(model.createdModelTime()) + "[]  [darkgray]|[]  "
                        + playTimeLbl + " [white]" + formatDuration(model.totalPlayTime(), local) + "[]"), l -> l.align("left").growX());
            })).row();
        })).row();

        // --- Card 2: MiniPvP Rating & Progression Card ---
        body.add(Ui.table(c -> {
            c.background("button");
            c.margin(10f);
            c.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

            RatingLeague league = RatingLeague.fromRating(model.pvpRating());
            String leagueName = local != null ? local.t(league.localizationKey()) : league.name();
            RatingLeague nextLeague = league.next();

            c.add(Ui.table(top -> {
                top.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(4f));
                String pvpLbl = local != null ? local.t("player-stats-pvp-summary") : "MiniPvP:";
                String pvpTitle = "[red]" + Iconc.modePvp + " " + pvpLbl + "[] " + league.icon() + " [white]" + leagueName + "[] ([sky]" + model.pvpRating() + " ELO[])";

                int matches = model.pvpMatches();
                int wins = model.pvpWins();
                int winRate = matches <= 0 ? 0 : Math.round((wins * 100.0f) / matches);
                String statsPart = matches > 0
                        ? "  [darkgray]|[]  [white]" + matches + "[] [gray]matches[] [darkgray]|[] [lime]" + wins + "[] [gray]wins (" + winRate + "%)[]"
                        : "";

                String legacyPart = model.legacyPvpRating() > 0
                        ? "  [darkgray]|[]  " + (local != null ? local.t("player-stats-legacy-pvp-rating") : "[gray]Legacy PvP:[]") + " [sky]" + model.legacyPvpRating() + "[]"
                        : "";

                if (model.isMobile()) {
                    top.label(Text.raw(pvpTitle), l -> l.align("left").growX()).row();
                    if (!statsPart.isEmpty() || !legacyPart.isEmpty()) {
                        String secondLine = (statsPart.startsWith("  [darkgray]|[]  ") ? statsPart.substring(17) : statsPart) + legacyPart;
                        top.label(Text.raw(secondLine), l -> l.align("left").growX());
                    }
                } else {
                    top.label(Text.raw(pvpTitle + statsPart + legacyPart), l -> l.align("left").growX());
                }
            })).row();

            // Progress bar
            c.add(Ui.table(bar -> {
                bar.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(2f));
                String progressLine;
                if (!league.hasNext()) {
                    progressLine = local != null ? local.t("player-stats-max-league") : "[gold]★ MAX LEAGUE ACHIEVED ★[]";
                } else {
                    String progressBar = renderRatingLeagueProgressBar(league, model.pvpRating(), model.isMobile() ? 10 : 14);
                    int remaining = nextLeague.minimumRating() - model.pvpRating();
                    String nextLeagueName = local != null ? local.t(nextLeague.localizationKey()) : nextLeague.name();
                    String progressText = local != null
                            ? local.t("player-stats-league-elo-left", args("elo", Math.max(0, remaining), "league", nextLeague.icon() + " " + nextLeagueName))
                            : Math.max(0, remaining) + " ELO to " + nextLeague.icon() + " " + nextLeagueName;
                    progressLine = progressBar + "  " + progressText;
                }

                bar.label(Text.raw(progressLine), l -> l.align("left").growX());
            })).row();
        })).row();

        // --- Card 3: Hexed Rank Progression Card ---
        body.add(Ui.table(c -> {
            c.background("button");
            c.margin(10f);
            c.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

            HexedRank rank = model.hexedRank() != null ? model.hexedRank() : HexedRank.values()[0];
            String rankName = local != null ? local.t("hexed-ranks-" + rank.name()) : rank.name();
            String tag = rank.tag != null ? rank.tag : "";

            c.add(Ui.table(top -> {
                top.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(4f));
                String topRankStr = model.hexedTopRank() != null ? "#" + model.hexedTopRank() : "-";
                String hexedRankLbl = local != null ? local.t("player-stats-hexed-rank") : "[gray]Legacy Hexed:[]";
                String topLbl = local != null ? local.t("player-stats-hexed-leaderboard") : "[gray]Hexed Top:[]";
                String ptsLbl = local != null ? local.t("player-stats-hexed-points", args("points", model.hexedPoints())) : "(" + model.hexedPoints() + " pts)";

                top.label(Text.raw(hexedRankLbl + " " + tag + " [white]" + rankName + "[] " + ptsLbl
                        + "   " + topLbl + " [accent]" + topRankStr + "[]"), l -> l.align("left").growX());
            })).row();

            // Progress bar
            c.add(Ui.table(bar -> {
                bar.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(2f));
                String progressLine;
                if (!rank.hasNext()) {
                    progressLine = local != null ? local.t("player-stats-max-rank") : "[gold]★ MAX RANK ACHIEVED ★[]";
                } else {
                    String progressBar = renderHexedProgressBar(rank, model.hexedPoints(), model.isMobile() ? 10 : 14);
                    int remaining = rank.next.requirements.wins() - model.hexedPoints();
                    String nextRankName = local != null ? local.t("hexed-ranks-" + rank.next.name()) : rank.next.name();
                    String progressText = local != null
                            ? local.t("player-stats-hexed-wins-left", args("wins", Math.max(0, remaining), "rank", nextRankName))
                            : Math.max(0, remaining) + " wins to " + nextRankName;
                    progressLine = progressBar + "  " + progressText;
                }

                bar.label(Text.raw(progressLine), l -> l.align("left").growX());
            })).row();
        })).row();

        // --- Card 3: Highlights Quick Preview ---
        body.add(Ui.table(c -> {
            c.background("button");
            c.margin(10f);
            c.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

            if (model.isStatsLoading()) {
                c.label(Text.raw(local != null ? local.t("player-stats-loading") : "[lightgray]Loading telemetry...[]"), l -> l.align("center").growX());
            } else if (model.stats() != null) {
                AggregatedPlayerStats overall = model.stats().overall();
                NumberFormat nf = NumberFormat.getIntegerInstance(local != null ? local.getLocale() : Locale.ROOT);

                c.add(Ui.table(row -> {
                    row.layout(l -> l.width(metrics.cardInnerWidth()));
                    String gamesLbl = local != null ? local.t("player-stats-total-games") : "[gray]Total games:[]";
                    String winsLbl = local != null ? local.t("player-stats-victories") : "[gray]Victories:[]";
                    String blocksLbl = local != null ? local.t("player-stats-blocks-built") : "[gray]Blocks built:[]";

                    if (model.isMobile()) {
                        row.label(Text.raw(gamesLbl + " [white]" + nf.format(overall.gamesPlayed()) + "[]  [darkgray]|[]  "
                                + winsLbl + " [lime]" + nf.format(overall.gamesWon()) + "[] [gray](" + overall.winRatePercent() + "%)[]"), l -> l.align("left").growX()).row();
                        row.label(Text.raw(blocksLbl + " [lime]" + nf.format(overall.blocksBuilt()) + "[]"), l -> l.align("left").growX());
                    } else {
                        row.label(Text.raw(gamesLbl + " [white]" + nf.format(overall.gamesPlayed()) + "[]  [darkgray]|[]  "
                                + winsLbl + " [lime]" + nf.format(overall.gamesWon()) + "[] [gray](" + overall.winRatePercent() + "%)[]  [darkgray]|[]  "
                                + blocksLbl + " [lime]" + nf.format(overall.blocksBuilt()) + "[]"), l -> l.align("left").growX());
                    }
                })).row();
            } else {
                c.label(Text.raw(local != null ? local.t("player-stats-no-stats") : "[gray]No match telemetry recorded yet.[]"), l -> l.align("center").growX());
            }
        })).row();
    }

    // =========================================================================
    // Tab 2: STATS
    // =========================================================================

    private void renderStatsTab(Ui.TableBuilder body, ProfileModel model, UiMetrics metrics, Localization local) {
        NumberFormat nf = NumberFormat.getIntegerInstance(local != null ? local.getLocale() : Locale.ROOT);

        if (model.isStatsLoading()) {
            body.add(Ui.table(c -> {
                c.background("button");
                c.margin(14f);
                c.layout(l -> l.width(metrics.cardWidth()).padBottom(8f));
                c.label(Text.raw(local != null ? local.t("player-stats-loading") : "[lightgray]Loading telemetry...[]"), l -> l.align("center").growX());
            })).row();
            return;
        }

        PlayerStatsOverview overview = model.stats() != null ? model.stats() : PlayerStatsOverview.EMPTY;
        AggregatedPlayerStats overall = overview.overall();

        // --- Card 1: Overall Performance Banner ---
        body.add(Ui.table(c -> {
            c.background("button");
            c.margin(10f);
            c.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

            c.add(Ui.table(row -> {
                row.layout(l -> l.width(metrics.cardInnerWidth()));
                String gamesVal = local != null
                        ? local.t("player-stats-games-played-value", args("count", nf.format(overall.gamesPlayed())))
                        : overall.gamesPlayed() + " games";
                String winsVal = local != null
                        ? local.t("player-stats-victories-value", args("wins", nf.format(overall.gamesWon()), "winRate", overall.winRatePercent()))
                        : overall.gamesWon() + " wins | " + overall.winRatePercent() + "% win rate";

                row.label(Text.raw("[accent]■ " + (local != null ? local.t("player-stats-total-games") : "Overall Performance") + "[]  "
                        + gamesVal + "  [darkgray]|[]  " + winsVal), l -> l.align("left").growX());
            })).row();
        })).row();

        // --- Card 2: Game Modes Breakdown ---
        body.add(Ui.table(c -> {
            c.background("button");
            c.margin(10f);
            c.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

            // MiniPvP
            c.add(Ui.table(pvpRow -> {
                pvpRow.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(4f));
                RatingLeague league = RatingLeague.fromRating(model.pvpRating());
                String leagueName = local != null ? local.t(league.localizationKey()) : league.name();
                int matches = model.pvpMatches();
                int wins = model.pvpWins();
                int winRate = matches <= 0 ? 0 : Math.round((wins * 100.0f) / matches);
                String pvpSummary = matches > 0
                        ? "[white]" + nf.format(matches) + "[] [gray]matches[] | [lime]" + nf.format(wins) + "[] [gray]wins (" + winRate + "%)[]"
                        : (local != null ? local.t("player-menu-player-no-mode-stats") : "[gray]no data[]");

                String legacyText = model.legacyPvpRating() > 0
                        ? "  [darkgray]|[]  [gray]" + (local != null ? local.t("player-stats-legacy-pvp-rating") : "Legacy:") + "[] [sky]" + model.legacyPvpRating() + "[]"
                        : "";

                if (model.isMobile()) {
                    pvpRow.label(Text.raw("[red]" + Iconc.modePvp + " " + (local != null ? local.t("player-stats-pvp-summary") : "MiniPvP:") + "[] "
                            + league.icon() + " [sky]" + model.pvpRating() + "[] [gray](" + leagueName + ")[]" + legacyText), l -> l.align("left").growX()).row();
                    pvpRow.label(Text.raw(pvpSummary), l -> l.align("left").growX());
                } else {
                    pvpRow.label(Text.raw("[red]" + Iconc.modePvp + " " + (local != null ? local.t("player-stats-pvp-summary") : "MiniPvP:") + "[] " + pvpSummary
                            + "  [gray]—[] " + league.icon() + " [sky]" + model.pvpRating() + "[] [gray](" + leagueName + ")[]" + legacyText), l -> l.align("left").growX());
                }
            })).row();

            // Survival
            c.add(Ui.table(survRow -> {
                survRow.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(4f));
                ModeStatsSummary surv = overview.survival();
                String survSummary = surv.hasData()
                        ? "[white]" + nf.format(surv.gamesPlayed()) + "[] [gray]runs[] | "
                        + (local != null ? local.t("player-stats-waves-summary", args("best", nf.format(surv.bestWave()), "avg", nf.format(surv.averageWave()))) : "waves: max " + surv.bestWave() + ", avg " + surv.averageWave())
                        : (local != null ? local.t("player-menu-player-no-mode-stats") : "[gray]no data[]");
                survRow.label(Text.raw("[green]" + Iconc.defense + " " + (local != null ? local.t("player-stats-survival-summary") : "Survival:") + "[] " + survSummary), l -> l.align("left").growX());
            })).row();

            // Legacy Hexed
            c.add(Ui.table(hexRow -> {
                hexRow.layout(l -> l.width(metrics.cardInnerWidth()));
                ModeStatsSummary hex = overview.hexed();
                String hexSummary = hex.hasData()
                        ? "[white]" + nf.format(hex.gamesPlayed()) + "[] [gray]matches[] | [lime]" + nf.format(hex.gamesWon()) + "[] [gray]top-1[] | "
                        + (local != null ? local.t("player-stats-hexed-top-placement", args("best", hex.bestPlacement(), "top3", hex.top3Finishes())) : "best #" + hex.bestPlacement() + ", top-3: " + hex.top3Finishes())
                        : (local != null ? local.t("player-menu-player-no-mode-stats") : "[gray]no data[]");
                hexRow.label(Text.raw("[purple]" + Iconc.star + " " + (local != null ? local.t("player-stats-hexed-summary") : "Legacy Hexed:") + "[] " + hexSummary), l -> l.align("left").growX());
            })).row();
        })).row();

        // --- Card 3: Combat & Construction Efficiency ---
        body.add(Ui.table(c -> {
            c.background("button");
            c.margin(10f);
            c.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

            c.add(Ui.table(titleRow -> {
                titleRow.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(4f));
                titleRow.label(Text.raw("[accent]■ " + (local != null ? local.t("player-stats-combat-efficiency") : "Combat & Construction Efficiency") + "[]"), l -> l.align("left").growX());
            })).row();

            // Blocks numbers
            c.add(Ui.table(blocksRow -> {
                blocksRow.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(4f));
                String builtLbl = local != null ? local.t("player-stats-blocks-built") : "Built:";
                String deconLbl = local != null ? local.t("player-stats-blocks-deconstructed") : "Decon:";
                String destLbl = local != null ? local.t("player-stats-blocks-destroyed") : "Destroyed:";

                blocksRow.label(Text.raw(builtLbl + " [lime]" + Iconc.hammer + " " + nf.format(overall.blocksBuilt()) + "[]  [darkgray]|[]  "
                        + deconLbl + " [orange]" + Iconc.refresh + " " + nf.format(overall.blocksDeconstructed()) + "[]  [darkgray]|[]  "
                        + destLbl + " [scarlet]" + Iconc.warning + " " + nf.format(overall.blocksDestroyed()) + "[]"), l -> l.align("left").growX());
            })).row();

            // 10-bar ratio visualization
            c.add(Ui.table(ratioRow -> {
                ratioRow.layout(l -> l.width(metrics.cardInnerWidth()).padBottom(4f));
                String ratioBar = renderBlockRatioBar(overall.blocksBuilt(), overall.blocksDeconstructed(), overall.blocksDestroyed(), model.isMobile() ? 12 : 16);
                String ratioLegend = local != null ? local.t("player-stats-ratio-legend") : "[lightgray]Build / Decon / Destroy Ratio[]";
                ratioRow.label(Text.raw(ratioBar + "  " + ratioLegend), l -> l.align("left").growX());
            })).row();

            // Units stats (if produced or destroyed)
            if (overall.unitsProduced() > 0 || overall.unitsDestroyed() > 0) {
                c.add(Ui.table(unitsRow -> {
                    unitsRow.layout(l -> l.width(metrics.cardInnerWidth()));
                    unitsRow.label(Text.raw("[gray]Units:[] [sky]" + Iconc.units + " " + nf.format(overall.unitsProduced()) + " produced[]  [darkgray]|[]  [scarlet]" + Iconc.cancel + " "
                            + nf.format(overall.unitsDestroyed()) + " lost[]"), l -> l.align("left").growX());
                })).row();
            }
        })).row();
    }

    // =========================================================================
    // Tab 3: PLAYERS (Live Online Server Roster)
    // =========================================================================

    private void renderPlayersTab(Ui.TableBuilder body, ProfileModel model, UiMetrics metrics, Localization local) {
        List<OnlinePlayerRow> onlineList = model.onlinePlayers() != null ? model.onlinePlayers() : List.of();
        int totalPlayers = onlineList.size();
        int perPage = PLAYERS_PER_PAGE;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalPlayers / perPage));
        int validPage = Math.clamp(model.playersPage(), 1, totalPages);
        int skip = (validPage - 1) * perPage;

        List<OnlinePlayerRow> pagePlayers = onlineList.stream()
                .skip(skip)
                .limit(perPage)
                .toList();

        // 1. Controls Toolbar: Filter + Pagination + Refresh
        body.add(Ui.table(tb -> {
            tb.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

            // Admin Filter Button
            String filterLabel = switch (model.adminFilter()) {
                case ALL -> local != null ? local.t("player-stats-filter-all") : "Filter: All";
                case ADMINS_ONLY -> local != null ? local.t("player-stats-filter-admins") : "Filter: Admins";
                case NON_ADMINS -> local != null ? local.t("player-stats-filter-non-admins") : "Filter: Non-Admins";
            };
            tb.button(Text.raw(filterLabel), "action:filter_cycle", b -> b
                    .style("cleart")
                    .layout(l -> l.height(34f).padRight(4f)));

            // Pagination Controls
            if (validPage > 1) {
                tb.button(Text.raw("[accent]< Prev[]"), "action:page:" + (validPage - 1), b -> b
                        .style("cleart")
                        .layout(l -> l.height(34f).padRight(4f)));
            }
            tb.label(Text.raw("[white]" + validPage + " / " + totalPages + "[]"), l -> l.align("center").padRight(4f));
            if (validPage < totalPages) {
                tb.button(Text.raw("[accent]Next >[]"), "action:page:" + (validPage + 1), b -> b
                        .style("cleart")
                        .layout(l -> l.height(34f).padRight(4f)));
            }

            tb.label(Text.raw(""), l -> l.growX()); // Spacer

            // Refresh button
            tb.button(Text.raw("[sky]" + Iconc.refresh + " " + (local != null ? local.t("player-stats-refresh") : "Refresh") + "[]"),
                    "action:refresh_players", b -> b
                            .style("cleart")
                            .layout(l -> l.height(34f)));
        })).row();

        // 2. Online Player Cards
        if (pagePlayers.isEmpty()) {
            body.add(Ui.table(c -> {
                c.background("button");
                c.margin(14f);
                c.layout(l -> l.width(metrics.cardWidth()));
                c.label(Text.raw(local != null ? local.t("player-menu-players-empty") : "No online players found"), l -> l.align("center").growX());
            })).row();
            return;
        }

        for (OnlinePlayerRow p : pagePlayers) {
            body.add(Ui.table(row -> {
                row.background("button");
                row.margin(8f);
                row.layout(l -> l.width(metrics.cardWidth()).padBottom(4f));

                // Left: Status dot + Badges + Nickname + PID
                row.add(Ui.table(info -> {
                    info.layout(l -> l.width(metrics.cardInnerWidth() - 90f).align("left"));

                    StringBuilder sb = new StringBuilder("[lime]●[] ");
                    if (p.isAdmin()) {
                        sb.append("[scarlet]<" + Iconc.admin + ">[] ");
                    }
                    if (p.activeBadge() != null && !p.activeBadge().isBlank()) {
                        Badge b = Badge.byId(p.activeBadge());
                        if (b != null) {
                            sb.append(PlayerSettingsUiController.renderBadgeTagExact(b, p.badgeSymbolColorMode(), p.playerColorHex())).append(" ");
                        }
                    }

                    String nick = p.customNickname() != null && !p.customNickname().isBlank()
                            ? p.customNickname()
                            : (p.nickname() != null && !p.nickname().isBlank() ? p.nickname() : "Player");
                    if (nick.startsWith("[")) {
                        sb.append(nick);
                    } else {
                        sb.append("[white]").append(nick);
                    }
                    sb.append(" [gray]#").append(p.pid()).append("[]");

                    info.label(Text.raw(sb.toString()), l -> l.align("left").growX());
                }));

                // Right: Inspect button
                row.button(Text.raw("[accent]" + (local != null ? local.t("player-stats-inspect") : "Inspect →") + "[]"),
                        "action:inspect:" + p.uuid(), b -> b
                                .style("cleart")
                                .layout(l -> l.height(32f)));
            })).row();
        }
    }
}
