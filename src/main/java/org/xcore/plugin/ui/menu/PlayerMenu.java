package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import arc.util.Strings;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import static com.ospx.flubundle.Bundle.args;
@Singleton
public class PlayerMenu extends Menu {

    /** Vanilla Mindustry name length limit in UTF-8 bytes (see Vars.maxNameLength). */
    private static final int MAX_PLAIN_NAME_BYTES = 40;
    private static final int BADGE_ICON_RANGE_START = 0xE800;
    private static final int BADGE_ICON_RANGE_END = 0xF8FF;

    private final PlayerDataRepository playerDataRepository;
    private final GameDataRepository gameDataRepository;
    private final Bundle bundle;
    private final NetworkService network;
    private final PlayerDisplayService playerDisplayService;
    private final AuditHistoryMenu auditHistoryMenu;

    @Inject
    public PlayerMenu(Config config,
                      GlobalConfig globalConfig,
                      SessionService sessionService,
                      PlayerDataRepository playerDataRepository,
                      GameDataRepository gameDataRepository,
                      Bundle bundle,
                      NetworkService network,
                      PlayerDisplayService playerDisplayService,
                      AuditHistoryMenu auditHistoryMenu) {
        super(config, globalConfig, sessionService);
        this.playerDataRepository = playerDataRepository;
        this.gameDataRepository = gameDataRepository;
        this.bundle = bundle;
        this.network = network;
        this.playerDisplayService = playerDisplayService;
        this.auditHistoryMenu = auditHistoryMenu;
    }

    public void player(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        Localization local = session.locale();
        boolean isOwner = session.data.uuid.equals(targetData.uuid);

        String customNickname = targetData.customNickname == null || Objects.equals(targetData.customNickname, "")
                ? targetData.nickname : targetData.customNickname;
        String description = targetData.description == null || Objects.equals(targetData.description, "")
                ? local.t("no-description") : targetData.description;
        String activeBadge = activeBadgeName(local, targetData);
        String systemBadge = systemBadgeName(local, targetData);
        String accountCreated = formatTime(targetData.createdModelTime, session);
        String playTime = formatPlayTime(targetData.totalPlayTime, local);
        String rankName = local.t("hexed-ranks-" + targetData.hexedRank().name());
        String hexedProgress = formatHexedProgress(local, targetData);
        PlayerStatsOverview statsOverview = gameDataRepository.aggregatePlayerStatsOverview(targetData.uuid);
        var overallStats = statsOverview.overall();
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(local.getLocale());

        session.builder()
                .title("player-menu-player-title")
                .content("player-menu-player-content", args(
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
                ))
                .ifAddLocal((isOwner || session.player.admin), "player-menu-settings", () -> {
                    session.pushHistory(() -> player(uuid, targetData));
                    settings(uuid, targetData);
                })
                .ifAddLocal(session.player.admin, "audit-menu-open", () -> {
                    session.pushHistory(() -> player(uuid, targetData));
                    auditHistoryMenu.history(uuid, targetData);
                })
                .ifAddLocal(session.player.admin, "audit-menu-actions-open", () -> {
                    session.pushHistory(() -> player(uuid, targetData));
                    auditHistoryMenu.actions(uuid, targetData);
                })
                .end()
                .addLocalRow("player-menu-players", () -> {
                    session.pushHistory(() -> player(uuid, targetData));
                    players(uuid, 1);
                })
                .addNavigationRow()
                .show();
    }

    private String formatPvpSummary(Localization local, ModeStatsSummary stats, NumberFormat numberFormat) {
        if (!stats.hasData()) {
            return local.t("player-menu-player-no-mode-stats");
        }
        return local.t("player-menu-player-pvp-summary", args(
                "gamesPlayed", numberFormat.format(stats.gamesPlayed()),
                "gamesWon", numberFormat.format(stats.gamesWon()),
                "winRate", numberFormat.format(stats.winRatePercent())
        ));
    }

