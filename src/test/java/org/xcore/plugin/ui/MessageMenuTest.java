package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PrivateMessage;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.menu.MessageMenu;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageMenuTest {

    private SessionService sessionService;
    private PrivateMessageService privateMessageService;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private MessageMenu messageMenu;
    private Session session;
    private PrivateMessage message;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionService = mock(SessionService.class);
        privateMessageService = mock(PrivateMessageService.class);
        gateway = mock(MindustryMenuGateway.class);
        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();
        secretsConfig.messages.privateMessages.maxLength = 300;
        secretsConfig.pagination.privateMessagesPerPage = 10;
        messageMenu = new MessageMenu(secretsConfig, sessionService, privateMessageService, menuService);
        messageMenu.init();

        session = session();
        when(sessionService.get("viewer-1")).thenReturn(session);

        message = new PrivateMessage();
        message.id = new ObjectId();
        message.fromUuid = "author-1";
        message.fromPid = 42;
        message.fromName = "Author";
        message.toUuid = session.data.uuid;
        message.message = "hello";
        when(privateMessageService.getMessage(message.id, session.data.uuid)).thenReturn(message);
        when(privateMessageService.inbox(session.data.uuid, 1)).thenReturn(List.of());
    }

    @Test
    @DisplayName("inbox renders through route-backed flow runtime")
    void inbox_rendersThroughRouteBackedFlowRuntime() {
        when(privateMessageService.countInbox(session.data.uuid)).thenReturn(0L);
        when(privateMessageService.countUnread(session.data.uuid)).thenReturn(0L);

        messageMenu.inbox("viewer-1", 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        assertThat(session.activeScreen().hasRoute()).isTrue();
    }

    @Test
    @DisplayName("inbox blocked action uses route history and opens blocked route")
    void inbox_blockedActionUsesRouteHistoryAndOpensBlockedRoute() {
        when(privateMessageService.countInbox(session.data.uuid)).thenReturn(0L);
        when(privateMessageService.countUnread(session.data.uuid)).thenReturn(0L);
        when(privateMessageService.listBlocked(session)).thenReturn(List.of());

        messageMenu.inbox("viewer-1", 1);
        menuService.onMenuOption(session, 1);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
    }

    @Test
    @DisplayName("inbox message selection uses route history and opens details")
    void inbox_messageSelectionUsesRouteHistoryAndOpensDetails() {
        when(privateMessageService.countInbox(session.data.uuid)).thenReturn(1L);
        when(privateMessageService.countUnread(session.data.uuid)).thenReturn(1L);
        when(privateMessageService.inbox(session.data.uuid, 1)).thenReturn(List.of(message));

        messageMenu.inbox("viewer-1", 1);
        menuService.onMenuOption(session, 0);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
    }

    @Test
    @DisplayName("details not found renders routed fallback screen when opened from routed inbox")
    void details_notFoundRendersRoutedFallbackScreenWhenOpenedFromRoutedInbox() {
        ObjectId missingId = new ObjectId();
        when(privateMessageService.countInbox(session.data.uuid)).thenReturn(1L);
        when(privateMessageService.countUnread(session.data.uuid)).thenReturn(1L);
        when(privateMessageService.inbox(session.data.uuid, 1)).thenReturn(List.of(message));
        when(privateMessageService.getMessage(missingId, session.data.uuid)).thenReturn(null);

        message.id = missingId;
        messageMenu.inbox("viewer-1", 1);
        menuService.onMenuOption(session, 0);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.hasRouteHistory()).isTrue();
    }

    @Test
    @DisplayName("reply opens active prompt through menu service")
    void reply_opensActivePromptThroughMenuService() {
        messageMenu.details("viewer-1", message.id, 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        menuService.onMenuOption(session, 0);

        assertThat(session.activePrompt()).isNotNull();
        assertThat(session.textHandler).isNull();
        verify(gateway).textInput(eq(session.player), eq(0), eq("private-message-reply-title"), eq("private-message-reply-message"), eq(300), eq(""), eq(false));
    }

    @Test
    @DisplayName("details delete returns through route history when opened from routed inbox")
    void details_deleteReturnsThroughRouteHistoryWhenOpenedFromRoutedInbox() {
        when(privateMessageService.countInbox(session.data.uuid)).thenReturn(1L);
        when(privateMessageService.countUnread(session.data.uuid)).thenReturn(1L);
        when(privateMessageService.inbox(session.data.uuid, 1)).thenReturn(List.of(message));

        messageMenu.inbox("viewer-1", 1);
        menuService.onMenuOption(session, 0);
        menuService.onMenuOption(session, 1);

        verify(privateMessageService).softDelete(message.id, session.data.uuid);
        assertThat(session.hasHistory()).isFalse();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
    }

    @Test
    @DisplayName("reply submit sends message and returns to details")
    void replySubmit_sendsMessageAndReturnsToDetails() {
        messageMenu.details("viewer-1", message.id, 1);
        menuService.onMenuOption(session, 0);

        menuService.onTextInput(session, "reply text");

        verify(privateMessageService).send(session, 42, "reply text");
        assertThat(session.activePrompt()).isNull();
        assertThat(session.activeScreen()).isNotNull();
    }

    @Test
    @DisplayName("reply cancel returns to details without sending")
    void replyCancel_returnsToDetailsWithoutSending() {
        messageMenu.details("viewer-1", message.id, 1);
        menuService.onMenuOption(session, 0);

        menuService.onTextInput(session, null);

        verify(privateMessageService, never()).send(any(), anyInt(), anyString());
        assertThat(session.activePrompt()).isNull();
        assertThat(session.activeScreen()).isNotNull();
    }

    @Test
    @DisplayName("compose opens target prompt then body prompt")
    void compose_opensTargetPromptThenBodyPrompt() {
        when(privateMessageService.countInbox(session.data.uuid)).thenReturn(0L);
        when(privateMessageService.countUnread(session.data.uuid)).thenReturn(0L);
        messageMenu.inbox("viewer-1", 1);
        menuService.onMenuOption(session, 0);

        verify(gateway).textInput(eq(session.player), eq(0), eq("private-message-compose-target-title"), eq("private-message-compose-target-message"), eq(32), eq(""), eq(false));
        when(privateMessageService.parseMenuPid("#55")).thenReturn(55);

        menuService.onTextInput(session, "#55");

        assertThat(session.activePrompt()).isNotNull();
        verify(gateway).textInput(eq(session.player), eq(0), eq("private-message-compose-body-title"), eq("private-message-compose-body-message"), eq(300), eq(""), eq(false));
        assertThat(session.textHandler).isNull();
    }

    @Test
    @DisplayName("compose body submit sends message and returns")
    void composeBodySubmit_sendsMessageAndReturns() {
        when(privateMessageService.countInbox(session.data.uuid)).thenReturn(0L);
        when(privateMessageService.countUnread(session.data.uuid)).thenReturn(0L);
        messageMenu.inbox("viewer-1", 1);
        menuService.onMenuOption(session, 0);
        when(privateMessageService.parseMenuPid("#55")).thenReturn(55);
        menuService.onTextInput(session, "#55");

        menuService.onTextInput(session, "hello target");

        verify(privateMessageService).send(session, 55, "hello target");
        assertThat(session.activePrompt()).isNull();
        assertThat(session.activeScreen()).isNotNull();
    }

    @Test
    @DisplayName("compose target cancel returns without opening stale handler")
    void composeTargetCancel_returnsWithoutOpeningStaleHandler() {
        when(privateMessageService.countInbox(session.data.uuid)).thenReturn(0L);
        when(privateMessageService.countUnread(session.data.uuid)).thenReturn(0L);
        messageMenu.inbox("viewer-1", 1);
        menuService.onMenuOption(session, 0);

        menuService.onTextInput(session, null);

        verify(privateMessageService, never()).parseMenuPid(anyString());
        verify(privateMessageService, never()).send(any(), anyInt(), anyString());
        assertThat(session.activePrompt()).isNull();
        assertThat(session.textHandler).isNull();
        assertThat(session.activeScreen()).isNotNull();
    }

    @Test
    @DisplayName("compose invalid target returns without leaving stale prompt")
    void composeInvalidTarget_returnsWithoutLeavingStalePrompt() {
        when(privateMessageService.countInbox(session.data.uuid)).thenReturn(0L);
        when(privateMessageService.countUnread(session.data.uuid)).thenReturn(0L);
        messageMenu.inbox("viewer-1", 1);
        menuService.onMenuOption(session, 0);
        when(privateMessageService.parseMenuPid("bad")).thenReturn(null);

        menuService.onTextInput(session, "bad");

        verify(privateMessageService, never()).send(any(), anyInt(), anyString());
        assertThat(session.activePrompt()).isNull();
        assertThat(session.textHandler).isNull();
        assertThat(session.activeScreen()).isNotNull();
        verify(session.localization).send(eq("error-private-message-invalid-pid"), anyMap());
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("viewer-1", true);
        data.uuid = "viewer-1";
        data.pid = 7;
        Session session = new Session(
                new TomlSecretsConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;
        return session;
    }
}
