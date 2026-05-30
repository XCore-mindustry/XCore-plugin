package org.xcore.plugin.vote;

import io.avaje.inject.AssistFactory;
import io.avaje.inject.Assisted;
import jakarta.inject.Inject;
import mindustry.gen.Player;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@AssistFactory(VoteEventFactory.class)
public class VoteEvent extends VoteSession {
    public final EventData target;

    private final EventDataRepository eventDataRepository;
    private final SessionService sessionService;
    private final VoteService voteService;

    @Inject
    public VoteEvent(
            @Assisted EventData target, EventDataRepository eventDataRepository,

            TomlSecretsConfig secretsConfig,
            SessionService sessionService,
            VoteService voteService) {
        super(secretsConfig);
        this.target = target;
        this.eventDataRepository = eventDataRepository;
        this.sessionService = sessionService;
        this.voteService = voteService;
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        sessionService.broadcast("vote-event-vote", args(
                "nickname", player.coloredName(),
                "name", target.name,
                "votes", votes(),
                "votesRequired", votesRequired()));
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0) {
            sessionService.broadcast("vote-event-left", args(
                    "nickname", player.coloredName(),
                    "votes", votes(),
                    "votesRequired", votesRequired()));
        }
    }

    @Override
    public void success() {
        stop();
        if (!target.isActive) {
            eventDataRepository.activateEvent(target);
        }

        sessionService.broadcast("vote-event-success", args("name", target.name));
    }

    @Override
    public void fail() {
        stop();
        sessionService.broadcast("vote-event-fail", args("name", target.name));
    }

    @Override
    public void cancelByAdmin(Player admin) {
        stop();
        sessionService.broadcast("vote-event-cancelled", args(
                "name", target.name,
                "admin", admin.coloredName()));
    }

    @Override
    public void stop() {
        voteService.endVote();
        if (end != null) end.cancel();
    }
}
