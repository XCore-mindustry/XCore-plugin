package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.MenuService;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.model.PlayerData;

import static com.ospx.flubundle.Bundle.args;

import org.xcore.plugin.command.core.ClientController;
import org.xcore.plugin.ui.MenuSession;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class PlayerController implements ClientController {

    private final PlayerDataRepository playerDataRepository;
    private final PlayerSessionService playerSessionService;
    private final Config config;
    private final BundleService bundle;
    private final MenuService menuService;

    @Inject
    public PlayerController(PlayerDataRepository playerDataRepository,
                            PlayerSessionService playerSessionService,
                            Config config, BundleService bundle, MenuService menuService) {
        this.playerDataRepository = playerDataRepository;
        this.playerSessionService = playerSessionService;
        this.config = config;
        this.bundle = bundle;
        this.menuService = menuService;
    }

    @Override
    public int priority() {
        return 60;
    }

    @Command(name = "player", params = "[player-id]", aliases = {"stats", "player-statistics"})
    public void player(ClientContext ctx) {
        PlayerData data = ctx.args().length > 0
                ? playerSessionService.getOrLoadFromDb(ctx.argInt(0, -1))
                : playerSessionService.get(ctx.player().uuid());
        handlePlayer(ctx.player(), data);
    }

    @Command(name = "lb")
    public void leaderboard(ClientContext ctx) {
        var data = playerSessionService.get(ctx.player().uuid());
        data.leaderboard = !data.leaderboard;

        ctx.send("commands-lb-success", args(
                "leaderboardEnabled", String.valueOf(data.leaderboard)
        ));

        playerDataRepository.save(data);
    }

    @Command(name = "top")
    public void top(ClientContext ctx) {
        boolean isHexed = config.isMiniHexed();

        Seq<PlayerData> leaders = isHexed
                ? playerDataRepository.findLeaders("hexedRank", "hexedPoints")
                : playerDataRepository.findLeaders("pvpRating");

        if (leaders.isEmpty()) {
            ctx.send("empty", args());
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < leaders.size; i++) {
            var d = leaders.get(i);
            String key = isHexed ? "commands-top-hexed-content" : "commands-top-pvp-content";

            builder.append(ctx.format(key, args(
                    "index", i + 1,
                    "nickname", d.nickname,
                    "rating", d.pvpRating,
                    "rankName", ctx.format("hexed-ranks-" + d.hexedRank().name(), args()),
                    "points", d.hexedPoints
            ))).append("\n");
        }

        ctx.player().sendMessage(builder.toString());
    }

    public void handlePlayer(Player player, PlayerData data) {
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

        List<String> row1 = new ArrayList<>();
        row1.add(session.add(bundle.format(bundle.locale(player), "close", args()), () -> {}));
        rows.add(row1);

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, convertListToArray(rows));
    }

    private String[][] convertListToArray(List<List<String>> rows) {
        String[][] result = new String[rows.size()][];

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);

            result[i] = row.toArray(new String[0]);
        }

        return result;
    }
}
