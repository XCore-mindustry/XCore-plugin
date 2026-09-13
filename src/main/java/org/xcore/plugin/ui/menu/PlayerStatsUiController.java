package org.xcore.plugin.ui.menu;

import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.session.Session;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Modern, reactive player statistics and profile screen for Mindustry v160.
 *
 * <p>Features:
 * <ul>
 *   <li>Structured native Mindustry card styling with pane background and tab toggles.</li>
 *   <li>Two-column key-value metric grid (Overview, Matches, Combat & Blocks).</li>
 *   <li>In-place zero-flicker tab switching ({@code Call.menuBuilderUpdate}).</li>
 * </ul>
 */
public class PlayerStatsUiController implements UiController<PlayerStatsUiController.StatsModel, PlayerStatsUiController.StatsEvent> {

    public static final SlotKey<Object> SLOT_TAB_BODY = SlotKey.of("slot_tab_body");

    private final PlayerMenu menu;
    private final AuditHistoryMenu auditHistoryMenu;
    private final Session session;
    private final PlayerData targetData;

    public PlayerStatsUiController(PlayerMenu menu, AuditHistoryMenu auditHistoryMenu, Session session, PlayerData targetData) {
        this.menu = menu;
        this.auditHistoryMenu = auditHistoryMenu;
        this.session = session;
        this.targetData = targetData;
    }

    public record StatsModel(
            String targetUuid,
            String nickname,
            String customNickname,
            long pid,
            String description,
            String activeBadge,
            String systemBadge,
            boolean isAdmin,
            String accountCreated,
            String totalPlayTime,
            int pvpRating,
            String hexedRankName,
            int hexedPoints,
            String hexedTopRank,
            int gamesPlayed,
            int gamesWon,
            int winRatePercent,
            String pvpSummary,
            String survivalSummary,
            String hexedSummary,
            long blocksBuilt,
            long blocksDeconstructed,
            long blocksDestroyed,
            String activeTab,
            boolean isOwner,
            boolean viewerIsAdmin
    ) {
        public StatsModel withTab(String tab) {
            return new StatsModel(
                    targetUuid, nickname, customNickname, pid, description,
                    activeBadge, systemBadge, isAdmin, accountCreated, totalPlayTime,
                    pvpRating, hexedRankName, hexedPoints, hexedTopRank,
                    gamesPlayed, gamesWon, winRatePercent,
                    pvpSummary, survivalSummary, hexedSummary,
                    blocksBuilt, blocksDeconstructed, blocksDestroyed,
                    tab, isOwner, viewerIsAdmin
            );
        }
    }

    public sealed interface StatsEvent {
        record SelectTab(String tab) implements StatsEvent {}
        record OpenSettings() implements StatsEvent {}
        record OpenPlayers() implements StatsEvent {}
        record OpenAuditHistory() implements StatsEvent {}
        record OpenAuditActions() implements StatsEvent {}
        record Close() implements StatsEvent {}
    }

    public static StatsModel createModel(PlayerMenu menu, Session session, PlayerData targetData, PlayerStatsOverview statsOverview, Integer hexedTop) {
        Localization local = session.locale();
        boolean isOwner = session.data != null && session.data.uuid.equals(targetData.uuid);
        boolean viewerIsAdmin = session.player != null && session.player.admin;

        String customNickname = targetData.customNickname == null || targetData.customNickname.isBlank()
                ? targetData.nickname : targetData.customNickname;
        String description = targetData.description == null || targetData.description.isBlank()
                ? (local != null ? local.t("no-description") : "No description") : targetData.description;
        String activeBadge = PlayerSettingsFlows.activeBadgeName(local, targetData);
        String systemBadge = PlayerSettingsFlows.systemBadgeName(local, targetData);
        String accountCreated = menu != null ? menu.formatTime(targetData.createdModelTime, session) : "-";
        String playTime = menu != null ? menu.formatPlayTime(targetData.totalPlayTime, local) : "-";
        String rankName = (local != null && targetData.hexedRank() != null)
                ? local.t("hexed-ranks-" + targetData.hexedRank().name()) : "-";
        Locale locale = (local != null && local.getLocale() != null) ? local.getLocale() : Locale.ENGLISH;
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(locale);
        String hexedTopRank = hexedTop != null ? "#" + numberFormat.format(hexedTop) : "-";

        var overall = statsOverview != null ? statsOverview.overall() : null;
        int gamesPlayed = overall != null ? overall.gamesPlayed() : 0;
        int gamesWon = overall != null ? overall.gamesWon() : 0;
        int winRate = overall != null ? overall.winRatePercent() : 0;
        long blocksBuilt = overall != null ? overall.blocksBuilt() : 0;
        long blocksDecon = overall != null ? overall.blocksDeconstructed() : 0;
        long blocksDestroy = overall != null ? overall.blocksDestroyed() : 0;

        String pvpSummary = statsOverview != null ? PlayerProfileFlows.formatPvpSummary(local, statsOverview.pvp(), numberFormat) : "-";
        String survivalSummary = statsOverview != null ? PlayerProfileFlows.formatSurvivalSummary(local, statsOverview.survival(), numberFormat) : "-";
        String hexedSummary = statsOverview != null ? PlayerProfileFlows.formatHexedSummary(local, statsOverview.hexed(), numberFormat) : "-";

        return new StatsModel(
                targetData.uuid,
                targetData.nickname,
                customNickname,
                targetData.pid,
                description,
                activeBadge,
                systemBadge,
                targetData.admin,
                accountCreated,
                playTime,
                targetData.pvpRating,
                rankName,
                targetData.hexedPoints,
                hexedTopRank,
                gamesPlayed,
                gamesWon,
                winRate,
                pvpSummary,
                survivalSummary,
                hexedSummary,
                blocksBuilt,
                blocksDecon,
                blocksDestroy,
                "overview",
                isOwner,
                viewerIsAdmin
        );
    }

