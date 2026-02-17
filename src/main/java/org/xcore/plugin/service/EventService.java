package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteEvent;
import org.xcore.plugin.vote.VoteEventFactory;
import org.xcore.plugin.vote.VoteService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class EventService {

    private final EventDataRepository eventDataRepository;
    private final SessionService sessionService;
    private final VoteService voteService;
    private final VoteEventFactory voteEventFactory;

    @Inject
    public EventService(EventDataRepository eventDataRepository,
                        SessionService sessionService,
                        VoteService voteService,
                        VoteEventFactory voteEventFactory) {
        this.eventDataRepository = eventDataRepository;
        this.sessionService = sessionService;
        this.voteService = voteService;
        this.voteEventFactory = voteEventFactory;
    }

    public void startVoteSession(Player player, EventData target, boolean forced) {
        var session = sessionService.get(player.uuid());
        if (voteService.isVoting() && !(voteService.getCurrentSession() instanceof VoteEvent)) {
            session.locale().send("error-vote-in-progress");
            return;
        } else if (voteService.isVoting() && !forced) {
            session.locale().send("error-vote-in-progress");
            return;
        } else if (voteService.isVoting() && forced) {
            voteService.endVote();
        }

        if (target == null) {
            session.locale().send("error-event-not-found");
            return;
        }

        if (forced) {
            eventDataRepository.activateEvent(target);
            sessionService.broadcast("commands-artv-event-skipped", args("name", target.name, "nickname", player.coloredName()));
        } else {
            var vote = voteEventFactory.create(target);
            voteService.startVote(vote);
            vote.vote(player, 1);
        }
    }

    public void handleReputation(Player player, boolean like, EventData event) {
        var session = sessionService.get(player.uuid());
        PlayerData p = session.data;
        Boolean prev = p.eventVotes.get(event.id.toString());

        if (Boolean.valueOf(like).equals(prev)) {
            session.locale().send("error-already-voted");
            return;
        }

        if (like) {
            event.like += 1;
            if (prev != null) event.dislike -= 1;
            session.locale().send(prev != null ? "like-event-changed" : "like-event-success");
        } else {
            event.dislike += 1;
            if (prev != null) event.like -= 1;
            session.locale().send(prev != null ? "dislike-event-changed" : "dislike-event-success");
        }

        p.eventVotes.put(event.id.toString(), like);
        session.save();
        eventDataRepository.save(event);
    }

    public void checkTimedEvents() {
    long now = System.currentTimeMillis();
        eventDataRepository.findActive().ifPresent(event -> {
            if (event.isTemporary && event.plannedEndTime > 0 && now > event.plannedEndTime) {
                eventDataRepository.finishActiveEvent();
                arc.util.Log.info("[XCore] Event '@' automatically finished by timer.", event.name);
                sessionService.broadcast("event-end", args("name", event.name));
            }
        });
    }
}
