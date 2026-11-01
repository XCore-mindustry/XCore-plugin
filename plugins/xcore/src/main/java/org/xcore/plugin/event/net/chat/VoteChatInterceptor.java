package org.xcore.plugin.event.net.chat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteChoice;
import org.xcore.plugin.vote.VoteKick;
import org.xcore.plugin.vote.VoteService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class VoteChatInterceptor {

    private final SessionService sessionService;
    private final VoteService voteService;

    @Inject
    public VoteChatInterceptor(SessionService sessionService, VoteService voteService) {
        this.sessionService = sessionService;
        this.voteService = voteService;
    }

    public boolean intercept(Player author, String text) {
        VoteChoice choice = VoteChoice.parse(text);
        if (!choice.isValid() || !voteService.isVoting()) {
            return false;
        }

        var currentVote = voteService.getCurrentSession();
        VoteKick currentKick = voteService.getCurrentVoteKick();
        if (currentKick != null && currentKick.isTarget(author)) {
            var session = sessionService.get(author);
            if (session != null) {
                session.locale().send("error-vote-yourself", args());
            }
            return true;
        }

        if (currentVote.voted.containsKey(author.id)) {
            var session = sessionService.get(author);
            if (session != null) {
                session.locale().send("error-already-voted", args());
            }
            return true;
        }

        currentVote.vote(author, choice.sign());
        return true;
    }
}
