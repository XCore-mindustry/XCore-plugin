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
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
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
    private final Config config;
    private final MenuService menuService;

    @Inject
    public PlayerController(PlayerDataRepository playerDataRepository,
                            PlayerSessionService playerSessionService,
                            Config config,
                            MenuService menuService) {
        this.playerDataRepository = playerDataRepository;
        this.playerSessionService = playerSessionService;
        this.config = config;
        this.menuService = menuService;
    }

    @Command("player|stats|player-statistics [id]")
    public void player(XCoreSender sender,
                       @Argument("id") @Default("-1") int id) {

        PlayerData data = id == -1
                ? playerSessionService.get(sender.player().uuid())
                : playerSessionService.getOrLoadFromDb(id);

        handlePlayer(sender, data);
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

    private void handlePlayer(XCoreSender sender, PlayerData data) {
        if (data == null) {
            sender.send("error-player-not-found", args());
            return;
        }

        Player player = sender.player();

        String menuTitle = sender.format("player-menu-player-title", args());
        String menuContent = sender.format("player-menu-player-content", args(
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

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }
}
