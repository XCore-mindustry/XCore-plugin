package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerMenu extends Menu {

    private final PlayerDataRepository playerDataRepository;
    private final BundleService bundle;

    @Inject
    public PlayerMenu(Config config, GlobalConfig globalConfig, SessionService sessionService, PlayerDataRepository playerDataRepository, BundleService bundle) {
        super(config, globalConfig, sessionService);
        this.playerDataRepository = playerDataRepository;
        this.bundle = bundle;
    }

    public void player(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid).clear();

        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        Localization local = session.locale();
        boolean isOwner = session.data.uuid.equals(targetData.uuid);

        session.builder()
                .title("player-menu-player-title")
                .content("player-menu-player-content", args(
                        "nickname", targetData.nickname,
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
        int totalPlayers = (int) playerDataRepository.count(session.sortStatus);
        int perPage = globalConfig.eventsPerPage;
        var pagination = CustomGatherers.calculatePagination(totalPlayers, perPage);

        if (totalPlayers == 0) {
            session.locale().send("player-menu-players-empty");
            return;
        }

        int validPage = pagination.clampPage(page);
        int skip = (validPage - 1) * perPage;
        List<PlayerData> players = playerDataRepository.findPage(skip, perPage, session.sortStatus);

        session.builder()
                .title("player-menu-players-title")
                .content("player-menu-players-content", args("page", validPage, "total", pagination.totalPages()))

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

        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        boolean isOwner = session.data.uuid.equals(targetData.uuid);
        if (!(isOwner || session.player.admin)) {
            session.locale().send("error-no-access");
        }

        Localization local = session.locale();
        String leaderboardText = targetData.leaderboard ? "player-leaderboard-active" : "player-leaderboard-inactive";
        String currentLangName = Objects.equals(targetData.language, "auto")
            ? local.t("auto")
            : bundle.locale(targetData.language).getDisplayLanguage(bundle.locale(targetData.language));

        String currentTranslatorLangName = Objects.equals(targetData.translatorLanguage, "auto")
            ? local.t("off")
            : bundle.locale(targetData.translatorLanguage).getDisplayLanguage(bundle.locale(targetData.translatorLanguage));


        session.builder().title("player-menu-settings-title")
                .content("player-menu-settings-content", args(
                        "leaderboard", targetData.leaderboard ? local.t("yes") : local.t("no"),
                        "language", targetData.language,
                        "translatorLanguage", targetData.translatorLanguage
                ))
                .addRow(leaderboardText, () -> {
                    targetData.leaderboard = !targetData.leaderboard;
                    playerDataRepository.save(targetData);
                    settings(uuid, targetData);
                })
                .add(local.t("settings-language-label", args("lang", currentLangName)), () -> {
                    session.pushHistory(() -> settings(uuid, targetData));
                    languageSelectionMenu(uuid, targetData, false);
                    playerDataRepository.save(targetData);
                }).end()
                .add(local.t("settings-language-label", args("lang", currentTranslatorLangName)), () -> {
                    session.pushHistory(() -> settings(uuid, targetData));
                    languageSelectionMenu(uuid, targetData, true);
                    playerDataRepository.save(targetData);
                }).end()
                .addNavigationRow()
                .show();
    }

    public void languageSelectionMenu(String uuid, PlayerData targetData, boolean isTranslator) {
        Session session = sessionService.get(uuid).clear();

        Seq<Locale> locales = bundle.getAvailableLocales();

        var builder = session.builder()
                .title(isTranslator ? "player-menu-settings-translator-title" : "player-menu-settings-language-title")
                .start()
                    .addLocal(isTranslator ? "default" : "auto", () -> {
                        if (isTranslator) targetData.translatorLanguage = "off";
                        else targetData.language = "auto";
                        playerDataRepository.save(targetData);
                        session.popHistory().run();
                    })
                .end();

        builder.addForEach(locales, (b, loc) -> {
            String code = Objects.equals(loc.getLanguage(), "uk") ? "uk_UA" : loc.getLanguage();
            String langName = Strings.capitalize(loc.getDisplayLanguage(loc));

            b.addRow(langName, () -> {
                if (isTranslator) targetData.translatorLanguage = code;
                else targetData.language = code;
                playerDataRepository.save(targetData);
                session.popHistory().run();
            });
        });

        builder.addNavigationRow().show();
    }
}