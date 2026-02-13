package org.xcore.plugin.ui.menu;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerMenu extends Menu {

    private final PlayerDataRepository playerDataRepository;

    @Inject
    public PlayerMenu(Config config, GlobalConfig globalConfig, SessionService sessionService, PlayerDataRepository playerDataRepository) {
        super(config, globalConfig, sessionService);
        this.playerDataRepository = playerDataRepository;
    }

    public void player(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid).clear();

        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        session.builder()
                .title("player-menu-player-title")
                .content("player-menu-player-content", args(
                        "nickname", targetData.nickname,
                        "pid", targetData.pid,
                        "totalPlayTime", targetData.totalPlayTime,
                        "hexedRankTag", targetData.hexedRank().tag,
                        "hexedRankName", targetData.hexedRank().name(),
                        "pvpRating", targetData.pvpRating
                ))
                .addRow("player-menu-players", () -> {
                    session.clearHistory();
                    players(uuid, 1);
                })
                .addNavigationRow()
                .show();
    }

    public void players(String uuid, int page) {
        Session session = sessionService.get(uuid).clear();
        int totalPlayers = (int) playerDataRepository.count();
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
                    .ifAdd(validPage > 1, "previous", () -> players(uuid, validPage - 1))
                    .ifAdd(validPage < pagination.totalPages(), "next", () -> players(uuid, validPage + 1))
                .end()

                .addForEach(players, (b, pData) -> b.addRow(pData.nickname, () -> {
                    session.pushHistory(() -> players(uuid, validPage));
                    player(uuid, pData);
                }))

                .addNavigationRow()
                .show();
    }
}