package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.bson.types.ObjectId;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.concurrent.Async;
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
    private final Async async;

    @Inject
    public EventService(EventDataRepository eventDataRepository,
                        SessionService sessionService,
                        VoteService voteService,
                        VoteEventFactory voteEventFactory,
                        Async async) {
        this.eventDataRepository = eventDataRepository;
        this.sessionService = sessionService;
        this.voteService = voteService;
        this.voteEventFactory = voteEventFactory;
        this.async = async;
    }

    public void startVoteSession(Player player, EventData target, boolean forced) {
        var session = sessionService.get(player.uuid());
        if (voteService.shouldBlockVoteStart(VoteEvent.class, forced)) {
            session.locale().send("error-vote-in-progress");
            return;
        }

        if (forced && voteService.isVoting()) {
            voteService.endVote();
        }

        if (target == null) {
            session.locale().send("error-event-not-found");
            return;
        }

        if (forced) {
            activateEventImmediately(player, target);
        } else {
            startEventVote(player, target);
        }
    }

    /**
     * Counts the player's vote on an event. A vote already given the same way changes nothing.
     *
     * @param event the event as the player sees it; its counts are moved along with the stored ones
     * @return whether the vote was counted
     */
    public boolean handleReputation(Player player, boolean like, EventData event) {
        var session = sessionService.get(player.uuid());
        PlayerData p = session.data;
        Boolean previousVote = p.eventVotes.get(event.id.toString());

        if (Boolean.valueOf(like).equals(previousVote)) {
            session.locale().send("error-already-voted");
            return false;
        }

        int likeDelta = like ? 1 : previousVote != null ? -1 : 0;
        int dislikeDelta = like ? previousVote != null ? -1 : 0 : 1;
        event.like += likeDelta;
        event.dislike += dislikeDelta;
        if (like) {
            session.locale().send(previousVote != null ? "like-event-changed" : "like-event-success");
        } else {
            session.locale().send(previousVote != null ? "dislike-event-changed" : "dislike-event-success");
        }

        sessionService.putEventVote(session, event.id.toString(), like);
        ObjectId id = event.id;
        async.run(() -> eventDataRepository.applyVote(id, likeDelta, dislikeDelta));
        return true;
    }

    public void checkTimedEvents() {
        long now = System.currentTimeMillis();
        eventDataRepository.findActive().ifPresent(event -> {
            if (event.isTemporary && event.plannedEndTime > 0 && now > event.plannedEndTime) {
                eventDataRepository.finishActiveEvent();
                PLog.info("Event '@' finished by timer.", event.name);
                sessionService.broadcast("event-end", args("name", event.name));
            }
        });
    }

    public void finishActiveEvent() {
        eventDataRepository.finishActiveEvent();
    }

    private void activateEventImmediately(Player player, EventData target) {
        eventDataRepository.activateEvent(target);
        sessionService.broadcast(
                "commands-artv-event-skipped",
                args("name", target.name, "nickname", player.coloredName())
        );
    }

    private void startEventVote(Player player, EventData target) {
        var vote = voteEventFactory.create(target);
        voteService.startVote(vote);
        vote.vote(player, 1);
    }
}
