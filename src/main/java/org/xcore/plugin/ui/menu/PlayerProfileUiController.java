package org.xcore.plugin.ui.menu;

import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.model.enums.TopCategory;
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
 * Reactive Elm/MVI controller for viewing player profile and statistics in Mindustry v160.
 *
 * <p>Replaces legacy multi-row text dialogs with a tabbed interface (Overview, Stats, Modes)
 * featuring zero-flicker partial updates via {@code slot_content} and mobile-responsive layout.
 */
public class PlayerProfileUiController implements UiController<PlayerProfileUiController.ProfileModel, PlayerProfileUiController.ProfileEvent> {

    public static final SlotKey<Object> SLOT_CONTENT = SlotKey.of("slot_content");

    public enum ProfileTab {
        OVERVIEW,
        STATS,
        MODES
    }

    public record ProfileModel(
            String targetUuid,
            int targetPid,
            String nickname,
            String customNickname,
            String description,
            String activeBadge,
            String systemBadge,
            String accountCreated,
            String playTime,
            String hexedRankTag,
            String hexedRankName,
            String hexedProgress,
            String hexedTopRank,
            int hexedPoints,
            int pvpRating,
            boolean admin,
            String discordUsername,
            PlayerStatsOverview stats,
            ProfileTab activeTab,
            boolean isOwner,
            boolean viewerIsAdmin
    ) {
        public ProfileModel withActiveTab(ProfileTab newTab) {
            return new ProfileModel(
                    targetUuid, targetPid, nickname, customNickname, description,
                    activeBadge, systemBadge, accountCreated, playTime,
                    hexedRankTag, hexedRankName, hexedProgress, hexedTopRank,
                    hexedPoints, pvpRating, admin, discordUsername, stats,
                    newTab, isOwner, viewerIsAdmin
            );
        }
    }

    public sealed interface ProfileEvent {
        record SelectTab(ProfileTab tab) implements ProfileEvent {}
        record OpenSettings() implements ProfileEvent {}
        record OpenAudit() implements ProfileEvent {}
        record OpenPlayers() implements ProfileEvent {}
        record Close() implements ProfileEvent {}
    }

    private final PlayerMenu menu;
    private final AuditHistoryMenu auditHistoryMenu;
    private final Session session;
    private final PlayerData targetData;

    public PlayerProfileUiController(PlayerMenu menu,
                                     AuditHistoryMenu auditHistoryMenu,
                                     Session session,
                                     PlayerData targetData) {
        this.menu = menu;
        this.auditHistoryMenu = auditHistoryMenu;
        this.session = session;
        this.targetData = targetData;
    }

