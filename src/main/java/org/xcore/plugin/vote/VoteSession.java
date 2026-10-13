package org.xcore.plugin.vote;

import arc.math.Mathf;
import arc.struct.IntIntMap;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.util.concurrent.atomic.AtomicBoolean;

import static arc.Core.app;

public abstract class VoteSession {

    public final IntIntMap voted = new IntIntMap();
    private final ScheduledTasks tasks = new ScheduledTasks(getClass());
    public final ScheduledTasks.Handle end;
    protected final VoteService voteService;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public VoteSession(TomlSecretsConfig secretsConfig, VoteService voteService) {
        this.voteService = voteService;
        this.end = tasks.after(secretsConfig.moderation.votekick.voteDurationSeconds, this::fail);
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
        if (votes() >= votesRequired()) app.post(() -> {
            if (!isStopped()) success();
        });
    }

    public abstract void left(Player player);

    public abstract void success();

    public abstract void fail();

    public abstract void cancelByAdmin(Player admin);

    /** Whether the vote is about the round being played and must not survive a world reset. */
    public boolean endsWithRound() {
        return false;
    }

    /** Called instead of {@link #fail()} when the round ends under a round-bound vote. */
    public void roundEnded() {
        stop();
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        tasks.close();
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
