package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.DiscordLinkCodeRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.DiscordLinkCode;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordLinkServiceTest {

    @Test
    @DisplayName("createCode invalidates old codes and publishes creation event")
    void createCode_invalidatesOldCodesAndPublishesCreationEvent() {
        DiscordLinkCodeRepository codeRepository = mock(DiscordLinkCodeRepository.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        when(codeRepository.save(any(DiscordLinkCode.class))).thenReturn(true);

        DiscordLinkService service = new DiscordLinkService(codeRepository, playerDataRepository, sessionService, networkService, config);

        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-7").pid(7).nickname("Target").build();

        var result = service.createCode(session);

        assertThat(result.success()).isTrue();
        assertThat(result.code()).hasSize(6);
        verify(codeRepository).invalidatePendingByPlayerUuid("uuid-7");
        verify(codeRepository).save(any(DiscordLinkCode.class));
        verify(networkService).post(any(SocketEvents.DiscordLinkCodeCreatedEvent.class));
    }

    @Test
    @DisplayName("createCode returns error when code persistence fails")
    void createCode_returnsErrorWhenCodePersistenceFails() {
        DiscordLinkCodeRepository codeRepository = mock(DiscordLinkCodeRepository.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        when(codeRepository.save(any(DiscordLinkCode.class))).thenReturn(false);

        DiscordLinkService service = new DiscordLinkService(codeRepository, playerDataRepository, sessionService, networkService, config);

        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-7").pid(7).nickname("Target").build();

        var result = service.createCode(session);

        assertThat(result.success()).isFalse();
        assertThat(result.errorKey()).isEqualTo("save-failed");
    }

    @Test
    @DisplayName("createCode returns already-linked when player already has discord account")
    void createCode_returnsAlreadyLinkedWhenPlayerAlreadyHasDiscordAccount() {
        DiscordLinkCodeRepository codeRepository = mock(DiscordLinkCodeRepository.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeRepository, playerDataRepository, sessionService, networkService, config);

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
    @DisplayName("confirmLink allows same discord account to be reused across players")
    void confirmLink_allowsSameDiscordAccountAcrossPlayers() {
        DiscordLinkCodeRepository codeRepository = mock(DiscordLinkCodeRepository.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeRepository, playerDataRepository, sessionService, networkService, config);

        DiscordLinkCode code = DiscordLinkCode.builder()
                .code("ABC123")
                .playerUuid("uuid-7")
                .playerPid(7)
                .playerNickname("Target")
                .server("mini-pvp")
                .expiresAt(System.currentTimeMillis() + 60_000L)
                .status("pending")
                .build();

        PlayerData playerData = PlayerData.builder()
                .uuid("uuid-7")
                .pid(7)
                .nickname("Target")
                .discordId("")
                .build();

        when(codeRepository.findByCode("ABC123")).thenReturn(code);
        when(playerDataRepository.findByUuid("uuid-7")).thenReturn(playerData);
        when(playerDataRepository.updateDiscordLink(eq("uuid-7"), eq("123"), eq("discord-user"), anyLong())).thenReturn(true);
        when(codeRepository.consumeCode(eq("ABC123"), eq("123"), anyLong())).thenReturn(true);

        var result = service.confirmLink("ABC123", "uuid-7", 7, "123", "discord-user");

        assertThat(result.success()).isTrue();
        assertThat(playerData.discordId).isEqualTo("123");
        assertThat(playerData.discordUsername).isEqualTo("discord-user");
        verify(networkService).post(any(SocketEvents.DiscordLinkStatusChangedEvent.class));
    }

    @Test
    @DisplayName("confirmLink rejects another discord account for already linked player")
    void confirmLink_rejectsOtherDiscordAccountForAlreadyLinkedPlayer() {
        DiscordLinkCodeRepository codeRepository = mock(DiscordLinkCodeRepository.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeRepository, playerDataRepository, sessionService, networkService, config);

        DiscordLinkCode code = DiscordLinkCode.builder()
                .code("ABC123")
                .playerUuid("uuid-7")
                .playerPid(7)
                .playerNickname("Target")
                .server("mini-pvp")
                .expiresAt(System.currentTimeMillis() + 60_000L)
                .status("pending")
                .build();

        PlayerData playerData = PlayerData.builder()
                .uuid("uuid-7")
                .pid(7)
                .nickname("Target")
                .discordId("other-discord")
                .build();

        when(codeRepository.findByCode("ABC123")).thenReturn(code);
        when(playerDataRepository.findByUuid("uuid-7")).thenReturn(playerData);

        var result = service.confirmLink("ABC123", "uuid-7", 7, "123", "discord-user");

        assertThat(result.success()).isFalse();
        assertThat(result.errorKey()).isEqualTo("already-linked-other-discord");
    }

    @Test
    @DisplayName("unlink by uuid updates offline player data")
    void unlinkByUuid_updatesOfflinePlayerData() {
        DiscordLinkCodeRepository codeRepository = mock(DiscordLinkCodeRepository.class);
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        DiscordLinkService service = new DiscordLinkService(codeRepository, playerDataRepository, sessionService, networkService, config);

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

        var result = service.unlink("uuid-7");

        assertThat(result).isTrue();
        verify(networkService).post(any(SocketEvents.DiscordLinkStatusChangedEvent.class));
    }
}