    public static ProfileModel createModel(Session session,
                                           PlayerData targetData,
                                           PlayerStatsOverview statsOverview,
                                           Integer hexedTopRank,
                                           PlayerMenu menu,
                                           PlayerDataRepository playerDataRepo) {
        Localization local = session != null ? session.locale() : null;
        boolean isOwner = session != null && session.data != null && session.data.uuid.equals(targetData.uuid);
        boolean viewerIsAdmin = session != null && session.player != null && session.player.admin;

        String customNick = targetData.customNickname != null ? targetData.customNickname : "";
        String desc = targetData.description != null && !targetData.description.isBlank()
                ? targetData.description
                : (local != null ? local.t("no-description") : "No description");

        String activeBadge = local != null ? PlayerSettingsFlows.activeBadgeName(local, targetData) : targetData.activeBadge;
        String systemBadge = local != null ? PlayerSettingsFlows.systemBadgeName(local, targetData) : (targetData.admin ? "Admin" : "None");

        String accountCreated = menu != null ? menu.formatTime(targetData.createdModelTime, session) : "-";
        String playTime = menu != null && local != null ? menu.formatPlayTime(targetData.totalPlayTime, local) : targetData.totalPlayTime + "m";

        var rank = targetData.hexedRank();
        String rankTag = rank.tag != null ? rank.tag : "";
        String rankName = local != null ? local.t("hexed-ranks-" + rank.name()) : rank.name();
        String progress = local != null ? PlayerProfileFlows.formatHexedProgress(local, targetData) : "-";

        NumberFormat numberFormat = local != null ? NumberFormat.getIntegerInstance(local.getLocale()) : NumberFormat.getIntegerInstance();

        Integer effectiveHexedTop = hexedTopRank;
        if (effectiveHexedTop == null && playerDataRepo != null) {
            effectiveHexedTop = playerDataRepo.findTopRank(TopCategory.HEXED, targetData);
        }
        String topRankStr = effectiveHexedTop != null ? "#" + numberFormat.format(effectiveHexedTop) : "-";

        PlayerStatsOverview effectiveStats = statsOverview != null ? statsOverview : PlayerStatsOverview.EMPTY;

        return new ProfileModel(
                targetData.uuid,
                targetData.pid,
                targetData.nickname != null ? targetData.nickname : "Unknown",
                customNick,
                desc,
                activeBadge,
                systemBadge,
                accountCreated,
                playTime,
                rankTag,
                rankName,
                progress,
                topRankStr,
                targetData.hexedPoints,
                targetData.pvpRating,
                targetData.admin,
                targetData.discordUsername != null ? targetData.discordUsername : "",
                effectiveStats,
                ProfileTab.OVERVIEW,
                isOwner,
                viewerIsAdmin
        );
    }

    @Override
    public VNode render(ProfileModel model) {
        return Ui.table(t -> {
            t.background("pane");
            t.margin(14f);
            t.layout(l -> l.width(580f).pad(6f));

            // 1. Header with player nickname, hexed rank tag, and close button
            t.add(Ui.table(h -> {
                h.layout(l -> l.growX().padBottom(4f));
                String displayName = !model.customNickname().isBlank() ? model.customNickname() : model.nickname();
                String titleText = "[accent]" + displayName + "[]" + (!model.hexedRankTag().isBlank() ? "  " + model.hexedRankTag() : "");
                h.label(Text.raw(titleText), l -> l.align("left").growX());
                h.button(Text.raw(" [scarlet]✕[] "), "action:close", b -> b
                        .style("cleart")
                        .layout(l -> l.size(34f)));
            })).row();
            t.image("whiteui", l -> l.growX().height(3f).padBottom(8f).color("ffd37f")).row();

            // 2. Navigation Tabs (Overview, Stats, Modes)
            t.add(Ui.table(tabs -> {
                tabs.layout(l -> l.growX().padBottom(6f));
                tabs.button(Text.t("player-menu-tab-overview"), "action:tab:overview", b -> b
                        .style("togglet")
                        .checked(model.activeTab() == ProfileTab.OVERVIEW)
                        .layout(l -> l.uniform().growX().height(36f).pad(2f)));
                tabs.button(Text.t("player-menu-tab-stats"), "action:tab:stats", b -> b
                        .style("togglet")
                        .checked(model.activeTab() == ProfileTab.STATS)
                        .layout(l -> l.uniform().growX().height(36f).pad(2f)));
                tabs.button(Text.t("player-menu-tab-modes"), "action:tab:modes", b -> b
                        .style("togglet")
                        .checked(model.activeTab() == ProfileTab.MODES)
                        .layout(l -> l.uniform().growX().height(36f).pad(2f)));
            })).row();

            // 3. Dynamic Content Slot (Pane with maxHeight 360f for mobile responsiveness)
            t.slot("slot_content", slot -> {
                slot.layout(l -> l.growX());
                slot.pane(p -> {
                    p.layout(l -> l.growX().maxHeight(360f));
                    p.table(body -> {
                        body.layout(l -> l.growX().fillX());
                        switch (model.activeTab()) {
                            case OVERVIEW -> renderOverviewTab(body, model);
                            case STATS -> renderStatsTab(body, model);
                            case MODES -> renderModesTab(body, model);
                        }
                    });
                });
            }).row();

            // 4. Action Buttons Footer
            t.add(Ui.table(actions -> {
                actions.layout(l -> l.growX().padTop(6f));
                if (model.isOwner() || model.viewerIsAdmin()) {
                    actions.button(Text.t("player-menu-settings"), "action:settings", b -> b
                            .style("cleart")
                            .layout(l -> l.growX().uniform().height(42f)));
                }
                if (model.viewerIsAdmin()) {
                    actions.button(Text.t("audit-menu-open"), "action:audit", b -> b
                            .style("cleart")
                            .layout(l -> l.growX().uniform().height(42f)));
                }
                actions.button(Text.t("player-menu-players"), "action:players", b -> b
                        .style("cleart")
                        .layout(l -> l.growX().uniform().height(42f)));
                actions.button(Text.t("close"), "action:close", b -> b
                        .style("cleart")
                        .layout(l -> l.growX().uniform().height(42f)));
            })).row();
        });
    }

