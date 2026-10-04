package org.xcore.plugin.ui.menu;

import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.gamemode.hexed.HexedRanks.HexedRank;
import org.xcore.plugin.integration.profile.ProfileSection;
import org.xcore.plugin.integration.profile.ProfileSectionView;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.plugin.ui.kit.TextWidth;
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
import java.util.Objects;
import java.util.Set;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.ui.kit.Kit.GAP;

/**
 * A player's profile ({@code /stats}) and the list of who is online ({@code /players}) as tabs of
 * one dialog: who the player is, what the modes say about them, their figures, and the players
 * on the server as rows that open their profiles. Laid out once per {@link Screen}.
 */
public class PlayerProfileUiController implements UiController<PlayerProfileUiController.ProfileModel, PlayerProfileUiController.ProfileEvent> {

    public static final int PLAYERS_PER_PAGE = 8;

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
            List<ProfileSectionView> sections,
            int hexedPoints,
            HexedRank hexedRank,
            Integer hexedTopRank,
            PlayerStatsOverview stats,
            boolean isStatsLoading,

            // Viewer & Context
            boolean isSelf,
            boolean isViewerAdmin,
            Tab tab,
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
                    unlockedBadges, playerColorHex, sections, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, newTab,
                    viewingFromPlayersTab, adminFilter, playersPage, totalOnlineCount, onlinePlayers, "");
        }

        /** What has been read about the target so far, {@code null} while it is still loading. */
        public ProfileDetails details() {
            return isStatsLoading ? null : new ProfileDetails(stats, hexedTopRank, sections);
        }

        public ProfileModel withDetails(ProfileDetails details) {
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, details.sections(), hexedPoints, hexedRank, details.hexedTopRank(),
                    details.stats(), false, isSelf, isViewerAdmin, tab,
                    viewingFromPlayersTab, adminFilter, playersPage, totalOnlineCount, onlinePlayers, feedbackMessage);
        }

        /** @param details what is already loaded about {@code target}, {@code null} while it is being read */
        public ProfileModel withTarget(PlayerData target, boolean isOnline, String colorHex,
                                      ProfileDetails details, boolean fromPlayersTab) {
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
                    details != null ? details.sections() : List.of(),
                    target.hexedPoints,
                    target.hexedRank(),
                    details != null ? details.hexedTopRank() : null,
                    details != null ? details.stats() : null,
                    details == null,
                    Objects.equals(viewerUuid, target.uuid),
                    isViewerAdmin,
                    Tab.OVERVIEW,
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
                    unlockedBadges, playerColorHex, sections, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, tab,
                    viewingFromPlayersTab, adminFilter, newPage, totalOnlineCount, onlinePlayers, feedbackMessage);
        }

        public ProfileModel withAdminFilter(AdminFilter filter, List<OnlinePlayerRow> players) {
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, sections, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, tab,
                    viewingFromPlayersTab, filter, 1, totalOnlineCount, players, feedbackMessage);
        }

        public ProfileModel withRefreshedPlayers(List<OnlinePlayerRow> players, int totalCount) {
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, sections, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, tab,
                    viewingFromPlayersTab, adminFilter, 1, totalCount, players, feedbackMessage);
        }

        public ProfileModel withRefreshedPlayersPreservingPage(List<OnlinePlayerRow> players, int totalCount) {
            int maxPage = Math.max(1, (int) Math.ceil((double) players.size() / PLAYERS_PER_PAGE));
            int page = Math.clamp(playersPage, 1, maxPage);
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, sections, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, tab,
                    viewingFromPlayersTab, adminFilter, page, totalCount, players, feedbackMessage);
        }

        public ProfileModel withFeedback(String msg) {
            return new ProfileModel(viewerUuid, targetUuid, pid, nickname, customNickname, description, isTargetAdmin,
                    isTargetOnline, createdModelTime, totalPlayTime, activeBadge, badgeSymbolColorMode,
                    unlockedBadges, playerColorHex, sections, hexedPoints, hexedRank, hexedTopRank,
                    stats, isStatsLoading, isSelf, isViewerAdmin, tab,
                    viewingFromPlayersTab, adminFilter, playersPage, totalOnlineCount, onlinePlayers, msg);
        }
    }

    public sealed interface ProfileEvent {
        record SelectTab(Tab tab) implements ProfileEvent {}
        record DetailsLoaded(String targetUuid, ProfileDetails details) implements ProfileEvent {}
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

    private static final String SEPARATOR = "  [darkgray]|[]  ";

    private final PlayerMenu playerMenu;
    private final AuditHistoryMenu auditHistoryMenu;
    private final SessionService sessionService;
    private final PlayerDisplayService playerDisplayService;
    private final Session session;
    private final PlayerData initialTargetData;

    public PlayerProfileUiController(PlayerMenu playerMenu,
                                     AuditHistoryMenu auditHistoryMenu,
                                     SessionService sessionService,
                                     PlayerDisplayService playerDisplayService,
                                     Session session,
                                     PlayerData targetData) {
        this.playerMenu = playerMenu;
        this.auditHistoryMenu = auditHistoryMenu;
        this.sessionService = sessionService;
        this.playerDisplayService = playerDisplayService;
        this.session = session;
        this.initialTargetData = targetData;
    }

    /** @param details what is already loaded about the target, {@code null} while it is being read */
    public ProfileModel createInitialModel(Tab tab, ProfileDetails details) {
        return createModel(session, initialTargetData, tab, details, sessionService, playerDisplayService);
    }

    public static ProfileModel createModel(Session session,
                                           PlayerData targetData,
                                           Tab initialTab,
                                           ProfileDetails details,
                                           SessionService sessionService,
                                           PlayerDisplayService playerDisplayService) {
        Objects.requireNonNull(targetData, "targetData");
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
                details != null ? details.sections() : List.of(),
                targetData.hexedPoints,
                targetData.hexedRank(),
                details != null ? details.hexedTopRank() : null,
                details != null ? details.stats() : null,
                details == null,
                isSelf,
                isViewerAdmin,
                initialTab != null ? initialTab : Tab.OVERVIEW,
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
        return createInitialModel(tab, null);
    }

    @Override
    public UpdateResult<ProfileModel> update(ProfileModel model, ProfileEvent event, ControllerContext ctx) {
        return switch (event) {
            case ProfileEvent.SelectTab(Tab tab) ->
                    UpdateResult.rerender(model.withTab(tab));

            case ProfileEvent.DetailsLoaded(String targetUuid, ProfileDetails details) -> {
                if (Objects.equals(model.targetUuid(), targetUuid) && details != null) {
                    yield UpdateResult.rerender(model.withDetails(details));
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
                ProfileModel newModel = model.withTarget(target, isOnline, colorHex, null, true);
                if (playerMenu != null) {
                    playerMenu.loadDetails(session, target);
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
                    final ProfileDetails cached = model.details();

                    session.pushHistory(() -> {
                        playerMenu.openProfileUi(session, target, returnTab, cached);
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
                    final ProfileDetails cached = model.details();

                    session.pushHistory(() -> {
                        playerMenu.openProfileUi(session, target, returnTab, cached);
                    });
                    auditHistoryMenu.actions(session.data.uuid, target);
                }
                yield UpdateResult.close(model);
            }

            case ProfileEvent.Close() -> UpdateResult.close(model);
        };
    }

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
        return Screen.each(screen -> window(model, screen));
    }

    /** The window as one {@link Screen} sees it. */
    VNode window(ProfileModel model, Screen screen) {
        float width = screen.width();
        Localization local = session != null ? session.locale() : null;
        boolean players = model.tab() == Tab.PLAYERS;

        return Kit.window(window -> {
            String title = "[orange]" + Iconc.players + "[] [white]"
                    + t(local, players ? "player-menu-players-title" : "player-stats-title") + "[]";
            String status = players ? ""
                    : "\n" + identity(model, local) + "  "
                    + t(local, model.isTargetOnline() ? "player-stats-status-online" : "player-stats-status-offline");
            window.add(Kit.header(width, title + status)).row();

            window.add(Kit.tabs(width, "profile_tabs", List.of(
                    new Kit.Tab(Iconc.admin, t(local, "player-stats-tab-overview"), "action:tab:overview",
                            accent(Tab.OVERVIEW), model.tab() == Tab.OVERVIEW),
                    new Kit.Tab(Iconc.chartBar, t(local, "player-stats-tab-stats"), "action:tab:stats",
                            accent(Tab.STATS), model.tab() == Tab.STATS),
                    new Kit.Tab(Iconc.players, local != null
                            ? local.t("player-stats-tab-players", args("count", model.totalOnlineCount()))
                            : "Online (" + model.totalOnlineCount() + ")", "action:tab:players",
                            accent(Tab.PLAYERS), players)))).row();
            window.add(Kit.line(width, accent(model.tab()))).row();

            Kit.body(window, screen, body -> {
                switch (model.tab()) {
                    case OVERVIEW -> overview(body, model, screen, local);
                    case STATS -> stats(body, model, screen, local);
                    case PLAYERS -> players(body, model, screen, local);
                }
            });

            if (players) {
                int pages = Math.max(1, (int) Math.ceil((double) model.onlinePlayers().size() / PLAYERS_PER_PAGE));
                int page = Math.clamp(model.playersPage(), 1, pages);
                window.add(Kit.pager(width, page + " / " + pages,
                        page > 1 ? "action:page:" + (page - 1) : null,
                        page < pages ? "action:page:" + (page + 1) : null,
                        "action:refresh_players")).row();
            }

            if (model.feedbackMessage() != null && !model.feedbackMessage().isBlank()) {
                window.add(Ui.table(feedback -> {
                    feedback.layout(l -> l.padTop(GAP));
                    feedback.add(Kit.feedback(width, model.feedbackMessage(), false));
                })).row();
            }

            List<Kit.Action> actions = actions(model, local);
            if (!actions.isEmpty()) {
                window.add(Ui.table(bar -> {
                    bar.layout(l -> l.padTop(GAP));
                    bar.add(Kit.actions(width, actions));
                })).row();
            }
        });
    }

    private static Accent accent(Tab tab) {
        return switch (tab) {
            case OVERVIEW -> Accent.GOLD;
            case STATS -> Accent.BLUE;
            case PLAYERS -> Accent.GREEN;
        };
    }

    /** The way back, and what the viewer may do with the profile on screen. The client's own button closes the dialog. */
    private List<Kit.Action> actions(ProfileModel model, Localization local) {
        List<Kit.Action> actions = new ArrayList<>();
        if (session != null && session.hasHistory()) {
            actions.add(new Kit.Action("[lightgray]" + Iconc.left + "[] " + t(local, "back"), "action:back"));
        } else if (model.viewingFromPlayersTab() && model.tab() != Tab.PLAYERS) {
            actions.add(new Kit.Action("[lightgray]" + Iconc.left + "[] " + t(local, "back"), "action:back_to_players"));
        }
        if (model.tab() != Tab.PLAYERS) {
            if (model.isSelf() || model.isViewerAdmin()) {
                actions.add(new Kit.Action("[accent]" + Iconc.settings + "[] " + t(local, "player-stats-btn-settings"),
                        "action:settings"));
            }
            if (model.isViewerAdmin()) {
                actions.add(new Kit.Action("[sky]" + Iconc.list + "[] " + t(local, "player-stats-btn-audit"),
                        "action:audit_history"));
                actions.add(new Kit.Action("[scarlet]" + Iconc.hammer + "[] " + t(local, "audit-menu-actions-open"),
                        "action:audit_actions"));
            }
        }
        return actions;
    }

    private static String t(Localization local, String key) {
        return local != null ? local.t(key) : key;
    }

    /** The admin mark, the badge, the name in its own colours and the player's number. */
    private static String identity(ProfileModel model, Localization local) {
        StringBuilder identity = new StringBuilder();
        if (model.isTargetAdmin()) {
            identity.append("[scarlet]<").append(Iconc.admin).append(' ').append(t(local, "admin")).append(">[] ");
        }
        appendName(identity, model.activeBadge(), model.badgeSymbolColorMode(), model.playerColorHex(),
                model.customNickname(), model.nickname(), "[accent]");
        return identity.append(" [gray]#").append(model.pid()).append("[]").toString();
    }

    private static void appendName(StringBuilder text, String badgeId, String badgeColorMode, String colorHex,
                                   String customNickname, String nickname, String color) {
        if (badgeId != null && !badgeId.isBlank()) {
            Badge badge = Badge.byId(badgeId);
            if (badge != null) {
                text.append(PlayerSettingsUiController.renderBadgeTagExact(badge, badgeColorMode, colorHex)).append(' ');
            }
        }
        String name = customNickname != null && !customNickname.isBlank()
                ? customNickname
                : (nickname != null && !nickname.isBlank() ? nickname : "Player");
        // A name that brings its own colours keeps them.
        text.append(name.startsWith("[") ? "" : color).append(name).append("[]");
    }

    // =========================================================================
    // Tab 1: OVERVIEW
    // =========================================================================

    /** The mode sections in the viewer's language; a section that fails to render is left out. */
    private static List<ProfileSection> renderSections(ProfileModel model, Localization local) {
        List<ProfileSection> sections = new ArrayList<>();
        if (local == null) {
            return sections;
        }
        for (ProfileSectionView view : model.sections()) {
            try {
                sections.add(view.render(local));
            } catch (Exception ignored) {
                // One mode's section must not take the whole profile down.
            }
        }
        return sections;
    }

    private void overview(Ui.TableBuilder body, ProfileModel model, Screen screen, Localization local) {
        float card = screen.card();
        List<VNode> left = new ArrayList<>();
        List<VNode> right = new ArrayList<>();

        // Who this is: the bio, when the account appeared and how long it has played.
        left.add(Kit.card(card, Accent.GOLD, Iconc.players + " " + identity(model, local), (content, inner) -> {
            String bio = model.description() != null && !model.description().isBlank()
                    ? "[lightgray]\"" + model.description().trim() + "[lightgray]\"[]"
                    : t(local, "player-stats-no-bio");
            content.add(Kit.text(bio, inner)).row();
            content.add(Ui.table(facts -> {
                facts.layout(l -> l.padTop(GAP));
                facts.add(Kit.text(t(local, "player-stats-account-created") + " [white]"
                        + formatTimestamp(model.createdModelTime()) + "[]\n"
                        + t(local, "player-stats-play-time") + " [white]"
                        + formatDuration(model.totalPlayTime(), local) + "[]", inner));
            })).row();
        }));

        // What the player has done so far, in three figures.
        if (model.isStatsLoading()) {
            left.add(Kit.note(card, t(local, "player-stats-loading")));
        } else if (model.stats() != null) {
            AggregatedPlayerStats overall = model.stats().overall();
            NumberFormat nf = NumberFormat.getIntegerInstance(local != null ? local.getLocale() : Locale.ROOT);
            left.add(Kit.card(card, (content, inner) -> content.add(Kit.text(
                    t(local, "player-stats-total-games") + " [white]" + nf.format(overall.gamesPlayed()) + "[]\n"
                            + t(local, "player-stats-victories") + " [lime]" + nf.format(overall.gamesWon())
                            + "[] [gray](" + overall.winRatePercent() + "%)[]\n"
                            + t(local, "player-stats-blocks-built") + " [lime]" + nf.format(overall.blocksBuilt()) + "[]",
                    inner)).row()));
        } else {
            left.add(Kit.note(card, t(local, "player-stats-no-stats")));
        }

        // One card per section the server's modes contribute (rating ladders).
        for (ProfileSection section : renderSections(model, local)) {
            right.add(Kit.card(card, (content, inner) -> {
                StringBuilder text = new StringBuilder(section.headline());
                if (!section.details().isEmpty()) {
                    text.append('\n').append(String.join(SEPARATOR, section.details()));
                }
                for (String line : section.lines()) {
                    text.append('\n').append(line);
                }
                content.add(Kit.text(text.toString(), inner)).row();
            }));
        }

        // The rank of the old Hexed mode and the way to the next one.
        right.add(Kit.card(card, (content, inner) -> {
            HexedRank rank = model.hexedRank() != null ? model.hexedRank() : HexedRank.values()[0];
            String rankName = local != null ? local.t("hexed-ranks-" + rank.name()) : rank.name();
            String points = local != null
                    ? local.t("player-stats-hexed-points", args("points", model.hexedPoints()))
                    : "(" + model.hexedPoints() + " pts)";
            String progress;
            if (!rank.hasNext()) {
                progress = t(local, "player-stats-max-rank");
            } else {
                int remaining = Math.max(0, rank.next.requirements.wins() - model.hexedPoints());
                String nextRank = local != null ? local.t("hexed-ranks-" + rank.next.name()) : rank.next.name();
                progress = renderHexedProgressBar(rank, model.hexedPoints(), 14) + "  " + (local != null
                        ? local.t("player-stats-hexed-wins-left", args("wins", remaining, "rank", nextRank))
                        : remaining + " wins to " + nextRank);
            }
            content.add(Kit.text(t(local, "player-stats-hexed-rank") + " " + (rank.tag != null ? rank.tag : "")
                    + " [white]" + rankName + "[] " + points + "\n"
                    + t(local, "player-stats-hexed-leaderboard") + " [accent]"
                    + (model.hexedTopRank() != null ? "#" + model.hexedTopRank() : "-") + "[]\n"
                    + progress, inner)).row();
        }));

        body.add(Kit.columns(screen, left, right)).row();
    }

    // =========================================================================
    // Tab 2: STATS
    // =========================================================================

    private void stats(Ui.TableBuilder body, ProfileModel model, Screen screen, Localization local) {
        if (model.isStatsLoading()) {
            body.add(Kit.note(screen.cards(), t(local, "player-stats-loading"))).row();
            return;
        }
        float card = screen.card();
        NumberFormat nf = NumberFormat.getIntegerInstance(local != null ? local.getLocale() : Locale.ROOT);
        PlayerStatsOverview overview = model.stats() != null ? model.stats() : PlayerStatsOverview.EMPTY;
        AggregatedPlayerStats overall = overview.overall();
        List<VNode> left = new ArrayList<>();
        List<VNode> right = new ArrayList<>();

        // Every game of every mode together.
        left.add(Kit.card(card, (content, inner) -> content.add(Kit.text(
                t(local, "player-stats-total-games") + " [white]" + nf.format(overall.gamesPlayed()) + "[]\n"
                        + (local != null
                        ? local.t("player-stats-victories-value",
                        args("wins", nf.format(overall.gamesWon()), "winRate", overall.winRatePercent()))
                        : overall.gamesWon() + " wins | " + overall.winRatePercent() + "% win rate"),
                inner)).row()));

        // Mode by mode: the rating ladders first, then the modes that keep no rating.
        left.add(Kit.card(card, (content, inner) -> {
            List<String> modes = new ArrayList<>();
            for (ProfileSection section : renderSections(model, local)) {
                modes.add(section.details().isEmpty() ? section.headline()
                        : section.headline() + "\n  [lightgray]\u21b3[] " + String.join(SEPARATOR, section.details()));
            }
            ModeStatsSummary survival = overview.survival();
            modes.add("[green]" + Iconc.defense + "[] " + t(local, "player-stats-survival-summary") + " "
                    + (survival.hasData()
                    ? games(local, nf, survival.gamesPlayed()) + SEPARATOR + (local != null
                    ? local.t("player-stats-waves-summary",
                    args("best", nf.format(survival.bestWave()), "avg", nf.format(survival.averageWave())))
                    : "waves: max " + survival.bestWave() + ", avg " + survival.averageWave())
                    : t(local, "player-menu-player-no-mode-stats")));
            ModeStatsSummary hexed = overview.hexed();
            modes.add("[purple]" + Iconc.star + "[] " + t(local, "player-stats-hexed-summary") + " "
                    + (hexed.hasData()
                    ? games(local, nf, hexed.gamesPlayed()) + SEPARATOR
                    + t(local, "player-stats-victories") + " [lime]" + nf.format(hexed.gamesWon()) + "[]" + SEPARATOR
                    + (local != null
                    ? local.t("player-stats-hexed-top-placement",
                    args("best", hexed.bestPlacement(), "top3", hexed.top3Finishes()))
                    : "best #" + hexed.bestPlacement() + ", top-3: " + hexed.top3Finishes())
                    : t(local, "player-menu-player-no-mode-stats")));
            for (int i = 0; i < modes.size(); i++) {
                String mode = modes.get(i);
                boolean first = i == 0;
                content.add(Ui.table(line -> {
                    line.layout(l -> l.padTop(first ? 0f : GAP));
                    line.add(Kit.text(mode, inner));
                })).row();
            }
        }));

        // What was built, taken apart and lost.
        right.add(Kit.card(card, Accent.BLUE, Iconc.hammer + " " + t(local, "player-stats-combat-efficiency"),
                (content, inner) -> {
                    StringBuilder text = new StringBuilder()
                            .append(t(local, "player-stats-blocks-built")).append(" [lime]")
                            .append(nf.format(overall.blocksBuilt())).append("[]\n")
                            .append(t(local, "player-stats-blocks-deconstructed")).append(" [orange]")
                            .append(nf.format(overall.blocksDeconstructed())).append("[]\n")
                            .append(t(local, "player-stats-blocks-destroyed")).append(" [scarlet]")
                            .append(nf.format(overall.blocksDestroyed())).append("[]\n")
                            .append(renderBlockRatioBar(overall.blocksBuilt(), overall.blocksDeconstructed(),
                                    overall.blocksDestroyed(), 10))
                            .append("  ").append(t(local, "player-stats-ratio-legend"));
                    if (overall.unitsProduced() > 0 || overall.unitsDestroyed() > 0) {
                        text.append('\n').append(t(local, "player-stats-units-produced")).append(" [sky]")
                                .append(nf.format(overall.unitsProduced())).append("[]\n")
                                .append(t(local, "player-stats-units-lost")).append(" [scarlet]")
                                .append(nf.format(overall.unitsDestroyed())).append("[]");
                    }
                    content.add(Kit.text(text.toString(), inner)).row();
                }));

        body.add(Kit.columns(screen, left, right)).row();
    }

    private static String games(Localization local, NumberFormat nf, long count) {
        return local != null
                ? local.t("player-stats-games-played-value", args("count", nf.format(count)))
                : count + " games";
    }

    // =========================================================================
    // Tab 3: PLAYERS (Live Online Server Roster)
    // =========================================================================

    private void players(Ui.TableBuilder body, ProfileModel model, Screen screen, Localization local) {
        float width = screen.cards();
        List<OnlinePlayerRow> online = model.onlinePlayers() != null ? model.onlinePlayers() : List.of();
        int pages = Math.max(1, (int) Math.ceil((double) online.size() / PLAYERS_PER_PAGE));
        int page = Math.clamp(model.playersPage(), 1, pages);

        // Who is shown: everyone, the admins or the rest. A press steps to the next.
        body.add(Ui.table(filter -> {
            filter.layout(l -> l.padBottom(GAP));
            filter.add(Kit.button(new Kit.Action("[accent]" + Iconc.filter + "[] " + t(local, switch (model.adminFilter()) {
                case ALL -> "player-stats-filter-all";
                case ADMINS_ONLY -> "player-stats-filter-admins";
                case NON_ADMINS -> "player-stats-filter-non-admins";
            }), "action:filter_cycle"), width));
        })).row();

        List<OnlinePlayerRow> shown = online.stream()
                .skip((long) (page - 1) * PLAYERS_PER_PAGE)
                .limit(PLAYERS_PER_PAGE)
                .toList();
        if (shown.isEmpty()) {
            body.add(Kit.note(width, t(local, "player-menu-players-empty"))).row();
            return;
        }
        for (OnlinePlayerRow player : shown) {
            // The whole row opens the player's profile.
            body.add(Kit.row("action:inspect:" + player.uuid(), width,
                    Objects.equals(player.uuid(), model.viewerUuid()), true, (row, inner) -> {
                        StringBuilder name = new StringBuilder("[lime]\u25cf[] ");
                        if (player.isAdmin()) {
                            name.append("[scarlet]<").append(Iconc.admin).append(">[] ");
                        }
                        appendName(name, player.activeBadge(), player.badgeSymbolColorMode(), player.playerColorHex(),
                                player.customNickname(), player.nickname(), "[white]");
                        name.append(" [gray]#").append(player.pid()).append("[]");
                        row.add(Kit.text(TextWidth.fit(name.toString(), inner), inner));
                    })).row();
        }
    }
}
