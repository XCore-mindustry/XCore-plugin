package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.network.RedisDiscordLinkCodeStore;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkCodeCreatedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkStatusChangedV1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordLinkServiceTest {
    private final org.xcore.plugin.concurrent.StorageExecutor executor = org.mockito.Mockito.mock(org.xcore.plugin.concurrent.StorageExecutor.class);
    private final org.xcore.plugin.concurrent.Async async = new org.xcore.plugin.concurrent.Async(executor, Runnable::run);
    {
        org.mockito.Mockito.doAnswer(call -> {
            java.util.concurrent.Callable<?> task = call.getArgument(0);
            return java.util.concurrent.CompletableFuture.completedFuture(task.call());
        }).when(executor).supply(any());
    }


    @Test
    @DisplayName("createCode reserves a code and publishes creation event")
    void createCode_reservesCodeAndPublishesCreationEvent() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        when(codeStore.store(any())).thenReturn(true);

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, async);

        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-7").pid(7).nickname("Target").build();

        var result = service.createCode(session);

        assertThat(result.success()).isTrue();
        assertThat(result.code()).hasSize(6);
        verify(codeStore, never()).invalidatePendingByPlayerUuid("uuid-7");
        verify(codeStore).store(any());
        verify(networkService).post(any(DiscordLinkCodeCreatedV1.class));
    }

    @Test
    @DisplayName("createCode returns error when code persistence fails")
    void createCode_returnsErrorWhenCodePersistenceFails() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        when(codeStore.store(any())).thenReturn(false);

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, async);

        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-7").pid(7).nickname("Target").build();

        var result = service.createCode(session);

        assertThat(result.success()).isFalse();
        assertThat(result.errorKey()).isEqualTo("save-failed");
    }

    @Test
    @DisplayName("createCode returns already-linked when player already has discord account")
    void createCode_returnsAlreadyLinkedWhenPlayerAlreadyHasDiscordAccount() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, async);

        Session session = mock(Session.class);
        session.data = PlayerData.builder()
                .uuid("uuid-7")
                .pid(7)
                .nickname("Target")
                .discordId("123")
                .discordUsername("discord-user")
                .build();

        var result = service.createCode(session);

        assertThat(result.success()).isFalse();
        assertThat(result.errorKey()).isEqualTo("already-linked");
    }

    @Test
    @DisplayName("getOrCreateActiveCode returns existing active code without creating new one")
    void getOrCreateActiveCode_returnsExistingActiveCodeWithoutCreatingNewOne() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, async);

        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-7").pid(7).nickname("Target").build();

        long now = System.currentTimeMillis();
        var pending = new RedisDiscordLinkCodeStore.LinkCodePayload(
                "ABC123", "uuid-7", 7, "Target", "mini-pvp", now, now + 60_000L
        );
        when(codeStore.findPendingByPlayerUuid("uuid-7")).thenReturn(pending);

        var result = service.getOrCreateActiveCode(session);

        assertThat(result.success()).isTrue();
        assertThat(result.code()).isEqualTo("ABC123");
        verify(codeStore, never()).store(any());
        verify(networkService, never()).post(any(DiscordLinkCodeCreatedV1.class));
    }

    @Test
    @DisplayName("getOrCreateActiveCode replaces expired code through atomic reservation")
    void getOrCreateActiveCode_replacesExpiredCodeAtomically() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        when(codeStore.store(any())).thenReturn(true);

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, async);

        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-7").pid(7).nickname("Target").build();

        long now = System.currentTimeMillis();
        var expired = new RedisDiscordLinkCodeStore.LinkCodePayload(
                "OLD123", "uuid-7", 7, "Target", "mini-pvp", now - 120_000L, now - 1L
        );
        when(codeStore.findPendingByPlayerUuid("uuid-7")).thenReturn(expired);

        var result = service.getOrCreateActiveCode(session);

        assertThat(result.success()).isTrue();
        assertThat(result.code()).hasSize(6);
        verify(codeStore, never()).invalidatePendingByPlayerUuid("uuid-7");
        verify(codeStore).store(any());
    }

    @Test
    @DisplayName("confirmLink allows same discord account to be reused across players")
    void confirmLink_allowsSameDiscordAccountAcrossPlayers() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, async);

        var code = new RedisDiscordLinkCodeStore.LinkCodePayload(
                "ABC123", "uuid-7", 7, "Target", "mini-pvp", System.currentTimeMillis(), System.currentTimeMillis() + 60_000L
        );

        PlayerData playerData = PlayerData.builder()
                .uuid("uuid-7")
                .pid(7)
                .nickname("Target")
                .discordId("")
                .build();

        when(codeStore.findByCode("ABC123")).thenReturn(code);
        when(playerDataRepository.findByUuid("uuid-7")).thenReturn(playerData);
        when(playerDataRepository.updateDiscordLink(eq("uuid-7"), eq(7), eq("123"), eq("discord-user"), anyLong())).thenAnswer(call -> {
            playerData.discordId = call.getArgument(2);
            playerData.discordUsername = call.getArgument(3);
            playerData.discordLinkedAt = call.getArgument(4);
            return playerData;
        });
        when(codeStore.claimCode(code)).thenReturn(code);

        var result = service.confirmLink("ABC123", "uuid-7", 7, "123", "discord-user");

        assertThat(result.success()).isTrue();
        assertThat(playerData.discordId).isEqualTo("123");
        assertThat(playerData.discordUsername).isEqualTo("discord-user");
        verify(networkService).post(any(DiscordLinkStatusChangedV1.class));
    }

    @Test
    @DisplayName("confirmLink rejects another discord account for already linked player")
    void confirmLink_rejectsOtherDiscordAccountForAlreadyLinkedPlayer() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, async);

        var code = new RedisDiscordLinkCodeStore.LinkCodePayload(
                "ABC123", "uuid-7", 7, "Target", "mini-pvp", System.currentTimeMillis(), System.currentTimeMillis() + 60_000L
        );

        PlayerData playerData = PlayerData.builder()
                .uuid("uuid-7")
                .pid(7)
                .nickname("Target")
                .discordId("other-discord")
                .build();

        when(codeStore.findByCode("ABC123")).thenReturn(code);
        when(playerDataRepository.findByUuid("uuid-7")).thenReturn(playerData);

        var result = service.confirmLink("ABC123", "uuid-7", 7, "123", "discord-user");

        assertThat(result.success()).isFalse();
        assertThat(result.errorKey()).isEqualTo("already-linked-other-discord");
    }

    @Test
    @DisplayName("unlink by uuid updates offline player data")
    void unlinkByUuid_updatesOfflinePlayerData() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, async);

        PlayerData playerData = PlayerData.builder()
                .uuid("uuid-7")
                .pid(7)
                .nickname("Target")
                .discordId("123")
                .discordUsername("discord-user")
                .discordLinkedAt(50L)
                .build();

        when(playerDataRepository.findByUuid("uuid-7")).thenReturn(playerData);
        when(playerDataRepository.clearDiscordLink("uuid-7", "123", 50L)).thenReturn(true);

        var result = service.unlink("uuid-7", "123", 50L);

        assertThat(result).isTrue();
        verify(networkService).post(any(DiscordLinkStatusChangedV1.class));
    }

    @Test
    @DisplayName("unlink by uuid clears online session discord state")
    void unlinkByUuid_clearsOnlineSessionDiscordState() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, async);

        PlayerData playerData = PlayerData.builder()
                .uuid("uuid-7")
                .pid(7)
                .nickname("Target")
                .discordId("123")
                .discordUsername("discord-user")
                .discordLinkedAt(50L)
                .build();

        Session session = mock(Session.class);
        session.data = PlayerData.builder()
                .uuid("uuid-7")
                .pid(7)
                .nickname("Target")
                .discordId("123")
                .discordUsername("discord-user")
                .discordLinkedAt(50L)
                .build();

        when(playerDataRepository.findByUuid("uuid-7")).thenReturn(playerData, PlayerData.builder().uuid("uuid-7").build());
        when(playerDataRepository.clearDiscordLink("uuid-7", "123", 50L)).thenReturn(true);
        when(sessionService.get("uuid-7")).thenReturn(session);

        var result = service.unlink("uuid-7", "123", 50L);

        assertThat(result).isTrue();
        assertThat(session.data.discordId).isBlank();
        assertThat(session.data.discordUsername).isBlank();
        assertThat(session.data.discordLinkedAt).isZero();
        verify(networkService).post(any(DiscordLinkStatusChangedV1.class));
    }

    @Test
    @DisplayName("link code result helper methods expose error and remaining minutes")
    void linkCodeResult_helpersExposeErrorAndRemainingMinutes() {
        var result = new DiscordLinkService.LinkCodeResult(false, "", 90_000L, "already-linked");

        assertThat(result.isError("already-linked")).isTrue();
        assertThat(result.isError("save-failed")).isFalse();
        assertThat(result.remainingMinutes(0L)).isEqualTo(2L);
    }

    @Test
    @DisplayName("status display name falls back to discord id when username missing")
    void linkStatusResult_displayNameFallsBackToDiscordId() {
        var status = DiscordLinkService.LinkStatusResult.linked("123", "", 50L);

        assertThat(status.displayName()).isEqualTo("123");
    }
    @Test
    void readOnlyDoesNotReadOrConsumeProof() {
        var codes = mock(RedisDiscordLinkCodeStore.class);
        var players = mock(PlayerDataRepository.class);
        when(players.isReadOnly()).thenReturn(true);
        var service = new DiscordLinkService(codes, players, mock(SessionService.class), mock(NetworkService.class), new TomlXcoreConfig(), async);
        assertThat(service.confirmLink("ABC123", "one", 7, "123", "Discord").errorKey()).isEqualTo("read-only");
        org.mockito.Mockito.verifyNoInteractions(codes);
    }

    @Test
    void cacheRefreshPatchesOnlyBindingOnMainThread() {
        var queue = new org.xcore.testkit.core.DeterministicQueue();
        var mainAsync = new org.xcore.plugin.concurrent.Async(executor, queue::post);
        var players = mock(PlayerDataRepository.class);
        var sessions = mock(SessionService.class);
        var session = mock(Session.class);
        session.data = PlayerData.builder().uuid("one").pid(7).totalPlayTime(999).nickname("Unsaved name").build();
        when(sessions.get("one")).thenReturn(session);
        when(players.findByUuid("one")).thenReturn(PlayerData.builder().uuid("one").pid(7).totalPlayTime(10)
                .discordId("123").discordUsername("Discord").discordLinkedAt(50L).build());
        var service = new DiscordLinkService(mock(RedisDiscordLinkCodeStore.class), players, sessions,
                mock(NetworkService.class), new TomlXcoreConfig(), mainAsync);
        service.refreshCachedBinding("one");
        assertThat(session.data.discordId).isEmpty();
        queue.runTurn();
        assertThat(session.data.discordId).isEqualTo("123");
        assertThat(session.data.totalPlayTime).isEqualTo(999);
        assertThat(session.data.nickname).isEqualTo("Unsaved name");
    }

    @Test
    void foreignOrStaleUnlinkDoesNotIssueWrite() {
        var players = mock(PlayerDataRepository.class);
        when(players.findByUuid("one")).thenReturn(PlayerData.builder().uuid("one").discordId("123").discordLinkedAt(50L).build());
        var service = new DiscordLinkService(mock(RedisDiscordLinkCodeStore.class), players, mock(SessionService.class),
                mock(NetworkService.class), new TomlXcoreConfig(), async);
        assertThat(service.unlink("one", "foreign", 50L)).isFalse();
        assertThat(service.unlink("one", "123", 49L)).isFalse();
        verify(players, never()).clearDiscordLink(any(), any(), anyLong());
    }

    @Test
    void websiteLinkIsDisabledByDefaultAndEncodesServer() {
        var config = new TomlXcoreConfig();
        var service = new DiscordLinkService(mock(RedisDiscordLinkCodeStore.class), mock(PlayerDataRepository.class),
                mock(SessionService.class), mock(NetworkService.class), config, async);
        assertThat(service.websiteLinkUrl("ABC123")).isEmpty();
        config.discord.websiteLinkUrl = "https://accounts.example/link";
        config.server.name = "mini pvp";
        assertThat(service.websiteLinkUrl("ABC123")).isEqualTo("https://accounts.example/link?code=ABC123&server=mini+pvp");
    }

}
