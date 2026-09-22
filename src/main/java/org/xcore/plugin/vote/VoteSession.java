package org.xcore.plugin.vote;

import arc.math.Mathf;
import arc.struct.IntIntMap;
import arc.util.Timer;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.util.concurrent.atomic.AtomicBoolean;

import static arc.Core.app;

public abstract class VoteSession {

    public final IntIntMap voted = new IntIntMap();
    public final Timer.Task end;
    protected final VoteService voteService;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public VoteSession(TomlSecretsConfig secretsConfig, VoteService voteService) {
        this.voteService = voteService;
        this.end = Timer.schedule(this::fail, secretsConfig.moderation.votekick.voteDurationSeconds);
    }

    public VoteSession(TomlSecretsConfig secretsConfig) {
        this(secretsConfig, null);
    }

    public boolean isStopped() {
        return stopped.get();
    }

    public void vote(Player player, int sign) {
        if (isStopped()) return;
        voted.put(player.id, sign);
        if (votes() >= votesRequired()) app.post(this::success);
    }

    public abstract void left(Player player);

    public abstract void success();

    public abstract void fail();

    public abstract void cancelByAdmin(Player admin);

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        if (end != null) {
            end.cancel();
        }
        if (voteService != null) {
            voteService.endVote();
        }
    }

    public int votes() {
        int sum = 0;
        var values = voted.values();
        while (values.hasNext()) {
            sum += values.next();
        }
        return sum;
    }

    public int votesRequired() {
        return Groups.player.size() > 2 ? Mathf.ceil(Groups.player.size() * 0.55f) : Groups.player.size();
    }
}
