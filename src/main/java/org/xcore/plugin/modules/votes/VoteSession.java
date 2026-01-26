package org.xcore.plugin.modules.votes;

import arc.math.Mathf;
import arc.struct.IntIntMap;
import arc.util.Timer;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import static arc.Core.app;
import static org.xcore.plugin.PluginVars.voteDuration;

public abstract class VoteSession {

    public final IntIntMap voted = new IntIntMap();
    public final Timer.Task end;

    public VoteSession() {
        end = Timer.schedule(this::fail, voteDuration);
    }

    public void vote(Player player, int sign) {
        voted.put(player.id, sign);
        if (votes() >= votesRequired()) app.post(this::success);
    }

    public abstract void left(Player player);

    public abstract void success();

    public abstract void fail();

    public void stop() {
        end.cancel();
    }

    public int votes() {
        return voted.values().toArray().sum();
    }

    public int votesRequired() {
        return Groups.player.size() > 2 ? Mathf.ceil(Groups.player.size() * 0.55f) : Groups.player.size();
    }
}
