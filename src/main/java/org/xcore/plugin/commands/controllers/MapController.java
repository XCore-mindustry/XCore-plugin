package org.xcore.plugin.commands.controllers;

import arc.Core;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.xcore.plugin.infra.commands.annotation.AdminOnly;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.context.CommandContext;
import org.xcore.plugin.modules.votes.VoteRtv;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.MapData;
import org.xcore.plugin.utils.models.PlayerData;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.PluginVars.*;

@SuppressWarnings("unused")
public class MapController {

    @Command(name = "rtv", params = "[map...]")
    public void rtv(CommandContext<Player> ctx) {
        if (vote != null) {
            ctx.send("error-vote-in-progress", args());
            return;
        }

        boolean isManual = ctx.args().length > 0;
        Map target = isManual
                ? Utils.findMap(ctx.arg(0))
                : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);

        if (target == null) {
            ctx.send("error-map-not-found", args());
            return;
        }

        vote = new VoteRtv(target, isManual);
        vote.vote(ctx.player(), 1);
    }

    @Command(name = "maps", params = "[page]")
    public void maps(CommandContext<Player> ctx) {
        Seq<Map> list = Utils.getAvailableMaps();
        int lines = 10;
        int pageCount = Mathf.ceil((float) list.size / lines);
        int page = ctx.argInt(0, 1);

        if (page < 1 || page > pageCount) {
            ctx.send("error-page-between", args("pageCount", pageCount));
            return;
        }

        StringBuilder builder = new StringBuilder(ctx.format("commands-maps-start-content", args(
                "mapName", Vars.state.map.name(),
                "page", page,
                "pageCount", pageCount
        )));

        long now = System.currentTimeMillis();
        for (int i = (page - 1) * lines; i < Math.min(page * lines, list.size); i++) {
            Map map = list.get(i);
            MapData m = database.mapDatas.get(map.plainName(), map.author(), Vars.state.rules.mode().name());

            String last = m.playedTimes == 0
                    ? ctx.format("never", args())
                    : (now - m.lastPlayedTime) / 60000 + "m";

            builder.append(ctx.format("commands-maps-content", args(
                    "index", i + 1,
                    "mapName", m.name,
                    "mapAuthor", m.author,
                    "mapWidth", map.width,
                    "mapHeight", map.height,
                    "mapReputation", m.reputation,
                    "mapLastPlayed", last
            )));
        }
        ctx.player().sendMessage(builder.toString());
    }

    @Command(name = "map-stats", params = "[map-name]", aliases = {"map"})
    public void mapStats(CommandContext<Player> ctx) {
        Map map = ctx.args().length > 0 ? Utils.findMap(ctx.arg(0)) : Vars.state.map;
        if (map == null) {
            ctx.send("error-map-not-found", args());
            return;
        }

        MapData m = database.mapDatas.get(map.plainName(), map.author(), Vars.state.rules.mode().name());
        String last = m.playedTimes == 0
                ? ctx.format("never", args())
                : (System.currentTimeMillis() - m.lastPlayedTime) / 60000 + "m";

        Call.infoMessage(ctx.player().con, ctx.format("commands-map-stats-content", args(
                "mapName", m.name,
                "mapAuthor", m.author,
                "mapDescription", map.description(),
                "mapWidth", map.width,
                "mapHeight", map.height,
                "mapReputation", m.reputation,
                "mapPopularity", String.format("%.1f", m.popularity),
                "mapInterest", String.format("%.1f", m.interest),
                "mapPlayedTimes", m.playedTimes,
                "mapPlayedTimesYear", m.playedTimesYear,
                "mapLastPlayed", last,
                "mapMin", m.minimumGameTime / 60000,
                "mapAvg", m.averageGameTime / 60000,
                "mapMax", m.maximumGameTime / 60000
        )));
    }

    @AdminOnly
    @Command(name = "artv", params = "[map...]")
    public void adminRtv(CommandContext<Player> ctx) {
        Map map = ctx.args().length > 0
                ? Utils.findMap(ctx.arg(0))
                : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);

        if (map == null) {
            ctx.send("error-map-not-found", args());
            return;
        }

        Timer.schedule(() -> {
            Utils.reloadWorld(() -> {
                Gamemode mode = Gamemode.valueOf(Core.settings.getString("lastServerMode"));
                Vars.world.loadMap(map, map.applyRules(mode));
            });
        }, mapLoadDelay);

        bundle.send("commands-artv-map-skipped", args(
                "nickname", ctx.player().coloredName()
        ));
    }

    @Command(name = "like", aliases = {"+"})
    public void like(CommandContext<Player> ctx) {
        handleReputation(ctx, true);
    }

    @Command(name = "dislike", aliases = {"-"})
    public void dislike(CommandContext<Player> ctx) {
        handleReputation(ctx, false);
    }

    private void handleReputation(CommandContext<Player> ctx, boolean like) {
        Map map = Vars.state.map;
        if (map == null) return;

        MapData m = database.mapDatas.get(map.plainName(), map.author(), Vars.state.rules.mode().name());
        PlayerData p = database.getCached(ctx.player().uuid());
        String id = String.valueOf(m.id);
        Boolean prev = p.mapVotes.get(id);

        if (Boolean.valueOf(like).equals(prev)) {
            ctx.send("error-already-voted", args());
            return;
        }

        int mod = (prev == null) ? 1 : 2;
        if (like) {
            m.reputation += mod;
            m.popularity += (mod * 2.0);
            ctx.send("commands-like-success", args());
        } else {
            m.reputation -= mod;
            m.popularity -= (mod * 2.0);
            ctx.send("commands-dislike-success", args());
        }

        p.mapVotes.put(id, like);
        p.save();
        m.save();
    }
}