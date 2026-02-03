package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.model.PlayerData;

import static com.ospx.flubundle.Bundle.args;

import org.xcore.plugin.command.core.ClientController;

@Singleton
public class StatsController implements ClientController {

    private final PlayerDataRepository playerDataRepository;
    private final PlayerSessionService playerSessionService;
    private final Config config;

    @Inject
    public StatsController(PlayerDataRepository playerDataRepository,
                           PlayerSessionService playerSessionService,
                           Config config) {
        this.playerDataRepository = playerDataRepository;
        this.playerSessionService = playerSessionService;
        this.config = config;
    }

    @Override
    public int priority() {
        return 60;
    }

    @Command(name = "stats", params = "[player-id]")
    public void stats(ClientContext ctx) {
        PlayerData data = ctx.args().length > 0
                ? playerSessionService.getOrLoadFromDb(ctx.argInt(0, -1))
                : playerSessionService.get(ctx.player().uuid());

        if (data == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        Call.infoMessage(ctx.player().con, ctx.format("commands-stats-content", args(
                "nickname", data.nickname,
                "pid", data.pid,
                "totalPlayTime", data.totalPlayTime,
                "hexedRankTag", data.hexedRank().tag,
                "hexedRankName", data.hexedRank().name(),
                "pvpRating", data.pvpRating
        )));
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
}
