package org.xcore.plugin.event.net.chat;

import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteKick;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.vote.VoteSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoteChatInterceptorTest {

    @Test
    @DisplayName("rejects vote from current VoteKick target")
    void rejectsVoteFromCurrentVoteKickTarget() {
        SessionService sessionService = mock(SessionService.class);
        VoteService voteService = mock(VoteService.class);
        VoteChatInterceptor interceptor = new VoteChatInterceptor(sessionService, voteService);

        Player author = mock(Player.class);
        Session session = mock(Session.class);
        Localization localization = mock(Localization.class);
        VoteSession currentSession = mock(VoteSession.class);
        VoteKick currentKick = mock(VoteKick.class);

        when(voteService.isVoting()).thenReturn(true);
        when(voteService.getCurrentSession()).thenReturn(currentSession);
        when(voteService.getCurrentVoteKick()).thenReturn(currentKick);
        when(currentKick.isTarget(author)).thenReturn(true);
        when(sessionService.get(author)).thenReturn(session);
        when(session.locale()).thenReturn(localization);

        boolean intercepted = interceptor.intercept(author, "y");

        assertThat(intercepted).isTrue();
        verify(localization).send(eq("error-vote-yourself"), anyMap());
        verify(currentSession, never()).vote(author, 1);
    }
}
