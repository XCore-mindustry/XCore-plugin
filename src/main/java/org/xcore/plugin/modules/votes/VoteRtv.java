package org.xcore.plugin.modules.votes;

import arc.Core;
import arc.util.Timer;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.maps.Map;

import static mindustry.Vars.world;
import static org.xcore.plugin.PluginVars.mapLoadDelay;
import static org.xcore.plugin.utils.Utils.reloadWorld;
import static useful.Bundle.send;

public class VoteRtv extends VoteSession {
    public final Map target;

    public VoteRtv(Map target) {
        this.target = target;
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        send("rtv.vote", player.coloredName(), target.name(), votes(), votesRequired());
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0)
            send("rtv.left", player.coloredName(), votes(), votesRequired());
    }

    @Override
    public void success() {
        stop();
        send("rtv.success", target.name(), mapLoadDelay);
        Timer.schedule(() -> reloadWorld(() -> world.loadMap(target, target.applyRules(Gamemode.valueOf(Core.settings.getString("lastServerMode"))))), mapLoadDelay);
    }

    @Override
    public void fail() {
        stop();
        send("rtv.fail", target.name());
    }
}