package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private final BundleService bundle;
    private final NetworkService network;
    private final PlayerDisplayService playerDisplayService;

    @Inject
    public PlayerMenu(Config config,
                      GlobalConfig globalConfig,
                      SessionService sessionService,
                      PlayerDataRepository playerDataRepository,
                      BundleService bundle,
                      NetworkService network,
                      PlayerDisplayService playerDisplayService) {
        super(config, globalConfig, sessionService);
        this.playerDataRepository = playerDataRepository;
        this.bundle = bundle;
        this.network = network;
        this.playerDisplayService = playerDisplayService;
    }

    public void player(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid).clear();
        if (session == null || session.data == null) return;

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

        session.builder()
                .title("player-menu-player-title")
                .content("player-menu-player-content", args(
                        "nickname", targetData.nickname,
                        "customNickname", customNickname,
                        "activeBadge", activeBadge,
                        "systemBadge", systemBadge,
                        "description", description,
                        "pid", targetData.pid,
                        "totalPlayTime", targetData.totalPlayTime,
                        "hexedRankTag", targetData.hexedRank().tag,
                        "hexedRankName", targetData.hexedRank().name(),
                        "pvpRating", targetData.pvpRating,
                        "admin", targetData.admin ? local.t("yes") : local.t("no"),
                        "leaderboard", targetData.leaderboard ? local.t("yes") : local.t("no"),
                        "language", targetData.language,
                        "translatorLanguage", targetData.translatorLanguage
                ))
                .ifAddLocal((isOwner || session.player.admin), "player-menu-settings", () -> {
                    session.pushHistory(() -> player(uuid, targetData));
                    settings(uuid, targetData);
                })
                .end()
                .addLocalRow("player-menu-players", () -> {
                    session.pushHistory(() -> player(uuid, targetData));
                    players(uuid, 1);
                })
                .addNavigationRow()
                .show();
    }

    public void players(String uuid, int page) {
        Session session = sessionService.get(uuid).clear();
        if (session == null || session.data == null) return;
        int totalPlayers = (int) playerDataRepository.count(session.sortStatus);
        int perPage = globalConfig.eventsPerPage;
        var pagination = CustomGatherers.calculatePagination(totalPlayers, perPage);

        int validPage = pagination.clampPage(page);
        int skip = (validPage - 1) * perPage;
        List<PlayerData> players = playerDataRepository.findPage(skip, perPage, session.sortStatus);

        String menuContent;
        if (totalPlayers == 0) {
            menuContent = session.locale().t("player-menu-players-empty");
        } else {
            menuContent = session.locale().t("player-menu-players-content", args(
                "page", validPage,
                "total", pagination.totalPages()
            ));
        }

        session.builder()
                .title("player-menu-players-title")
                .rawContent(menuContent)

                .addStatusButton("admin", () -> players(uuid, 1))

                .start()
                    .ifAddLocal(validPage > 1, "previous", () -> players(uuid, validPage - 1))
                    .ifAddLocal(validPage < pagination.totalPages(), "next", () -> players(uuid, validPage + 1))
                .end()

                .addForEach(players, (b, pData) -> b.addRow(pData.nickname, () -> {
                    session.pushHistory(() -> players(uuid, validPage));
                    player(uuid, pData);
                }))

                .addNavigationRow()
                .show();
    }

    public void settings(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid).clear();
        if (session == null || session.data == null) return;
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

        session.builder().title("player-menu-settings-title")
                .content("player-menu-settings-content", args(
                        "nickname", targetData.nickname,
                        "customNickname", customNickDisplay,
                        "activeBadge", activeBadge,
                        "systemBadge", systemBadge,
                        "description", descDisplay,
                        "leaderboard", targetData.leaderboard ? local.t("yes") : local.t("no"),
                        "language", local.getLanguageName(targetData.language, "auto"),
                        "translatorLanguage", local.getLanguageName(targetData.translatorLanguage, "off")
                ))
                .start()
                    .addLocal("player-menu-settings-customNickname", () -> {
                        session.setTextHandler(t -> {
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
                        });

                        Call.textInput(session.player.con, session.menuService.getTextId(),
                            local.t("event-menu-edit-name-title"),
                            local.t("player-menu-settings-customNickname-message"),
                            256, targetData.customNickname, false);
                    })
                    .addLocal("player-menu-settings-description", () -> {
                        session.setTextHandler(t -> {
                            updateDescription(targetData, t);
                            settings(uuid, targetData);
                        });
                        Call.textInput(session.player.con, session.menuService.getTextId(), local.t("event-menu-edit-description-title"), "", 1000, targetData.description, false);
                    })
                    .addLocal("player-menu-settings-badges", () -> {
                        session.pushHistory(() -> settings(uuid, targetData));
                        badges(uuid, targetData);
                    })
                .end()
                .addRow(targetData.leaderboard ? "player-leaderboard-active" : "player-leaderboard-inactive", () -> {
                    updateLeaderboard(targetData, !targetData.leaderboard);
                    settings(uuid, targetData);
                })
                .start()
                    .add(local.t("settings-language-label", args("lang", local.getLanguageName(targetData.language, "auto"))), () -> {
                        session.pushHistory(() -> settings(uuid, targetData));
                        languageSelectionMenu(uuid, targetData, false);
                    })
                    .add(local.t("settings-translator-label", args("lang", local.getLanguageName(targetData.translatorLanguage, "off"))), () -> {
                        session.pushHistory(() -> settings(uuid, targetData));
                        languageSelectionMenu(uuid, targetData, true);
                    })
                .end()
                .addNavigationRow()
                .show();
    }

    public void languageSelectionMenu(String uuid, PlayerData targetData, boolean isTranslator) {
        Session session = sessionService.get(uuid).clear();
        if (session == null || session.data == null) return;
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
        Session session = sessionService.get(uuid).clear();
        if (session == null || session.data == null) return;
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
                "activeBadge", activeBadgeName(local, targetData)
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
                .addLocal("badge-clear-button", () -> {
                    updateActiveBadge(targetData, "", true, true);
                    badges(uuid, targetData);
                })
                .end()
                .addNavigationRow()
                .show();
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
        updatePlayerData(targetData,
                data -> data.language = language,
                data -> playerDataRepository.updateLanguage(data.uuid, language),
                null,
                false,
                false);
    }

    private void updateTranslatorLanguage(PlayerData targetData, String language) {
        updatePlayerData(targetData,
                data -> data.translatorLanguage = language,
                data -> playerDataRepository.updateTranslatorLanguage(data.uuid, language),
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

    private boolean containsBadgeLikeGlyphs(String input) {
        return input.codePoints().anyMatch(cp -> cp >= BADGE_ICON_RANGE_START && cp <= BADGE_ICON_RANGE_END);
    }
}