    private void renderOverviewTab(Ui.TableBuilder body, ProfileModel model) {
        // Section 1: Account Info
        body.add(Ui.table(info -> {
            info.layout(l -> l.growX().padBottom(4f));

            info.add(Ui.table(row1 -> {
                row1.layout(l -> l.growX().padBottom(2f));
                row1.label(Text.raw("[gray]PID:[] [white]#" + model.targetPid() + "[]"), l -> l.align("left").growX());
                row1.label(Text.raw("[gray]Playtime:[] [accent]" + model.playTime() + "[]"), l -> l.align("right"));
            })).row();

            info.add(Ui.table(row2 -> {
                row2.layout(l -> l.growX().padBottom(2f));
                row2.label(Text.raw("[gray]Created:[] [lightgray]" + model.accountCreated() + "[]"), l -> l.align("left").growX());
                if (!model.discordUsername().isBlank()) {
                    row2.label(Text.raw("[sky]Discord:[] @" + model.discordUsername()), l -> l.align("right"));
                } else {
                    row2.label(Text.raw("[gray]Discord: Not linked[]"), l -> l.align("right"));
                }
            })).row();

            info.add(Ui.table(row3 -> {
                row3.layout(l -> l.growX().padBottom(2f));
                row3.label(Text.raw("[gray]Badge:[] " + model.activeBadge()), l -> l.align("left").growX());
                row3.label(Text.raw("[gray]Role:[] " + model.systemBadge()), l -> l.align("right"));
            })).row();
        })).row();

        body.image("whiteui", l -> l.growX().height(2f).padTop(6f).padBottom(6f).color("454545")).row();

        // Section 2: Hexed Rank Card
        body.add(Ui.table(hexed -> {
            hexed.layout(l -> l.growX().padBottom(4f));
            hexed.label(Text.raw("[accent]Hexed Status[]"), l -> l.align("left").padBottom(4f)).row();

            hexed.add(Ui.table(hRow -> {
                hRow.layout(l -> l.growX().padBottom(2f));
                String rankDisplay = (!model.hexedRankTag().isBlank() ? model.hexedRankTag() + " " : "") + model.hexedRankName();
                hRow.label(Text.raw("[gray]Rank:[] " + rankDisplay), l -> l.align("left").growX());
                hRow.label(Text.raw("[gray]Top:[] [gold]" + model.hexedTopRank() + "[]"), l -> l.align("right"));
            })).row();

            hexed.add(Ui.table(pRow -> {
                pRow.layout(l -> l.growX());
                pRow.label(Text.raw("[gray]Points:[] [white]" + model.hexedPoints() + "[]"), l -> l.align("left").growX());
                pRow.label(Text.raw("[lightgray]" + model.hexedProgress() + "[]"), l -> l.align("right"));
            })).row();
        })).row();

        body.image("whiteui", l -> l.growX().height(2f).padTop(6f).padBottom(6f).color("454545")).row();

        // Section 3: Description / About
        body.add(Ui.table(desc -> {
            desc.layout(l -> l.growX());
            desc.label(Text.t("player-menu-settings-description"), l -> l.align("left").padBottom(2f)).row();
            desc.label(Text.raw("[lightgray]" + model.description() + "[]"), l -> l.align("left").growX());
        })).row();
    }

