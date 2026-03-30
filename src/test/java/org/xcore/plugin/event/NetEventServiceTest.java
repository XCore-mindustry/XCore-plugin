package org.xcore.plugin.event;

import com.google.gson.Gson;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.net.admin.AdminRequestHandler;
import org.xcore.plugin.event.net.chat.ChatMessageHandler;
import org.xcore.plugin.event.net.chat.VoteChatInterceptor;
import org.xcore.plugin.event.net.connect.ConnectionAccessHandler;
import org.xcore.plugin.event.net.connect.ConnectPacketHandler;
import org.xcore.plugin.event.net.connect.ConnectionFilterService;
import org.xcore.plugin.event.net.connect.PlayerConnectionBootstrap;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressService;
import org.xcore.plugin.service.ChatFormatService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.service.TranslatorService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetEventServiceTest {

    @Test
    @DisplayName("chat muted player does not translate or publish message")
    void chatMutedPlayer_doesNotTranslateOrPublish() {
        SessionService sessionService = mock(SessionService.class);
        Config config = new Config();
        TranslatorService translatorService = mock(TranslatorService.class);
        NetworkService network = mock(NetworkService.class);
        VoteService voteService = mock(VoteService.class);
        SecurityService securityService = mock(SecurityService.class);
        IngressService ingressService = mock(IngressService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        AdminRequestHandler adminRequestHandler = mock(AdminRequestHandler.class);
        VoteChatInterceptor voteChatInterceptor = new VoteChatInterceptor(sessionService, voteService);
        ConnectPacketHandler connectPacketHandler = new ConnectPacketHandler(
                new ConnectionAccessHandler(ingressService),
                new PlayerConnectionBootstrap()
        );
        ConnectionFilterService connectionFilterService = new ConnectionFilterService();
        ChatMessageHandler chatMessageHandler = new ChatMessageHandler(
                config,
                translatorService,
                network,
                securityService,
                chatFormatService,
                voteChatInterceptor
        );
        NetEventService service = new NetEventService(
                chatMessageHandler,
                adminRequestHandler,
                connectPacketHandler,
                connectionFilterService
        );

        Player author = mock(Player.class);

        when(voteService.isVoting()).thenReturn(false);
        when(securityService.isMuted(author)).thenReturn(true);

        service.chat(author, "hello");

        verify(chatFormatService, never()).formatChat(any(), any());
        verify(translatorService, never()).translate(any(), any());
        verify(network, never()).post(any());
    }

    @Test
    @DisplayName("chat happy path formats translates and publishes message")
    void chatHappyPath_formatsTranslatesAndPublishes() {
        SessionService sessionService = mock(SessionService.class);
        Config config = new Config();
        config.server = "main";
        TranslatorService translatorService = mock(TranslatorService.class);
        NetworkService network = mock(NetworkService.class);
        VoteService voteService = mock(VoteService.class);
        SecurityService securityService = mock(SecurityService.class);
        IngressService ingressService = mock(IngressService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        AdminRequestHandler adminRequestHandler = mock(AdminRequestHandler.class);
        VoteChatInterceptor voteChatInterceptor = new VoteChatInterceptor(sessionService, voteService);
        ConnectPacketHandler connectPacketHandler = new ConnectPacketHandler(
                new ConnectionAccessHandler(ingressService),
                new PlayerConnectionBootstrap()
        );
        ConnectionFilterService connectionFilterService = new ConnectionFilterService();
        ChatMessageHandler chatMessageHandler = new ChatMessageHandler(
                config,
                translatorService,
                network,
                securityService,
                chatFormatService,
                voteChatInterceptor
        );
        NetEventService service = new NetEventService(
                chatMessageHandler,
                adminRequestHandler,
                connectPacketHandler,
                connectionFilterService
        );

        Player author = mock(Player.class);
        when(author.plainName()).thenReturn("Tester");
        when(chatFormatService.formatChat(author, "he`llo")).thenReturn("formatted");
        when(voteService.isVoting()).thenReturn(false);
        when(securityService.isMuted(author)).thenReturn(false);

        service.chat(author, "he`llo");

        verify(chatFormatService).formatChat(author, "he`llo");
        verify(author).sendMessage("formatted", author, "he`llo");
        verify(translatorService).translate(author, "he`llo");
        verify(network).post(new SocketEvents.MessageEvent("Tester", "he*llo", "main"));
    }

    @Test
    @DisplayName("connect packet ignores already kicked connection")
    void connectPacket_ignoresAlreadyKickedConnection() {
        SessionService sessionService = mock(SessionService.class);
        Config config = new Config();
        TranslatorService translatorService = mock(TranslatorService.class);
        NetworkService network = mock(NetworkService.class);
        VoteService voteService = mock(VoteService.class);
        SecurityService securityService = mock(SecurityService.class);
        IngressService ingressService = mock(IngressService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        AdminRequestHandler adminRequestHandler = mock(AdminRequestHandler.class);
        VoteChatInterceptor voteChatInterceptor = new VoteChatInterceptor(sessionService, voteService);
        ConnectPacketHandler connectPacketHandler = new ConnectPacketHandler(
                new ConnectionAccessHandler(ingressService),
                new PlayerConnectionBootstrap()
        );
        ConnectionFilterService connectionFilterService = new ConnectionFilterService();
        ChatMessageHandler chatMessageHandler = new ChatMessageHandler(
                config,
                translatorService,
                network,
                securityService,
                chatFormatService,
                voteChatInterceptor
        );

        NetEventService service = new NetEventService(
                chatMessageHandler,
                adminRequestHandler,
                connectPacketHandler,
                connectionFilterService
        );

        NetConnection con = mock(NetConnection.class);
        con.kicked = true;
        Packets.ConnectPacket packet = new Packets.ConnectPacket();

        service.connectPacket(con, packet);

        verifyNoInteractions(ingressService);
    }

    @Test
    @DisplayName("connect packet closes connection on silent deny")
    void connectPacket_closesOnSilentDeny() {
        SessionService sessionService = mock(SessionService.class);
        Config config = new Config();
        TranslatorService translatorService = mock(TranslatorService.class);
        NetworkService network = mock(NetworkService.class);
        VoteService voteService = mock(VoteService.class);
        SecurityService securityService = mock(SecurityService.class);
        IngressService ingressService = mock(IngressService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        AdminRequestHandler adminRequestHandler = mock(AdminRequestHandler.class);
        VoteChatInterceptor voteChatInterceptor = new VoteChatInterceptor(sessionService, voteService);
        ConnectPacketHandler connectPacketHandler = new ConnectPacketHandler(
                new ConnectionAccessHandler(ingressService),
                new PlayerConnectionBootstrap()
        );
        ConnectionFilterService connectionFilterService = new ConnectionFilterService();
        ChatMessageHandler chatMessageHandler = new ChatMessageHandler(
                config,
                translatorService,
                network,
                securityService,
                chatFormatService,
                voteChatInterceptor
        );

        NetEventService service = new NetEventService(
                chatMessageHandler,
                adminRequestHandler,
                connectPacketHandler,
                connectionFilterService
        );

        NetConnection con = mock(NetConnection.class);
        Packets.ConnectPacket packet = new Packets.ConnectPacket();
        when(ingressService.validate(con, packet)).thenReturn(new AccessResult.Denied("blocked", true, 0));

        service.connectPacket(con, packet);

        verify(con).close();
    }

    @Test
    @DisplayName("connect filter accepts allowed ip without changing counters")
    void connectFilter_acceptsAllowedIp() {
        SessionService sessionService = mock(SessionService.class);
        Config config = new Config();
        TranslatorService translatorService = mock(TranslatorService.class);
        NetworkService network = mock(NetworkService.class);
        VoteService voteService = mock(VoteService.class);
        SecurityService securityService = mock(SecurityService.class);
        IngressService ingressService = mock(IngressService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        AdminRequestHandler adminRequestHandler = mock(AdminRequestHandler.class);
        VoteChatInterceptor voteChatInterceptor = new VoteChatInterceptor(sessionService, voteService);
        ConnectPacketHandler connectPacketHandler = new ConnectPacketHandler(
                new ConnectionAccessHandler(ingressService),
                new PlayerConnectionBootstrap()
        );
        ConnectionFilterService connectionFilterService = new ConnectionFilterService();
        ChatMessageHandler chatMessageHandler = new ChatMessageHandler(
                config,
                translatorService,
                network,
                securityService,
                chatFormatService,
                voteChatInterceptor
        );

        NetEventService service = new NetEventService(
                chatMessageHandler,
                adminRequestHandler,
                connectPacketHandler,
                connectionFilterService
        );

        service.setIpAcceptor(ip -> true);

        boolean allowed = service.connectFilter("1.2.3.4");

        org.assertj.core.api.Assertions.assertThat(allowed).isTrue();
        org.assertj.core.api.Assertions.assertThat(service.blockedIPs).isZero();
        org.assertj.core.api.Assertions.assertThat(service.blockedIPsPerMinute).isZero();
    }

    @Test
    @DisplayName("connect filter rejects blocked ip and increments counters")
    void connectFilter_rejectsBlockedIpAndIncrementsCounters() {
        SessionService sessionService = mock(SessionService.class);
        Config config = new Config();
        TranslatorService translatorService = mock(TranslatorService.class);
        NetworkService network = mock(NetworkService.class);
        VoteService voteService = mock(VoteService.class);
        SecurityService securityService = mock(SecurityService.class);
        IngressService ingressService = mock(IngressService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        AdminRequestHandler adminRequestHandler = mock(AdminRequestHandler.class);
        VoteChatInterceptor voteChatInterceptor = new VoteChatInterceptor(sessionService, voteService);
        ConnectPacketHandler connectPacketHandler = new ConnectPacketHandler(
                new ConnectionAccessHandler(ingressService),
                new PlayerConnectionBootstrap()
        );
        ConnectionFilterService connectionFilterService = new ConnectionFilterService();
        ChatMessageHandler chatMessageHandler = new ChatMessageHandler(
                config,
                translatorService,
                network,
                securityService,
                chatFormatService,
                voteChatInterceptor
        );

        NetEventService service = new NetEventService(
                chatMessageHandler,
                adminRequestHandler,
                connectPacketHandler,
                connectionFilterService
        );

        service.setIpAcceptor(ip -> false);

        boolean allowed = service.connectFilter("5.6.7.8");

        org.assertj.core.api.Assertions.assertThat(allowed).isFalse();
        org.assertj.core.api.Assertions.assertThat(service.blockedIPs).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(service.blockedIPsPerMinute).isEqualTo(1);
    }

}
