package org.xcore.plugin.commands.controllers.client;

import arc.struct.Seq;
import mindustry.gen.Call;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.utils.models.PlayerData;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.PluginVars.database;

@SuppressWarnings("unused")
public class StatsController {

    @Command(name = "stats", params = "[player-id]")
    public void stats(ClientContext ctx) {
        PlayerData data = ctx.args().length > 0
                ? database.getCachedOrDb(ctx.argInt(0, -1))
                : database.getCached(ctx.player().uuid());

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
        var data = database.getCached(ctx.player().uuid());
        data.leaderboard = !data.leaderboard;

        ctx.send("commands-lb-success", args(
                "leaderboardEnabled", String.valueOf(data.leaderboard)
        ));

        data.save();
    }

    @Command(name = "top")
    public void top(ClientContext ctx) {
        boolean isHexed = org.xcore.plugin.PluginVars.config.isMiniHexed();

        Seq<PlayerData> leaders = isHexed
                ? database.getPlayerDatas().getLeaders("hexedRank", "hexedPoints")
                : database.getPlayerDatas().getLeaders("pvpRating");

        if (leaders.isEmpty()) {
            ctx.send("empty", args());
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < leaders.size; i++) {
            var d = leaders.get(i);
            String key = isHexed ? "commands-top-hexed-content" : "commands-top-pvp-content";

            // Форматируем каждую строку топа
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