    private void renderStatsTab(Ui.TableBuilder body, ProfileModel model) {
        var overall = model.stats().overall();
        body.add(Ui.table(stats -> {
            stats.layout(l -> l.growX().padBottom(4f));

            stats.label(Text.raw("[accent]Overall Statistics[]"), l -> l.align("left").padBottom(6f)).row();

            stats.add(Ui.table(grid -> {
                grid.layout(l -> l.growX());

                // Row 1: Rating & Winrate
                grid.label(Text.raw("[gray]PvP Rating:[] [gold]" + model.pvpRating() + "[]"), l -> l.align("left").uniform().growX());
                grid.label(Text.raw("[gray]Win Rate:[] [accent]" + overall.winRatePercent() + "%[]"), l -> l.align("left").uniform().growX()).row();

                // Row 2: Games
                grid.label(Text.raw("[gray]Games Played:[] [white]" + overall.gamesPlayed() + "[]"), l -> l.align("left").uniform().growX());
                grid.label(Text.raw("[gray]Games Won:[] [white]" + overall.gamesWon() + "[]"), l -> l.align("left").uniform().growX()).row();

                // Row 3: Blocks
                grid.label(Text.raw("[gray]Blocks Built:[] [white]" + overall.blocksBuilt() + "[]"), l -> l.align("left").uniform().growX());
                grid.label(Text.raw("[gray]Blocks Deconstructed:[] [white]" + overall.blocksDeconstructed() + "[]"), l -> l.align("left").uniform().growX()).row();

                // Row 4: Combat
                grid.label(Text.raw("[gray]Blocks Destroyed:[] [scarlet]" + overall.blocksDestroyed() + "[]"), l -> l.align("left").uniform().growX());
                grid.label(Text.raw("[gray]Units Destroyed:[] [scarlet]" + overall.unitsDestroyed() + "[]"), l -> l.align("left").uniform().growX()).row();

                // Row 5: Production
                grid.label(Text.raw("[gray]Units Produced:[] [white]" + overall.unitsProduced() + "[]"), l -> l.align("left").uniform().growX());
                grid.label(Text.raw(""), l -> l.align("left").uniform().growX()).row();
            })).row();
        })).row();
    }

