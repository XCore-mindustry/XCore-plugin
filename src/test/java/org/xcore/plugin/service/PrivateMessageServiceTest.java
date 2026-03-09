package org.xcore.plugin.service;

import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PrivateMessageRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PrivateMessage;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PrivateMessageServiceTest {

    private BeanScope scope;
    private PrivateMessageService privateMessageService;
    private PrivateMessageRepository privateMessageRepository;
    private SessionService sessionService;
    private SecurityService securityService;
    private NetworkService networkService;

    @BeforeEach
    void setUp() {
        scope = BeanScope.builder()
                .modules(new PrivateMessageModule())
                .forTesting()
                .mock(PrivateMessageRepository.class)
                .mock(SessionService.class)
                .mock(SecurityService.class)
                .mock(NetworkService.class)
                .build();

        privateMessageService = scope.get(PrivateMessageService.class);
        privateMessageRepository = scope.get(PrivateMessageRepository.class);
        sessionService = scope.get(SessionService.class);
        securityService = scope.get(SecurityService.class);
        networkService = scope.get(NetworkService.class);
    }

    @AfterEach
    void tearDown() {
        scope.close();
    }

    @Test
    @DisplayName("send saves and updates reply state for valid pid")
    void send_savesAndUpdatesReplyState_forValidPid() {
        Session sender = mockSession("sender-uuid", 10, "Sender");
        PlayerData target = PlayerData.builder().uuid("target-uuid").pid(42).nickname("Target").blockedPrivateUuids(new HashSet<>()).build();

        when(securityService.isMuted(sender.player)).thenReturn(false);
        when(sessionService.getOrLoadFromDb(42)).thenReturn(target);
        when(privateMessageRepository.countUnread("target-uuid")).thenReturn(0L);
        when(privateMessageRepository.save(any(PrivateMessage.class))).thenReturn(true);
        when(sessionService.get("target-uuid")).thenReturn(null);

        boolean result = privateMessageService.send(sender, 42, "hello there");

        assertThat(result).isTrue();
        assertThat(sender.lastPrivateTargetPid).isEqualTo(42);
        assertThat(sender.lastPrivateMessageAt).isGreaterThan(0L);
        verify(privateMessageRepository).save(any(PrivateMessage.class));
        verify(sender.locale()).send(eq("private-message-sent"), anyMap());
        verify(networkService).post(any(SocketEvents.PrivateMessageEvent.class));
    }

    @Test
    @DisplayName("send rejects self messages")
    void send_rejectsSelfMessages() {
        Session sender = mockSession("same-uuid", 5, "Sender");
        PlayerData target = PlayerData.builder().uuid("same-uuid").pid(5).nickname("Sender").blockedPrivateUuids(new HashSet<>()).build();

        when(securityService.isMuted(sender.player)).thenReturn(false);
        when(sessionService.getOrLoadFromDb(5)).thenReturn(target);

        boolean result = privateMessageService.send(sender, 5, "hello");

        assertThat(result).isFalse();
        verify(sender.locale()).send(eq("error-private-message-self"), anyMap());
        verify(privateMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("send rejects when recipient blocked sender")
    void send_rejectsWhenRecipientBlockedSender() {
        Session sender = mockSession("sender-uuid", 1, "Sender");
        var blocked = new HashSet<String>();
        blocked.add("sender-uuid");
        PlayerData target = PlayerData.builder().uuid("target-uuid").pid(6).nickname("Target").blockedPrivateUuids(blocked).build();

        when(securityService.isMuted(sender.player)).thenReturn(false);
        when(sessionService.getOrLoadFromDb(6)).thenReturn(target);

        boolean result = privateMessageService.send(sender, 6, "hello");

        assertThat(result).isFalse();
        verify(sender.locale()).send(eq("error-private-message-target-unavailable"), anyMap());
        verify(privateMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("send returns false when persistence fails")
    void send_returnsFalse_whenPersistenceFails() {
        Session sender = mockSession("sender-uuid", 10, "Sender");
        PlayerData target = PlayerData.builder().uuid("target-uuid").pid(42).nickname("Target").blockedPrivateUuids(new HashSet<>()).build();

        when(securityService.isMuted(sender.player)).thenReturn(false);
        when(sessionService.getOrLoadFromDb(42)).thenReturn(target);
        when(privateMessageRepository.countUnread("target-uuid")).thenReturn(0L);
        when(privateMessageRepository.save(any(PrivateMessage.class))).thenReturn(false);

        boolean result = privateMessageService.send(sender, 42, "hello there");

        assertThat(result).isFalse();
        verify(sender.locale()).send(eq("error-processing-request"), anyMap());
        verify(sender.locale(), never()).send(eq("private-message-sent"), anyMap());
        verify(networkService, never()).post(any());
    }

    @Test
    @DisplayName("send does not publish cross-server event when recipient is local")
    void send_doesNotPublishCrossServerEvent_whenRecipientIsLocal() {
        Session sender = mockSession("sender-uuid", 10, "Sender");
        Session recipient = mockSession("target-uuid", 42, "Target");
        PlayerData target = PlayerData.builder().uuid("target-uuid").pid(42).nickname("Target").blockedPrivateUuids(new HashSet<>()).build();

        when(securityService.isMuted(sender.player)).thenReturn(false);
        when(sessionService.getOrLoadFromDb(42)).thenReturn(target);
        when(privateMessageRepository.countUnread("target-uuid")).thenReturn(0L);
        when(privateMessageRepository.save(any(PrivateMessage.class))).thenReturn(true);
        when(sessionService.get("target-uuid")).thenReturn(recipient);

        boolean result = privateMessageService.send(sender, 42, "hello there");

        assertThat(result).isTrue();
        verify(networkService, never()).post(any());
    }

    @Test
    @DisplayName("reply falls back to repository latest correspondent")
    void reply_fallsBackToRepositoryLatestCorrespondent() {
        Session sender = mockSession("sender-uuid", 10, "Sender");
        PrivateMessage latest = PrivateMessage.builder()
                .id(new ObjectId())
                .fromUuid("other-uuid")
                .fromPid(88)
                .fromName("Other")
                .toUuid("sender-uuid")
                .toPid(10)
                .message("hi")
                .build();
        PlayerData target = PlayerData.builder().uuid("other-uuid").pid(88).nickname("Other").blockedPrivateUuids(new HashSet<>()).build();

        when(privateMessageRepository.findLatestConversationMessage("sender-uuid")).thenReturn(latest);
        when(sessionService.getOrLoadFromDb("other-uuid")).thenReturn(target);
        when(securityService.isMuted(sender.player)).thenReturn(false);
        when(sessionService.getOrLoadFromDb(88)).thenReturn(target);
        when(privateMessageRepository.countUnread("other-uuid")).thenReturn(0L);
        when(privateMessageRepository.save(any(PrivateMessage.class))).thenReturn(true);
        when(sessionService.get("other-uuid")).thenReturn(null);

        boolean result = privateMessageService.reply(sender, "reply text");

        assertThat(result).isTrue();
        assertThat(sender.lastPrivateTargetPid).isEqualTo(88);
        verify(privateMessageRepository).save(any(PrivateMessage.class));
    }

    @Test
    @DisplayName("block adds target uuid and unblock removes it")
    void block_addsTargetUuid_andUnblockRemovesIt() {
        Session sender = mockSession("sender-uuid", 10, "Sender");
        PlayerData target = PlayerData.builder().uuid("target-uuid").pid(7).nickname("Target").build();

        when(sessionService.getOrLoadFromDb(7)).thenReturn(target);
        doAnswer(invocation -> {
            Session session = invocation.getArgument(0);
            String blockedUuid = invocation.getArgument(1);
            session.data.blockedPrivateUuids.add(blockedUuid);
            return true;
        }).when(sessionService).addBlockedPrivateUuid(same(sender), eq("target-uuid"));
        doAnswer(invocation -> {
            Session session = invocation.getArgument(0);
            String blockedUuid = invocation.getArgument(1);
            session.data.blockedPrivateUuids.remove(blockedUuid);
            return true;
        }).when(sessionService).removeBlockedPrivateUuid(same(sender), eq("target-uuid"));

        boolean blocked = privateMessageService.block(sender, 7);
        boolean unblocked = privateMessageService.unblock(sender, 7);

        assertThat(blocked).isTrue();
        assertThat(unblocked).isTrue();
        assertThat(sender.data.blockedPrivateUuids).doesNotContain("target-uuid");
        verify(sender.locale()).send(eq("private-message-block-success"), anyMap());
        verify(sender.locale()).send(eq("private-message-unblock-success"), anyMap());
    }

    @Test
    @DisplayName("softDelete marks message as recipient deleted")
    void softDelete_marksMessageAsRecipientDeleted() {
        ObjectId messageId = new ObjectId();
        PrivateMessage message = PrivateMessage.builder()
                .id(messageId)
                .toUuid("target-uuid")
                .recipientDeleted(false)
                .build();

        when(privateMessageRepository.findById(messageId)).thenReturn(message);

        boolean result = privateMessageService.softDelete(messageId, "target-uuid");

        assertThat(result).isTrue();
        assertThat(message.recipientDeleted).isTrue();
        verify(privateMessageRepository).save(message);
    }

    @Test
    @DisplayName("listBlocked resolves uuids through SessionService")
    void listBlocked_resolvesUuidsThroughSessionService() {
        Session sender = mockSession("sender-uuid", 10, "Sender");
        sender.data.blockedPrivateUuids.add("uuid-b");
        sender.data.blockedPrivateUuids.add("uuid-a");

        when(sessionService.getOrLoadFromDb("uuid-a")).thenReturn(PlayerData.builder().uuid("uuid-a").pid(1).nickname("A").build());
        when(sessionService.getOrLoadFromDb("uuid-b")).thenReturn(PlayerData.builder().uuid("uuid-b").pid(2).nickname("B").build());

        List<PlayerData> blocked = privateMessageService.listBlocked(sender);

        assertThat(blocked).extracting(data -> data.pid).containsExactly(1, 2);
    }

    @Test
    @DisplayName("parseMenuPid accepts plain ids and hash ids")
    void parseMenuPid_acceptsPlainIdsAndHashIds() {
        assertThat(privateMessageService.parseMenuPid("15")).isEqualTo(15);
        assertThat(privateMessageService.parseMenuPid("#16")).isEqualTo(16);
        assertThat(privateMessageService.parseMenuPid("bad")).isNull();
    }

    private static Session mockSession(String uuid, int pid, String nickname) {
        Session session = mock(Session.class);
        session.data = PlayerData.builder()
                .uuid(uuid)
                .pid(pid)
                .nickname(nickname)
                .blockedPrivateUuids(new HashSet<>())
                .build();
        session.player = mock(mindustry.gen.Player.class);
        when(session.player.uuid()).thenReturn(uuid);
        when(session.player.coloredName()).thenReturn(nickname);
        when(session.player.plainName()).thenReturn(nickname);
        Localization localization = mock(Localization.class);
        when(session.locale()).thenReturn(localization);
        return session;
    }

    private static final class PrivateMessageModule implements AvajeModule {
        @Override
        public Class<?>[] classes() {
            return new Class<?>[]{PrivateMessageService.class, GlobalConfig.class};
        }

        @Override
        public void build(Builder builder) {
            if (builder.isBeanAbsent(GlobalConfig.class)) {
                builder.register(new GlobalConfig());
            }
            if (builder.isBeanAbsent(Config.class)) {
                builder.register(new Config());
            }
            if (builder.isBeanAbsent(PrivateMessageService.class)) {
                builder.register(new PrivateMessageService(
                        builder.get(PrivateMessageRepository.class),
                        builder.get(SessionService.class),
                        builder.get(SecurityService.class),
                        builder.get(NetworkService.class),
                        builder.get(Config.class),
                        builder.get(GlobalConfig.class)
                ));
            }
        }
    }
}
