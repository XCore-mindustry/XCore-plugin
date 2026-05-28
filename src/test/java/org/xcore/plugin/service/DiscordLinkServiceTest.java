package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.network.RedisDiscordLinkCodeStore;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkCodeCreatedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkStatusChangedV1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordLinkServiceTest {

    @Test
    @DisplayName("createCode invalidates old codes and publishes creation event")
    void createCode_invalidatesOldCodesAndPublishesCreationEvent() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        when(codeStore.store(any())).thenReturn(true);

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, discordAdminAccessService);

        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-7").pid(7).nickname("Target").build();

        var result = service.createCode(session);

        assertThat(result.success()).isTrue();
        assertThat(result.code()).hasSize(6);
        verify(codeStore).invalidatePendingByPlayerUuid("uuid-7");
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
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        when(codeStore.store(any())).thenReturn(false);

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, discordAdminAccessService);

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
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, discordAdminAccessService);

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
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, discordAdminAccessService);

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
    @DisplayName("getOrCreateActiveCode clears expired code before creating new one")
    void getOrCreateActiveCode_clearsExpiredCodeBeforeCreatingNewOne() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        when(codeStore.store(any())).thenReturn(true);

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, discordAdminAccessService);

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
        verify(codeStore, atLeastOnce()).invalidatePendingByPlayerUuid("uuid-7");
        verify(codeStore).store(any());
    }

    @Test
    @DisplayName("confirmLink allows same discord account to be reused across players")
    void confirmLink_allowsSameDiscordAccountAcrossPlayers() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, discordAdminAccessService);

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
        when(playerDataRepository.updateDiscordLink(eq("uuid-7"), eq("123"), eq("discord-user"), anyLong())).thenReturn(true);
        when(codeStore.consumeCode(eq("ABC123"))).thenReturn(true);

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
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, discordAdminAccessService);

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
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, discordAdminAccessService);

        PlayerData playerData = PlayerData.builder()
                .uuid("uuid-7")
                .pid(7)
                .nickname("Target")
                .discordId("123")
                .discordUsername("discord-user")
                .discordLinkedAt(50L)
                .build();

        when(playerDataRepository.findByUuid("uuid-7")).thenReturn(playerData);
        when(playerDataRepository.clearDiscordLink("uuid-7")).thenReturn(true);
        when(discordAdminAccessService.revokeDiscordAdminAccess("uuid-7")).thenReturn(true);

        var result = service.unlink("uuid-7");

        assertThat(result).isTrue();
        verify(networkService).post(any(DiscordLinkStatusChangedV1.class));
        verify(networkService).post(any(DiscordAdminAccessChangedCommandV1.class));
        verify(discordAdminAccessService).revokeDiscordAdminAccess("uuid-7");
    }

    @Test
    @DisplayName("unlink by uuid clears online session discord state")
    void unlinkByUuid_clearsOnlineSessionDiscordState() {
        RedisDiscordLinkCodeStore codeStore = mock(RedisDiscordLinkCodeStore.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeStore, playerDataRepository, sessionService, networkService, config, discordAdminAccessService);

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

        when(playerDataRepository.findByUuid("uuid-7")).thenReturn(playerData);
        when(playerDataRepository.clearDiscordLink("uuid-7")).thenReturn(true);
        when(sessionService.get("uuid-7")).thenReturn(session);
        when(discordAdminAccessService.revokeDiscordAdminAccess("uuid-7")).thenReturn(true);

        var result = service.unlink("uuid-7");

        assertThat(result).isTrue();
        assertThat(session.data.discordId).isBlank();
        assertThat(session.data.discordUsername).isBlank();
        assertThat(session.data.discordLinkedAt).isZero();
        verify(networkService).post(any(DiscordAdminAccessChangedCommandV1.class));
        verify(discordAdminAccessService).revokeDiscordAdminAccess("uuid-7");
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
}
