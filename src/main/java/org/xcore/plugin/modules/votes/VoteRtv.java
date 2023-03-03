package org.xcore.plugin.modules.votes;

import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.maps.Map;

import static mindustry.Vars.world;
import static org.xcore.plugin.PluginVars.mapLoadDelay;
import static org.xcore.plugin.utils.Utils.reloadWorld;

public class VoteRtv extends VoteSession {
    public final Map target;

    public VoteRtv(Map target) {
        this.target = target;
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        Call.sendMessage(Strings.format("@[lightgray] voted to change the current map to [orange]@[lightgray]. ([accent]@[]/[accent]@[])\nType [orange]y[] or [orange]n[] to vote.",
                player.coloredName(), target.name(), votes(), votesRequired()));
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0)
            Call.sendMessage(Strings.format("@[lightgray] left. His vote to change the current map was cancelled. ([accent]@[]/[accent]@[])",
                    player.coloredName(), votes(), votesRequired()));
    }

    @Override
    public void success() {
        stop();
        Call.sendMessage(Strings.format("[orange]Vote passed. Map [accent]@[] will be loaded in [accent]@[] seconds...",
                target.name(), mapLoadDelay));
        Timer.schedule(() -> reloadWorld(() -> world.loadMap(target, target.applyRules(Vars.state.rules.mode()))), mapLoadDelay);
    }

    @Override
    public void fail() {
        stop();
        Call.sendMessage(Strings.format("[lightgray]Vote failed. Not enough votes to change the current map to [orange]@[].",
                target.name()));
    }
}
