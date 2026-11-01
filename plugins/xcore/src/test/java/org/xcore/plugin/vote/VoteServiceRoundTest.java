package org.xcore.plugin.vote;

import arc.Application;
import arc.Core;
import arc.Events;
import mindustry.game.EventType;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.event.RoundScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;

class VoteServiceRoundTest {
    private RoundScope round;
    private VoteService voteService;

    @BeforeEach
    void setUp() {
        Core.app = mock(Application.class);
        when(Core.app.isOnMainThread()).thenReturn(true);
        round = new RoundScope();
        round.init();
        voteService = new VoteService(round);
    }

    @AfterEach
    void tearDown() {
        voteService.stop();
        round.close();
        Core.app = null;
    }

    @Test
    void roundBoundVoteEndsWhenTheWorldResets() {
        var session = new TestVoteSession(voteService, true);
        voteService.startVote(session);

        Events.fire(new EventType.ResetEvent());

        assertThat(session.roundEndedCalls).isEqualTo(1);
        assertThat(session.isStopped()).isTrue();
        assertThat(voteService.isVoting()).isFalse();
    }

    @Test
    void voteThatIsNotAboutTheRoundSurvivesAWorldReset() {
        var session = new TestVoteSession(voteService, false);
        voteService.startVote(session);

        Events.fire(new EventType.ResetEvent());

        assertThat(session.isStopped()).isFalse();
        assertThat(voteService.getCurrentSession()).isSameAs(session);
        session.stop();
    }

    @Test
    void voteFinishedBeforeTheResetIsNotEndedTwice() {
        var finished = new TestVoteSession(voteService, true);
        voteService.startVote(finished);
        finished.stop();
        var next = new TestVoteSession(voteService, false);
        voteService.startVote(next);

        Events.fire(new EventType.ResetEvent());

        assertThat(finished.roundEndedCalls).isZero();
        assertThat(voteService.getCurrentSession()).isSameAs(next);
        next.stop();
    }

    @Test
    void successAlreadyPostedToTheMainLoopDoesNotRunAfterReset() {
        var queued = new ArrayList<Runnable>();
        doAnswer(invocation -> {
            queued.add(invocation.getArgument(0));
            return null;
        }).when(Core.app).post(any(Runnable.class));
        var session = new TestVoteSession(voteService, true);
        voteService.startVote(session);
        session.vote(mock(Player.class), 1);

        Events.fire(new EventType.ResetEvent());
        queued.forEach(Runnable::run);

        assertThat(queued).hasSize(1);
        assertThat(session.successCalls).isZero();
    }

    @Test
    void shutdownEndsEvenVotesThatSurviveWorldReset() {
        var session = new TestVoteSession(voteService, false);
        voteService.startVote(session);

        voteService.stop();

        assertThat(session.isStopped()).isTrue();
        assertThat(session.end.isActive()).isFalse();
        assertThat(voteService.isVoting()).isFalse();
    }

    private static final class TestVoteSession extends VoteSession {
        private final boolean endsWithRound;
        private int roundEndedCalls;
        private int successCalls;

        private TestVoteSession(VoteService voteService, boolean endsWithRound) {
            super(new TomlSecretsConfig(), voteService);
            this.endsWithRound = endsWithRound;
        }

        @Override
        public boolean endsWithRound() {
            return endsWithRound;
        }

        @Override
        public void roundEnded() {
            roundEndedCalls++;
            super.roundEnded();
        }

        @Override
        public void left(Player player) { }

        @Override
        public void success() { successCalls++; }

        @Override
        public int votesRequired() { return 1; }

        @Override
        public void fail() { }

        @Override
        public void cancelByAdmin(Player admin) { }
    }
}
