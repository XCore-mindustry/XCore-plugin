package org.xcore.plugin.event;

import com.google.gson.Gson;
import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.security.ingress.IngressService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.service.TranslatorService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteKick;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.vote.VoteSession;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;

class NetEventServiceTest {

    @Test
    @DisplayName("chat vote rejects vote from current VoteKick target")
    void chatVoteRejectsVoteFromCurrentVoteKickTarget() {
        SessionService sessionService = mock(SessionService.class);
        Config config = new Config();
        TranslatorService translatorService = mock(TranslatorService.class);
        NetworkService network = mock(NetworkService.class);
        VoteService voteService = mock(VoteService.class);
        SecurityService securityService = mock(SecurityService.class);
        IngressService ingressService = mock(IngressService.class);

        NetEventService service = new NetEventService(
                sessionService,
                config,
                translatorService,
                network,
                voteService,
                securityService,
                ingressService,
                new Gson()
        );

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

        service.chat(author, "y");

        verify(localization).send(eq("error-vote-yourself"), anyMap());
        verify(currentSession, never()).vote(author, 1);
        verify(network, never()).post(org.mockito.ArgumentMatchers.any());
    }
}