    @Override
    public StatsModel initialModel(Object context) {
        throw new UnsupportedOperationException("Call menu.openStatsUi(...) with pre-computed initial model");
    }

    @Override
    public UpdateResult<StatsModel> update(StatsModel model, StatsEvent event, ControllerContext ctx) {
        return switch (event) {
            case StatsEvent.SelectTab(var tab) ->
                    UpdateResult.patch(model.withTab(tab), SLOT_TAB_BODY);

            case StatsEvent.OpenSettings() -> {
                ctx.close();
                menu.settings(session.player.uuid(), targetData);
                yield UpdateResult.close(model);
            }

            case StatsEvent.OpenPlayers() -> {
                ctx.close();
                menu.players(session.player.uuid(), 1);
                yield UpdateResult.close(model);
            }

            case StatsEvent.OpenAuditHistory() -> {
                ctx.close();
                if (auditHistoryMenu != null) {
                    auditHistoryMenu.history(session.data.uuid, targetData);
                }
                yield UpdateResult.close(model);
            }

            case StatsEvent.OpenAuditActions() -> {
                ctx.close();
                if (auditHistoryMenu != null) {
                    auditHistoryMenu.actions(session.data.uuid, targetData);
                }
                yield UpdateResult.close(model);
            }

            case StatsEvent.Close() -> {
                ctx.close();
                yield UpdateResult.close(model);
            }
        };
    }

