package org.xcore.plugin.vote;

import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import mindustry.gen.Player;
import org.xcore.plugin.event.RoundScope;
import org.xcore.plugin.startup.ShutdownOrder;

@Singleton
public class VoteService {
    @Getter
    private volatile VoteSession currentSession;
    private final RoundScope roundScope;

    @Inject
    public VoteService(RoundScope roundScope) {
        this.roundScope = roundScope;
    }

    public VoteService() {
        this(new RoundScope());
    }

    public boolean startVote(VoteSession session) {
        if (currentSession != null) return false;
        this.currentSession = session;
        if (session.endsWithRound()) {
            roundScope.onEnd(() -> {
                if (currentSession == session) session.roundEnded();
            });
        }
        return true;
    }

    public void endVote() {
        VoteSession session = this.currentSession;
        if (session == null) {
            return;
        }
        this.currentSession = null;
        session.stop();
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        endVote();
    }

    public boolean isVoting() {
        return currentSession != null;
    }

    public boolean shouldBlockVoteStart(Class<? extends VoteSession> allowedForcedType, boolean forced) {
        if (currentSession == null) {
            return false;
        }

        if (!forced) {
            return true;
        }

        return !allowedForcedType.isInstance(currentSession);
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
