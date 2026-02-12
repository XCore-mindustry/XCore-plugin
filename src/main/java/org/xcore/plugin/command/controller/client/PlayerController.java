package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.ui.MenuSession;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerController implements CloudClientController {

    private final PlayerDataRepository playerDataRepository;
    private final PlayerSessionService playerSessionService;
    private final BundleService bundle;
    private final Config config;
    private final GlobalConfig globalConfig;
    private final MenuService menuService;

    @Inject
    public PlayerController(PlayerDataRepository playerDataRepository,
                            PlayerSessionService playerSessionService,
                            BundleService bundle,
                            Config config, GlobalConfig globalConfig,
                            MenuService menuService) {
        this.playerDataRepository = playerDataRepository;
        this.playerSessionService = playerSessionService;
        this.bundle = bundle;
        this.config = config;
        this.globalConfig = globalConfig;
        this.menuService = menuService;
    }

    @Command("player|stats|player-statistics [id]")
    public void player(XCoreSender sender, @Argument("id") @Default("-1") int id) {

        PlayerData data = id == -1
                ? playerSessionService.get(sender.player().uuid())
                : playerSessionService.getOrLoadFromDb(id);

        handlePlayer(sender.player(), data);
    }

    @Command("lb")
    public void leaderboard(XCoreSender sender) {
        var data = playerSessionService.get(sender.player().uuid());
        data.leaderboard = !data.leaderboard;

        sender.send("commands-lb-success", args(
                "leaderboardEnabled", String.valueOf(data.leaderboard)
        ));

        playerDataRepository.save(data);
    }

    @Command("top")
    public void top(XCoreSender sender) {
        boolean isHexed = config.isMiniHexed();

        Seq<PlayerData> leaders = isHexed
                ? playerDataRepository.findLeaders("hexedRank", "hexedPoints")
                : playerDataRepository.findLeaders("pvpRating");

        if (leaders.isEmpty()) {
            sender.send("empty", args());
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < leaders.size; i++) {
            var d = leaders.get(i);
            String key = isHexed ? "commands-top-hexed-content" : "commands-top-pvp-content";

            builder.append(sender.format(key, args(
                    "index", i + 1,
                    "nickname", d.nickname,
                    "rating", d.pvpRating,
                    "rankName", sender.format("hexed-ranks-" + d.hexedRank().name(), args()),
                    "points", d.hexedPoints
            ))).append("\n");
        }

        sender.player().sendMessage(builder.toString());
    }

    private void handlePlayer(Player player, PlayerData data) {
        if (data == null) {
            bundle.send("error-player-not-found", args());
            return;
        }

        String menuTitle = bundle.format(bundle.locale(player),"player-menu-player-title", args());
        String menuContent = bundle.format(bundle.locale(player), "player-menu-player-content", args(
                "nickname", data.nickname,
                "pid", data.pid,
                "totalPlayTime", data.totalPlayTime,
                "hexedRankTag", data.hexedRank().tag,
                "hexedRankName", data.hexedRank().name(),
                "pvpRating", data.pvpRating
        ));

        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();
        List<List<String>> rows = new ArrayList<>();

        menuService.addNavigationRow(player, session, rows);

        List<String> row = new ArrayList<>();
        row.add(session.add(bundle.format(bundle.locale(player), "player-menu-players", args()), () -> {
            session.clearHistory();
            handlePlayers(player, 1);
        }));
        rows.add(row);

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }

    public void handlePlayers(Player player, int page) {
        int totalPlayers = (int) playerDataRepository.count();
        int perPage = globalConfig.eventsPerPage;
        var pagination = CustomGatherers.calculatePagination(totalPlayers, perPage);

        if (totalPlayers == 0) {
            bundle.send(player, "player-menu-players-empty", args());
            return;
        }

        int validPage = pagination.clampPage(page);
        String menuTitle = bundle.format(bundle.locale(player), "player-menu-players-title", args());
        String menuContent = bundle.format(bundle.locale(player), "player-menu-players-content", args(
                "page", validPage,
                "total", pagination.totalPages()
        ));

        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();
        List<List<String>> rows = new ArrayList<>();

        List<String> sortRow = new ArrayList<>();

        Runnable lambda = () -> {handlePlayers(player, page); };
        menuService.addStatusButton(player, session, sortRow, "admin", lambda);

        rows.add(sortRow);

        List<String> navRow = new ArrayList<>();
        if (validPage > 1) {
            navRow.add(session.add(bundle.format(bundle.locale(player), "previous", args()), () -> handlePlayers(player, validPage - 1)));
        }
        if (validPage < pagination.totalPages()) {
            navRow.add(session.add(bundle.format(bundle.locale(player), "next", args()), () -> handlePlayers(player, validPage + 1)));
        }
        if (!navRow.isEmpty()) rows.add(navRow);

        int skip = (validPage - 1) * perPage;
        List<PlayerData> players = playerDataRepository.findPage(skip, perPage, session.sortStatus);

        for (PlayerData playerData : players) {
            String buttonText = playerData.nickname;
            rows.add(List.of(session.add(buttonText, () -> {
                session.pushHistory(() -> handlePlayers(player, validPage));
                handlePlayer(player, playerData);
            })));
        }

        menuService.addNavigationRow(player, session, rows);

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }

}
