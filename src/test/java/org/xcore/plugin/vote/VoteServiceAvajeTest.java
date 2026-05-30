package org.xcore.plugin.vote;

import arc.Application;
import arc.Core;
import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VoteServiceAvajeTest {

    private BeanScope scope;
    private VoteService voteService;

    @BeforeEach
    void setUp() {
        Core.app = mock(Application.class);

        scope = BeanScope.builder()
                .modules(new VoteServiceModule())
                .forTesting()
                .build();

        voteService = scope.get(VoteService.class);
    }

    @AfterEach
    void tearDown() {
        scope.close();
        Core.app = null;
    }

    @Test
    @DisplayName("startVote returns true when no active session exists")
    void startVoteReturnsTrueWhenNoSession() {
        var session = new TestVoteSession();

        var started = voteService.startVote(session);

        assertThat(started).isTrue();
        assertThat(voteService.getCurrentSession()).isSameAs(session);
    }

    @Test
    @DisplayName("startVote returns false when an active session already exists")
    void startVoteReturnsFalseWhenSessionAlreadyActive() {
        var first = new TestVoteSession();
        var second = new TestVoteSession();
        voteService.startVote(first);

        var started = voteService.startVote(second);

        assertThat(started).isFalse();
        assertThat(voteService.getCurrentSession()).isSameAs(first);
    }

    @Test
    @DisplayName("endVote resets currentSession to null")
    void endVoteResetsCurrentSession() {
        voteService.startVote(new TestVoteSession());

        voteService.endVote();

        assertThat(voteService.getCurrentSession()).isNull();
    }

    @Test
    @DisplayName("isVoting reflects current session state")
    void isVotingReflectsState() {
        assertThat(voteService.isVoting()).isFalse();

        voteService.startVote(new TestVoteSession());
        assertThat(voteService.isVoting()).isTrue();

        voteService.endVote();
        assertThat(voteService.isVoting()).isFalse();
    }

    @Test
    @DisplayName("shouldBlockVoteStart returns false when no session exists")
    void shouldBlockVoteStartReturnsFalseWhenNoSession() {
        assertThat(voteService.shouldBlockVoteStart(TestVoteSession.class, false)).isFalse();
    }

    @Test
    @DisplayName("shouldBlockVoteStart returns true when forced is false and session exists")
    void shouldBlockVoteStartReturnsTrueWhenForcedFalseAndSessionExists() {
        voteService.startVote(new TestVoteSession());

        assertThat(voteService.shouldBlockVoteStart(TestVoteSession.class, false)).isTrue();
    }

    @Test
    @DisplayName("shouldBlockVoteStart returns false when forced true and allowed type matches")
    void shouldBlockVoteStartReturnsFalseWhenForcedAndAllowedTypeMatches() {
        voteService.startVote(new TestVoteSession());

        assertThat(voteService.shouldBlockVoteStart(TestVoteSession.class, true)).isFalse();
    }

    @Test
    @DisplayName("shouldBlockVoteStart returns true when forced true and allowed type mismatches")
    void shouldBlockVoteStartReturnsTrueWhenForcedAndAllowedTypeMismatches() {
        voteService.startVote(new TestVoteSession());

        assertThat(voteService.shouldBlockVoteStart(VoteKick.class, true)).isTrue();
    }

    @Test
    @DisplayName("getCurrentVoteKick returns vote kick only for VoteKick session")
    void getCurrentVoteKickReturnsOnlyForVoteKickSession() {
        voteService.startVote(new TestVoteSession());
        assertThat(voteService.getCurrentVoteKick()).isNull();

        voteService.endVote();
        var voteKick = mock(VoteKick.class);
        voteService.startVote(voteKick);

        assertThat(voteService.getCurrentVoteKick()).isSameAs(voteKick);
    }

    @Test
    @DisplayName("handleLeave delegates to current session left(player)")
    void handleLeaveDelegatesToCurrentSession() {
        var player = mock(Player.class);
        var session = new TestVoteSession();
        voteService.startVote(session);

        voteService.handleLeave(player);

        assertThat(session.leftCalls).isEqualTo(1);
        assertThat(session.lastLeftPlayer).isSameAs(player);
    }

    private static final class VoteServiceModule implements AvajeModule {
        @Override
        public Class<?>[] classes() {
            return new Class<?>[]{VoteService.class};
        }

        @Override
        public void build(Builder builder) {
            if (builder.isBeanAbsent(VoteService.class)) {
                builder.register(new VoteService());
            }
        }
    }

    private static final class TestVoteSession extends VoteSession {
        private int leftCalls;
        private Player lastLeftPlayer;

        private TestVoteSession() {
            super(testConfig());
            // Prevent background timer activity in tests.
            end.cancel();
        }

        @Override
        public void left(Player player) {
            leftCalls++;
            lastLeftPlayer = player;
        }

        @Override
        public void success() {
        }

        @Override
        public void fail() {
        }

        @Override
        public void cancelByAdmin(Player admin) {
        }

        private static TomlSecretsConfig testConfig() {
            var config = new TomlSecretsConfig();
            config.moderation.votekick.voteDurationSeconds = 10_000.0f;
            return config;
        }
    }
}
