package org.xcore.plugin.event;

import mindustry.gen.Player;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatMessageV1;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.event.net.admin.AdminRequestHandler;
import org.xcore.plugin.event.net.chat.ChatMessageHandler;
import org.xcore.plugin.event.net.chat.VoteChatInterceptor;
import org.xcore.plugin.service.ChatFormatService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.service.TranslatorService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetEventServiceTest {

    private SessionService sessionService;
    private TomlXcoreConfig config;
    private TranslatorService translatorService;
    private NetworkService network;
    private VoteService voteService;
    private SecurityService securityService;
    private ChatFormatService chatFormatService;
    private AdminRequestHandler adminRequestHandler;
    private NetEventService service;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        config = new TomlXcoreConfig();
        translatorService = mock(TranslatorService.class);
        network = mock(NetworkService.class);
        voteService = mock(VoteService.class);
        securityService = mock(SecurityService.class);
        chatFormatService = mock(ChatFormatService.class);
        adminRequestHandler = mock(AdminRequestHandler.class);
        VoteChatInterceptor voteChatInterceptor = new VoteChatInterceptor(sessionService, voteService);

        ChatMessageHandler chatMessageHandler = new ChatMessageHandler(
                config,
                translatorService,
                network,
                securityService,
                chatFormatService,
                voteChatInterceptor
        );

        service = new NetEventService(
                chatMessageHandler,
                adminRequestHandler
        );
    }

    @Test
    @DisplayName("chat muted player does not translate or publish message")
    void chatMutedPlayer_doesNotTranslateOrPublish() {
        Player author = mock(Player.class);

        when(voteService.isVoting()).thenReturn(false);
        when(securityService.checkAndNotifyMuted(author)).thenReturn(true);

        service.chat(author, "hello");

        verify(chatFormatService, never()).formatChat(any(), any());
        verify(translatorService, never()).translate(any(), any());
        verify(network, never()).post(any());
    }

    @Test
    @DisplayName("chat happy path formats translates and publishes message")
    void chatHappyPath_formatsTranslatesAndPublishes() {
        config.server.name = "main";
        Player author = mock(Player.class);
        when(author.plainName()).thenReturn("Tester");
        when(chatFormatService.formatChat(author, "he`llo")).thenReturn("formatted");
        when(voteService.isVoting()).thenReturn(false);
        when(securityService.checkAndNotifyMuted(author)).thenReturn(false);

        service.chat(author, "he`llo");

        verify(chatFormatService).formatChat(author, "he`llo");
        verify(author).sendMessage("formatted", author, "he`llo");
        verify(translatorService).translate(author, "he`llo");
        verify(network).post(new ChatMessageV1("Tester", "he*llo", "main"));
    }

    @Test
    @DisplayName("adminRequest delegates to adminRequestHandler")
    void adminRequest_delegatesToHandler() {
        NetConnection con = mock(NetConnection.class);
        AdminRequestCallPacket packet = new AdminRequestCallPacket();

        service.adminRequest(con, packet);

        verify(adminRequestHandler).handle(con, packet);
    }
}