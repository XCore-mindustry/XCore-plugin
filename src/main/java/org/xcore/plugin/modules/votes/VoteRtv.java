package org.xcore.plugin.modules.votes;

import arc.Core;
import arc.util.Timer;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.maps.Map;

import org.xcore.plugin.utils.models.MapData;
import static org.xcore.plugin.PluginVars.database;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.bundle;
import static org.xcore.plugin.PluginVars.mapLoadDelay;
import static org.xcore.plugin.utils.Utils.reloadWorld;

public class VoteRtv extends VoteSession {
    public final Map target;
    public final boolean isManualSelection;

    public VoteRtv(Map target, boolean isManualSelection) {
        this.target = target;
        this.isManualSelection = isManualSelection;
    }

    public VoteRtv(Map target) {
        this(target, false);
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        bundle.send("rtv-vote", args(
                "nickname", player.coloredName(),
                "mapName", target.name(),
                "votes", votes(),
                "votesRequired", votesRequired()));
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0)
            bundle.send("rtv-left", args(
                    "nickname", player.coloredName(),
                    "votes", votes(),
                    "votesRequired", votesRequired()));
    }

    @Override
    public void success() {
        stop();
        bundle.send("rtv-success", args(
                "mapName", target.name(),
                "mapLoadDelay", mapLoadDelay));

        if (state.map != null && !state.isMenu()) {
            String currentMapName = state.map.plainName();
            String currentAuthor = state.map.author();
            String currentMode = state.rules.mode().name();

            MapData currentMapStats = database.mapDatas.get(currentMapName, currentAuthor, currentMode);
            currentMapStats.onSkip();
            currentMapStats.save();
        }

        if (isManualSelection) {
            String targetMapName = target.plainName();
            String targetAuthor = target.author();
            String targetMode = state.rules.mode().name();

            MapData targetMapStats = database.mapDatas.get(targetMapName, targetAuthor, targetMode);
            targetMapStats.popularity += 2.0;
            targetMapStats.save();
        }
        Timer.schedule(() -> reloadWorld(() -> world.loadMap(target, target.applyRules(Gamemode.valueOf(Core.settings.getString("lastServerMode"))))), mapLoadDelay);
    }

    @Override
    public void fail() {
        stop();
        bundle.send("rtv-fail", args("mapName", target.name()));
    }
}
