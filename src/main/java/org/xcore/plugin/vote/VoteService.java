package org.xcore.plugin.vote;

import jakarta.inject.Singleton;
import lombok.Getter;
import mindustry.gen.Player;

@Singleton
public class VoteService {
    @Getter
    private VoteSession currentSession;

    public boolean startVote(VoteSession session) {
        if (currentSession != null) return false;
        this.currentSession = session;
        return true;
    }

    public void endVote() {
        if (currentSession != null) {
            if (currentSession.end != null && !currentSession.end.isScheduled()) {
                currentSession.end.cancel();
            }
        }
        this.currentSession = null;
    }

    public boolean isVoting() {
        return currentSession != null;
    }

    public VoteKick getCurrentVoteKick() {
        if (currentSession instanceof VoteKick kick) {
            return kick;
        }
        return null;
    }

    public void handleLeave(Player player) {
        if (currentSession != null) {
            currentSession.left(player);
        }
    }
}