    private void renderModesTab(Ui.TableBuilder body, ProfileModel model) {
        var pvp = model.stats().pvp();
        var surv = model.stats().survival();
        var hexed = model.stats().hexed();

        body.add(Ui.table(modes -> {
            modes.layout(l -> l.growX());

            // 1. PvP Card
            modes.label(Text.raw("[accent]⚔ PvP[]"), l -> l.align("left").padBottom(2f)).row();
            modes.add(Ui.table(card -> {
                card.layout(l -> l.growX().padBottom(4f));
                card.label(Text.raw("[gray]Played:[] [white]" + pvp.gamesPlayed() + "[]"), l -> l.align("left").uniform().growX());
                card.label(Text.raw("[gray]Won:[] [white]" + pvp.gamesWon() + "[]"), l -> l.align("left").uniform().growX());
                card.label(Text.raw("[gray]Win Rate:[] [accent]" + pvp.winRatePercent() + "%[]"), l -> l.align("left").uniform().growX());
            })).row();

            modes.image("whiteui", l -> l.growX().height(2f).padTop(4f).padBottom(6f).color("454545")).row();

            // 2. Survival Card
            modes.label(Text.raw("[accent]🛡 Survival[]"), l -> l.align("left").padBottom(2f)).row();
            modes.add(Ui.table(card -> {
                card.layout(l -> l.growX().padBottom(4f));
                card.label(Text.raw("[gray]Played:[] [white]" + surv.gamesPlayed() + "[]"), l -> l.align("left").uniform().growX());
                card.label(Text.raw("[gray]Best Wave:[] [white]" + surv.bestWave() + "[]"), l -> l.align("left").uniform().growX());
                card.label(Text.raw("[gray]Avg Wave:[] [white]" + surv.averageWave() + "[]"), l -> l.align("left").uniform().growX());
            })).row();

            modes.image("whiteui", l -> l.growX().height(2f).padTop(4f).padBottom(6f).color("454545")).row();

            // 3. Hexed Card
            modes.label(Text.raw("[accent]👑 Hexed[]"), l -> l.align("left").padBottom(2f)).row();
            modes.add(Ui.table(card -> {
                card.layout(l -> l.growX().padBottom(4f));
                card.label(Text.raw("[gray]Played:[] [white]" + hexed.gamesPlayed() + "[]"), l -> l.align("left").uniform().growX());
                card.label(Text.raw("[gray]Won:[] [white]" + hexed.gamesWon() + "[]"), l -> l.align("left").uniform().growX());
                card.label(Text.raw("[gray]Best Place:[] [gold]#" + hexed.bestPlacement() + "[]"), l -> l.align("left").uniform().growX());
                card.label(Text.raw("[gray]Top 3:[] [accent]" + hexed.top3Finishes() + "[]"), l -> l.align("left").uniform().growX());
            })).row();
        })).row();
    }

    @Override
    public ProfileModel initialModel(Object context) {
        throw new UnsupportedOperationException("Open via menu.openPlayerProfileUi(...) with pre-computed initial model");
    }

    @Override
    public UpdateResult<ProfileModel> update(ProfileModel model, ProfileEvent event, ControllerContext ctx) {
        if (event instanceof ProfileEvent.SelectTab selectTab) {
            return UpdateResult.patch(model.withActiveTab(selectTab.tab()), SLOT_CONTENT);
        }

        if (event instanceof ProfileEvent.OpenSettings) {
            if (menu != null && session != null) {
                menu.openSettingsUi(session, targetData);
            }
            return UpdateResult.of(model);
        }

        if (event instanceof ProfileEvent.OpenAudit) {
            if (auditHistoryMenu != null && session != null) {
                auditHistoryMenu.history(session.data.uuid, targetData);
            }
            return UpdateResult.of(model);
        }

        if (event instanceof ProfileEvent.OpenPlayers) {
            if (menu != null && session != null) {
                menu.players(session.data.uuid, 1);
            }
            return UpdateResult.of(model);
        }

        if (event instanceof ProfileEvent.Close) {
            return UpdateResult.close(model);
        }

        return UpdateResult.of(model);
    }

    @Override
    public ProfileEvent parseEvent(MenuResult result) {
        if (result == null) return null;
        if (result.wasCancelled()) {
            return new ProfileEvent.Close();
        }

        String action = result.result;
        if (action == null || action.isBlank() || "action:close".equals(action)) {
            return new ProfileEvent.Close();
        }

        if (action.startsWith("action:tab:")) {
            String tabStr = action.substring("action:tab:".length()).toUpperCase(Locale.ROOT);
            try {
                return new ProfileEvent.SelectTab(ProfileTab.valueOf(tabStr));
            } catch (IllegalArgumentException e) {
                return new ProfileEvent.SelectTab(ProfileTab.OVERVIEW);
            }
        }

        return switch (action) {
            case "action:settings" -> new ProfileEvent.OpenSettings();
            case "action:audit" -> new ProfileEvent.OpenAudit();
            case "action:players" -> new ProfileEvent.OpenPlayers();
            default -> new ProfileEvent.Close();
        };
    }
}