    @Override
    public VNode render(StatsModel model) {
        return Ui.table(t -> {
            // Main Window Frame: native Mindustry pane background with inner padding
            t.background("pane");
            t.margin(14f);
            t.layout(l -> l.width(560f).pad(6f));

            // 1. Header Profile Card (dark button background with clean margins)
            t.add(Ui.table(h -> {
                h.background("button");
                h.margin(10f);
                h.layout(l -> l.growX().padBottom(8f));

                // Title row
                h.add(Ui.table(row -> {
                    row.layout(l -> l.growX());
                    String badgeTag = !model.activeBadge().isBlank() ? "  [gold][" + model.activeBadge() + "][]" : "";
                    Text title = Text.raw("[accent]" + model.customNickname() + "[] [gray]#" + model.pid() + "[]" + badgeTag);
                    if (model.isAdmin()) {
                        title = Text.join(title, Text.join(Text.raw("  "), Text.t("player-stats-admin-tag")));
                    }
                    row.label(title, l -> l.align("left").growX());
                })).row();

                // Description row
                h.add(Ui.table(row -> {
                    row.layout(l -> l.growX());
                    row.label(Text.raw("[lightgray]\"" + model.description() + "\"[]"),
                            l -> l.align("left").growX());
                }));
            })).row();

            // 2. Navigation Tabs (togglet style with clear visual active highlight)
            t.add(Ui.table(tabs -> {
                tabs.layout(l -> l.growX().padBottom(8f));
                tabs.button(Text.t("player-stats-tab-overview"), "tab:overview", b -> b
                        .style("togglet")
                        .checked("overview".equals(model.activeTab()))
                        .layout(l -> l.uniform().growX()));
                tabs.button(Text.t("player-stats-tab-matches"), "tab:matches", b -> b
                        .style("togglet")
                        .checked("matches".equals(model.activeTab()))
                        .layout(l -> l.uniform().growX()));
                tabs.button(Text.t("player-stats-tab-blocks"), "tab:blocks", b -> b
                        .style("togglet")
                        .checked("blocks".equals(model.activeTab()))
                        .layout(l -> l.uniform().growX()));
            })).row();

            // 3. Dynamic Slot for Selected Tab Content (structured 2-column grid, patched in-place)
            t.slot("slot_tab_body", body -> {
                body.background("button");
                body.margin(12f);
                body.layout(l -> l.growX().minHeight(150f));

                if ("overview".equals(model.activeTab())) {
                    addStatRow(body, Text.t("player-stats-account-created"), Text.raw("[white]" + model.accountCreated() + "[]"));
                    addStatRow(body, Text.t("player-stats-play-time"), Text.raw("[white]" + model.totalPlayTime() + "[]"));
                    addStatRow(body, Text.t("player-stats-pvp-rating"), Text.raw("[sky]" + model.pvpRating() + "[]"));
                    Text hexedVal = model.hexedPoints() > 0
                            ? Text.join(Text.raw("[sky]" + model.hexedRankName() + "[] "),
                                    Text.t("player-stats-hexed-points", java.util.Map.of("points", model.hexedPoints())))
                            : Text.raw("[sky]" + model.hexedRankName() + "[]");
                    addStatRow(body, Text.t("player-stats-hexed-rank"), hexedVal);
                    addStatRow(body, Text.t("player-stats-hexed-leaderboard"), Text.raw("[accent]" + model.hexedTopRank() + "[]"));
                } else if ("matches".equals(model.activeTab())) {
                    addStatRow(body, Text.t("player-stats-total-games"),
                            Text.t("player-stats-games-played-value", java.util.Map.of("count", model.gamesPlayed())));
                    addStatRow(body, Text.t("player-stats-victories"),
                            Text.t("player-stats-victories-value", java.util.Map.of("wins", model.gamesWon(), "winRate", model.winRatePercent())));
                    addStatRow(body, Text.t("player-stats-pvp-summary"), Text.raw(model.pvpSummary()));
                    addStatRow(body, Text.t("player-stats-survival-summary"), Text.raw(model.survivalSummary()));
                    addStatRow(body, Text.t("player-stats-hexed-summary"), Text.raw(model.hexedSummary()));
                } else {
                    addStatRow(body, Text.t("player-stats-blocks-built"), Text.raw("[lime]" + model.blocksBuilt() + "[]"));
                    addStatRow(body, Text.t("player-stats-blocks-deconstructed"), Text.raw("[orange]" + model.blocksDeconstructed() + "[]"));
                    addStatRow(body, Text.t("player-stats-blocks-destroyed"), Text.raw("[scarlet]" + model.blocksDestroyed() + "[]"));
                }
            }).row();

            // 4. Action Footer Bar
            t.add(Ui.table(f -> {
                f.layout(l -> l.growX().padTop(8f));
                if (model.isOwner() || model.viewerIsAdmin()) {
                    f.button(Text.t("player-stats-btn-settings"), "action:settings", b -> b.layout(l -> l.uniform().growX().height(36f)));
                }
                if (model.viewerIsAdmin()) {
                    f.button(Text.t("player-stats-btn-audit"), "action:audit_history", b -> b.layout(l -> l.uniform().growX().height(36f)));
                }
                f.button(Text.t("player-stats-btn-players"), "action:players", b -> b.layout(l -> l.uniform().growX().height(36f)));
                f.button(Text.t("player-stats-btn-close"), "action:close", b -> b.layout(l -> l.uniform().growX().height(36f)));
            }));
        });
    }

    private static void addStatRow(Ui.TableBuilder body, Text label, Text value) {
        body.label(label, l -> l.align("left").padRight(16f));
        body.label(value, l -> l.align("left"));
        body.row();
    }

    @Override
    public StatsEvent parseEvent(MenuResult result) {
        if (result == null || result.result == null) return null;
        if (result.result.startsWith("tab:")) return new StatsEvent.SelectTab(result.result.substring(4));
        if ("action:settings".equals(result.result)) return new StatsEvent.OpenSettings();
        if ("action:players".equals(result.result)) return new StatsEvent.OpenPlayers();
        if ("action:audit_history".equals(result.result)) return new StatsEvent.OpenAuditHistory();
        if ("action:audit_actions".equals(result.result)) return new StatsEvent.OpenAuditActions();
        if ("action:close".equals(result.result)) return new StatsEvent.Close();
        return null;
    }
}