    private String formatSurvivalSummary(Localization local, ModeStatsSummary stats, NumberFormat numberFormat) {
        if (!stats.hasData()) {
            return local.t("player-menu-player-no-mode-stats");
        }
        return local.t("player-menu-player-survival-summary", args(
                "gamesPlayed", numberFormat.format(stats.gamesPlayed()),
                "bestWave", numberFormat.format(stats.bestWave()),
                "averageWave", numberFormat.format(stats.averageWave())
        ));
    }

    private String formatHexedSummary(Localization local, ModeStatsSummary stats, NumberFormat numberFormat) {
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

    private String formatHexedProgress(Localization local, PlayerData targetData) {
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

    public void players(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        List<Session> onlinePlayers = getFilteredOnlinePlayers(session);
        int totalPlayers = onlinePlayers.size();
        int perPage = globalConfig.eventsPerPage;
        var pagination = CustomGatherers.calculatePagination(totalPlayers, perPage);
        int totalPages = Math.max(1, pagination.totalPages());

        int validPage = pagination.totalPages() <= 0 ? 1 : pagination.clampPage(page);
        int skip = (validPage - 1) * perPage;
        List<Session> players = onlinePlayers.stream()
                .skip(skip)
                .limit(perPage)
                .toList();

        String menuContent;
        if (totalPlayers == 0) {
            menuContent = session.locale().t("player-menu-players-empty");
        } else {
            menuContent = session.locale().t("player-menu-players-content", args(
                "page", validPage,
                "total", totalPages
            ));
        }

        session.builder()
                .title("player-menu-players-title")
                .rawContent(menuContent)

                .addStatusButton("admin", () -> players(uuid, 1))

                .start()
                    .ifAddLocal(validPage > 1, "previous", () -> players(uuid, validPage - 1))
                    .ifAddLocal(validPage < totalPages, "next", () -> players(uuid, validPage + 1))
                .end()

                .addForEach(players, (b, onlinePlayer) -> b.addRow(session.locale().t("player-menu-players-row", args(
                        "nickname", playerDisplayService.resolveBaseName(onlinePlayer.data, onlinePlayer.player),
                        "pid", onlinePlayer.data.pid
                )), () -> {
                    session.pushHistory(() -> players(uuid, validPage));
                    player(uuid, onlinePlayer.data);
                }))

                .addNavigationRow()
                .show();
    }

    private List<Session> getFilteredOnlinePlayers(Session viewerSession) {
        return streamCachedPlayers()
                .filter(onlineSession -> onlineSession != null && onlineSession.data != null && onlineSession.player != null)
                .filter(onlineSession -> matchesAdminFilter(viewerSession, onlineSession))
                .sorted(Comparator
                        .comparing((Session onlineSession) -> playerDisplayService.resolveBaseName(onlineSession.data, onlineSession.player), String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(onlineSession -> onlineSession.data.pid))
                .toList();
    }

    private java.util.stream.Stream<Session> streamCachedPlayers() {
        return sessionService.streamCached();
    }

    private boolean matchesAdminFilter(Session viewerSession, Session onlineSession) {
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

    public void settings(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (!session.data.uuid.equals(targetData.uuid) && !session.player.admin) {
            session.locale().send("error-no-access");
            return;
        }

        Localization local = session.locale();

        String customNickDisplay = (targetData.customNickname == null || targetData.customNickname.isEmpty())
                ? local.t("none") : targetData.customNickname;
        String descDisplay = (targetData.description == null || targetData.description.isEmpty())
                ? local.t("no-description") : targetData.description;
        String activeBadge = activeBadgeName(local, targetData);
        String systemBadge = systemBadgeName(local, targetData);
        String globalChat = targetData.globalChatVisible ? local.t("yes") : local.t("no");
        String discordRelay = targetData.discordRelayVisible ? local.t("yes") : local.t("no");

        session.builder().title("player-menu-settings-title")
                .content("player-menu-settings-content", args(
                        "nickname", targetData.nickname,
                        "customNickname", customNickDisplay,
                        "activeBadge", activeBadge,
                        "systemBadge", systemBadge,
                        "description", descDisplay,
                        "leaderboard", targetData.leaderboard ? local.t("yes") : local.t("no"),
                        "language", local.getLanguageName(targetData.language, "auto"),
                        "translatorLanguage", local.getLanguageName(targetData.translatorLanguage, "off"),
                        "globalChat", globalChat,
                        "discordRelay", discordRelay
                ))
                .start()
                    .addLocal("player-menu-settings-customNickname", () -> {
                        session.textHandler = t -> {
                            boolean isReset = (t == null || t.trim().isEmpty());
                            String newNick = isReset ? "" : t;

                            if (!isReset) {
                                String plain = Strings.stripColors(newNick);
                                if (plain.getBytes(StandardCharsets.UTF_8).length > MAX_PLAIN_NAME_BYTES) {
                                    local.send("error-nickname-too-long", args("max", MAX_PLAIN_NAME_BYTES));
                                    settings(uuid, targetData);
                                    return;
                                }

                                if (containsBadgeLikeGlyphs(plain)) {
                                    local.send("error-nickname-badge-glyph", args());
                                    settings(uuid, targetData);
                                    return;
                                }
                            }

                            updateCustomNickname(targetData, newNick, true, true);

                            settings(uuid, targetData);
                        };

                        Call.textInput(session.player.con, session.menuService.getTextId(),
                            local.t("event-menu-edit-name-title"),
                            local.t("player-menu-settings-customNickname-message"),
                            256, targetData.customNickname, false);
                    })
                    .addLocal("player-menu-settings-description", () -> {
                        session.textHandler = t -> {
                            updateDescription(targetData, t);
                            settings(uuid, targetData);
                        };
                        Call.textInput(session.player.con, session.menuService.getTextId(), local.t("event-menu-edit-description-title"), "", 1000, targetData.description, false);
                    })
                .end()
                .addLocalRow("player-menu-settings-chat", () -> {
                    session.pushHistory(() -> settings(uuid, targetData));
                    chatSettings(uuid, targetData);
                })
                .addLocalRow("player-menu-settings-badges", () -> {
                    session.pushHistory(() -> settings(uuid, targetData));
                    badges(uuid, targetData);
                })
                .addLocalRow(targetData.leaderboard ? "player-leaderboard-active" : "player-leaderboard-inactive", () -> {
                    updateLeaderboard(targetData, !targetData.leaderboard);
                    settings(uuid, targetData);
                })
                .start()
                    .add(local.t("settings-language-label", args("lang", local.getLanguageName(targetData.language, "auto"))), () -> {
                        session.pushHistory(() -> settings(uuid, targetData));
                        languageSelectionMenu(uuid, targetData, false);
                    })
                .end()
                .addNavigationRow()
                .show();
    }

    public void chatSettings(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (!session.data.uuid.equals(targetData.uuid) && !session.player.admin) {
            session.locale().send("error-no-access");
            return;
        }

        Localization local = session.locale();

        session.builder()
                .title("player-menu-settings-chat-title")
                .content("player-menu-settings-chat-content", args(
                        "globalChat", targetData.globalChatVisible ? local.t("yes") : local.t("no"),
                        "discordRelay", targetData.discordRelayVisible ? local.t("yes") : local.t("no"),
                        "translatorLanguage", local.getLanguageName(targetData.translatorLanguage, "off")
                ))
                .addLocalRow(targetData.globalChatVisible ? "player-menu-settings-global-chat-on" : "player-menu-settings-global-chat-off", () -> {
                    updateGlobalChatVisible(targetData, !targetData.globalChatVisible);
                    chatSettings(uuid, targetData);
                })
                .addLocalRow(targetData.discordRelayVisible ? "player-menu-settings-discord-relay-on" : "player-menu-settings-discord-relay-off", () -> {
                    updateDiscordRelayVisible(targetData, !targetData.discordRelayVisible);
                    chatSettings(uuid, targetData);
                })
                .addRow(local.t("settings-translator-label", args("lang", local.getLanguageName(targetData.translatorLanguage, "off"))), () -> {
                    session.pushHistory(() -> chatSettings(uuid, targetData));
                    languageSelectionMenu(uuid, targetData, true);
                })
                .addNavigationRow()
                .show();
    }

    public void languageSelectionMenu(String uuid, PlayerData targetData, boolean isTranslator) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        Seq<Locale> locales = bundle.getAvailableLocales();
        Localization local = session.locale();

        var builder = session.builder()
                .title(isTranslator ? "player-menu-settings-translator-title" : "player-menu-settings-language-title")
                .start()
                    .addLocal(isTranslator ? "default" : "auto", () -> {
                        if (isTranslator) {
                            updateTranslatorLanguage(targetData, "off");
                        } else {
                            updateLanguage(targetData, "auto");
                        }
                        session.popHistory().run();
                    })
                .end();

        builder.addForEach(locales, (b, loc) -> {
            String code = "uk".equals(loc.getLanguage()) ? "uk_UA" : loc.getLanguage();
            String langName = Strings.capitalize(loc.getDisplayLanguage(loc));

            b.addRow(langName, () -> {
                        if (isTranslator) {
                            updateTranslatorLanguage(targetData, code);
                        } else {
                            updateLanguage(targetData, code);
                        }
                        session.popHistory().run();
                    });
        });

        builder.addNavigationRow().show();
    }

    public void badges(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (!session.data.uuid.equals(targetData.uuid) && !session.player.admin) {
            session.locale().send("error-no-access");
            return;
        }

        Localization local = session.locale();
        List<Badge> badges = unlockedSelectableBadges(targetData);
        String header = local.t("badge-menu-content", args(
                "systemBadge", systemBadgeName(local, targetData),
                "activeBadge", activeBadgeName(local, targetData),
                "symbolColorMode", badgeSymbolColorModeLabel(local, targetData)
        ));

        var builder = session.builder()
                .title("badge-menu-title")
                .rawContent(badges.isEmpty() ? header + "\n" + local.t("badge-menu-empty") : header);

        if (!badges.isEmpty()) {
            builder.addForEach(badges, (b, badge) -> b.addRow(local.t("badge-menu-row", args(
                    "badge", badgeLabel(local, badge),
                    "description", local.t(badge.descriptionKey())
            )), () -> {
                updateActiveBadge(targetData, badge.id(), true, true);
                badges(uuid, targetData);
            }));
        }

        builder.start()
                .addLocal("badge-menu-symbol-color-button", () -> {
                    session.pushHistory(() -> badges(uuid, targetData));
                    badgeSymbolColorMode(uuid, targetData);
                }, args("mode", badgeSymbolColorModeLabel(local, targetData)))
                .end()
                .start()
                .addLocal("badge-menu-view-all", () -> {
                    session.pushHistory(() -> badges(uuid, targetData));
                    allBadges(uuid, targetData);
                })
                .addLocal("badge-clear-button", () -> {
                    updateActiveBadge(targetData, "", true, true);
                    badges(uuid, targetData);
                })
                .end()
                .addNavigationRow()
                .show();
    }

    public void badgeSymbolColorMode(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (!session.data.uuid.equals(targetData.uuid) && !session.player.admin) {
            session.locale().send("error-no-access");
            return;
        }

        Localization local = session.locale();

        session.builder()
                .title("badge-menu-symbol-color-title")
                .content("badge-menu-symbol-color-content", args(
                        "mode", badgeSymbolColorModeLabel(local, targetData)
                ))
                .addLocalRow("badge-menu-symbol-color-default", () -> {
                    updateBadgeSymbolColorMode(targetData, "default", true, true);
                    badgeSymbolColorMode(uuid, targetData);
                })
                .addLocalRow("badge-menu-symbol-color-player-color", () -> {
                    updateBadgeSymbolColorMode(targetData, "player-color", true, true);
                    badgeSymbolColorMode(uuid, targetData);
                })
                .addNavigationRow()
                .show();
    }

    public void allBadges(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (!session.data.uuid.equals(targetData.uuid) && !session.player.admin) {
            session.locale().send("error-no-access");
            return;
        }

        Localization local = session.locale();
        var builder = session.builder()
                .title("badge-menu-all-title")
                .content("badge-menu-all-content");

        builder.addForEach(List.of(Badge.values()), (b, badge) -> b.addRow(local.t("badge-menu-all-row", args(
                "badge", badgeLabel(local, badge),
                "state", badgeState(local, targetData, badge),
                "description", local.t(badge.descriptionKey())
        )), () -> {
            if (badge.selectable() && !badge.system() && ownsBadge(targetData, badge)) {
                updateActiveBadge(targetData, badge.id(), true, true);
            }
            allBadges(uuid, targetData);
        }));

        builder.addNavigationRow().show();
    }

    private void updateCustomNickname(PlayerData targetData, String customNickname, boolean refreshDisplay, boolean sync) {
        updatePlayerData(targetData,
                data -> data.customNickname = customNickname,
                data -> playerDataRepository.updateCustomNickname(data.uuid, customNickname),
                data -> new org.xcore.plugin.event.SocketEvents.PlayerCustomNicknameChanged(data.uuid, data.customNickname),
                refreshDisplay,
                sync);
    }

    private void updateDescription(PlayerData targetData, String description) {
        updatePlayerData(targetData,
                data -> data.description = description,
                data -> playerDataRepository.updateDescription(data.uuid, description),
                null,
                false,
                false);
    }

    private void updateLeaderboard(PlayerData targetData, boolean leaderboard) {
        updatePlayerData(targetData,
                data -> data.leaderboard = leaderboard,
                data -> playerDataRepository.updateLeaderboard(data.uuid, leaderboard),
                null,
                false,
                false);
    }

    private void updateLanguage(PlayerData targetData, String language) {
        Session targetSession = sessionService.get(targetData.uuid);
        if (targetSession != null) {
            targetSession.updateLanguage(language);
            return;
        }

        updatePlayerData(targetData,
                data -> data.language = language,
                data -> playerDataRepository.updateLanguage(data.uuid, language),
                null,
                false,
                false);
    }

    private void updateTranslatorLanguage(PlayerData targetData, String language) {
        Session targetSession = sessionService.get(targetData.uuid);
        if (targetSession != null) {
            targetSession.updateTranslatorLanguage(language);
            return;
        }

        updatePlayerData(targetData,
                data -> data.translatorLanguage = language,
                data -> playerDataRepository.updateTranslatorLanguage(data.uuid, language),
                null,
                false,
                false);
    }

    private void updateGlobalChatVisible(PlayerData targetData, boolean visible) {
        Session targetSession = sessionService.get(targetData.uuid);
        if (targetSession != null) {
            targetSession.updateGlobalChatVisible(visible);
            return;
        }

        updatePlayerData(targetData,
                data -> data.globalChatVisible = visible,
                data -> playerDataRepository.updateGlobalChatVisible(data.uuid, visible),
                null,
                false,
                false);
    }

    private void updateDiscordRelayVisible(PlayerData targetData, boolean visible) {
        Session targetSession = sessionService.get(targetData.uuid);
        if (targetSession != null) {
            targetSession.updateDiscordRelayVisible(visible);
            return;
        }

        updatePlayerData(targetData,
                data -> data.discordRelayVisible = visible,
                data -> playerDataRepository.updateDiscordRelayVisible(data.uuid, visible),
                null,
                false,
                false);
    }

    private void updateActiveBadge(PlayerData targetData, String badgeId, boolean refreshDisplay, boolean sync) {
        updatePlayerData(targetData,
                data -> data.activeBadge = badgeId,
                data -> playerDataRepository.setActiveBadge(data.uuid, badgeId),
                data -> new org.xcore.plugin.event.SocketEvents.PlayerActiveBadgeChanged(data.uuid, data.activeBadge),
                refreshDisplay,
                sync);
    }

    private void updateBadgeSymbolColorMode(PlayerData targetData, String mode, boolean refreshDisplay, boolean sync) {
        updatePlayerData(targetData,
                data -> data.badgeSymbolColorMode = mode,
                data -> playerDataRepository.updateBadgeSymbolColorMode(data.uuid, mode),
                data -> new org.xcore.plugin.event.SocketEvents.PlayerBadgeSymbolColorModeChanged(data.uuid, data.badgeSymbolColorMode),
                refreshDisplay,
                sync);
    }

    private void updatePlayerData(PlayerData targetData,
                                  Consumer<PlayerData> updater,
                                  Consumer<PlayerData> partialUpdater,
                                  java.util.function.Function<PlayerData, Object> syncEventFactory,
                                  boolean refreshDisplay,
                                  boolean sync) {
        Session targetSession = sessionService.get(targetData.uuid);
        if (targetSession != null) {
            updater.accept(targetSession.data);
            partialUpdater.accept(targetSession.data);
            if (refreshDisplay) {
                playerDisplayService.refresh(targetSession);
            }
            if (sync && syncEventFactory != null) {
                network.post(syncEventFactory.apply(targetSession.data));
            }
        } else {
            updater.accept(targetData);
            partialUpdater.accept(targetData);
            if (sync && syncEventFactory != null) {
                network.post(syncEventFactory.apply(targetData));
            }
        }
    }

    private String activeBadgeName(Localization local, PlayerData targetData) {
        Badge badge = Badge.byId(targetData.activeBadge);
        if (badge == null || targetData.unlockedBadges == null || !targetData.unlockedBadges.contains(badge.id())) {
            return local.t("none");
        }
        return badgeLabel(local, badge);
    }

    private String systemBadgeName(Localization local, PlayerData targetData) {
        return targetData.admin ? badgeLabel(local, Badge.ADMIN) : local.t("none");
    }

    private String badgeSymbolColorModeLabel(Localization local, PlayerData targetData) {
        return usesPlayerBadgeSymbolColor(targetData)
                ? local.t("badge-menu-symbol-color-player-color")
                : local.t("badge-menu-symbol-color-default");
    }

    private List<Badge> unlockedSelectableBadges(PlayerData targetData) {
        List<Badge> result = new ArrayList<>();
        for (Badge badge : Badge.selectableManualBadges()) {
            if (targetData.unlockedBadges != null && targetData.unlockedBadges.contains(badge.id())) {
                result.add(badge);
            }
        }
        return result;
    }

    private String badgeLabel(Localization local, Badge badge) {
        return badge.tag() + " " + local.t(badge.nameKey());
    }

    private String badgeState(Localization local, PlayerData targetData, Badge badge) {
        if (badge.system()) {
            return targetData.admin ? local.t("badge-state-system-active") : local.t("badge-state-system");
        }

        if (badge.id().equals(targetData.activeBadge) && ownsBadge(targetData, badge)) {
            return local.t("badge-state-active");
        }

        return ownsBadge(targetData, badge) ? local.t("badge-state-unlocked") : local.t("badge-state-locked");
    }

    private boolean ownsBadge(PlayerData targetData, Badge badge) {
        return targetData.unlockedBadges != null && targetData.unlockedBadges.contains(badge.id());
    }

    private boolean usesPlayerBadgeSymbolColor(PlayerData targetData) {
        return targetData != null
                && targetData.badgeSymbolColorMode != null
                && targetData.badgeSymbolColorMode.equalsIgnoreCase("player-color");
    }

    private boolean containsBadgeLikeGlyphs(String input) {
        return input.codePoints().anyMatch(cp -> cp >= BADGE_ICON_RANGE_START && cp <= BADGE_ICON_RANGE_END);
    }
}
