package org.xcore.plugin.vote;

import io.avaje.inject.AssistFactory;
import io.avaje.inject.Assisted;
import jakarta.inject.Inject;
import mindustry.Vars;
import mindustry.gen.Player;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;

@AssistFactory(VoteNewWaveFactory.class)
public class VoteNewWave extends VoteSession {

    public final int sourceWave;

    private final SessionService sessionService;
    private final VoteService voteService;

    @Inject
    public VoteNewWave(
            @Assisted int sourceWave,
            GlobalConfig globalConfig,
            SessionService sessionService,
            VoteService voteService
    ) {
        super(globalConfig);
        this.sourceWave = sourceWave;
        this.sessionService = sessionService;
        this.voteService = voteService;
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        sessionService.broadcast("vnw-vote", args(
                "nickname", player.coloredName(),
                "wave", targetWave(),
                "votes", votes(),
                "votesRequired", votesRequired()));
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0) {
            sessionService.broadcast("vnw-left", args(
                    "nickname", player.coloredName(),
                    "wave", targetWave(),
                    "votes", votes(),
                    "votesRequired", votesRequired()));
        }
    }

    @Override
    public void success() {
        stop();

        if (state.wave != sourceWave) {
            sessionService.broadcast("vnw-obsolete", args("wave", targetWave()));
            return;
        }

        Vars.logic.skipWave();
        sessionService.broadcast("vnw-success", args("wave", targetWave()));
    }

    @Override
    public void fail() {
        stop();
        sessionService.broadcast("vnw-fail", args("wave", targetWave()));
    }

    @Override
    public void cancelByAdmin(Player admin) {
        stop();
        sessionService.broadcast("vnw-cancelled", args(
                "wave", targetWave(),
                "admin", admin.coloredName()));
    }

    @Override
    public void stop() {
        voteService.endVote();
        if (end != null) {
            end.cancel();
        }
    }

    private int targetWave() {
        return sourceWave + 1;
    }
}
