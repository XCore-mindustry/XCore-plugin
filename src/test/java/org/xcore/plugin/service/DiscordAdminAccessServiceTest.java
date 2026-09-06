package org.xcore.plugin.service;

import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordAdminAccessServiceTest {

    private NetServer previousNetServer;

    @BeforeEach
    void setUp() {
        previousNetServer = Vars.netServer;
        NetServer netServer = mock(NetServer.class);
        netServer.admins = mock(Administration.class);
        Vars.netServer = netServer;
    }

    @AfterEach
    void tearDown() {
        Vars.netServer = previousNetServer;
    }

    @Test
    @DisplayName("hasDiscordAdminAccess returns true only for discord-role admin")
    void hasDiscordAdminAccess_returnsTrueOnlyForDiscordRoleAdmin() {
        DiscordAdminAccessService service = new DiscordAdminAccessService(
                mock(PlayerDataRepository.class),
                mock(SessionService.class),
                mock(PlayerDisplayService.class),
                mock(AuthStatusBroadcaster.class)
        );

        assertThat(service.hasDiscordAdminAccess(PlayerData.builder().admin(true).adminSource(DiscordAdminAccessService.SOURCE_DISCORD_ROLE).build())).isTrue();
        assertThat(service.hasDiscordAdminAccess(PlayerData.builder().admin(true).adminSource(DiscordAdminAccessService.SOURCE_NONE).build())).isFalse();
        assertThat(service.hasDiscordAdminAccess(PlayerData.builder().admin(false).adminSource(DiscordAdminAccessService.SOURCE_DISCORD_ROLE).build())).isFalse();
    }

    @Test
    @DisplayName("applyDiscordAdminAccess updates repository and online session")
    void applyDiscordAdminAccess_updatesRepositoryAndOnlineSession() {
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService service = new DiscordAdminAccessService(playerDataRepository, sessionService, playerDisplayService, mock(AuthStatusBroadcaster.class));

        PlayerData stored = PlayerData.builder().uuid("uuid-1").admin(false).adminSource(DiscordAdminAccessService.SOURCE_NONE).build();
        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-1").admin(false).adminSource(DiscordAdminAccessService.SOURCE_NONE).build();

        when(playerDataRepository.findByUuid("uuid-1")).thenReturn(stored);
        when(playerDataRepository.updateAdminStatus("uuid-1", true, DiscordAdminAccessService.SOURCE_DISCORD_ROLE)).thenReturn(true);
        when(sessionService.get("uuid-1")).thenReturn(session);

        boolean result = service.applyDiscordAdminAccess("uuid-1", "123", "discord-user");

        assertThat(result).isTrue();
        assertThat(stored.admin).isTrue();
        assertThat(stored.adminSource).isEqualTo(DiscordAdminAccessService.SOURCE_DISCORD_ROLE);
        assertThat(session.data.admin).isTrue();
        assertThat(session.data.adminSource).isEqualTo(DiscordAdminAccessService.SOURCE_DISCORD_ROLE);
        assertThat(session.data.discordId).isEqualTo("123");
        assertThat(session.data.discordUsername).isEqualTo("discord-user");
        verify(playerDisplayService).refresh(session);
    }

    @Test
    @DisplayName("revokeDiscordAdminAccess clears repository and runtime admin for online player")
    void revokeDiscordAdminAccess_clearsRepositoryAndRuntimeAdminForOnlinePlayer() {
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService service = new DiscordAdminAccessService(playerDataRepository, sessionService, playerDisplayService, mock(AuthStatusBroadcaster.class));

        Player player = Player.create();
        player.admin = true;

        PlayerData stored = PlayerData.builder().uuid("uuid-1").admin(true).adminSource(DiscordAdminAccessService.SOURCE_DISCORD_ROLE).build();
        Session session = mock(Session.class);
        session.player = player;
        session.data = PlayerData.builder().uuid("uuid-1").admin(true).adminSource(DiscordAdminAccessService.SOURCE_DISCORD_ROLE).build();

        when(playerDataRepository.findByUuid("uuid-1")).thenReturn(stored);
        when(playerDataRepository.clearAdminAccess("uuid-1")).thenReturn(true);
        when(sessionService.get("uuid-1")).thenReturn(session);

        boolean result = service.revokeDiscordAdminAccess("uuid-1");

        assertThat(result).isTrue();
        assertThat(stored.admin).isFalse();
        assertThat(stored.adminSource).isEqualTo(DiscordAdminAccessService.SOURCE_NONE);
        assertThat(session.data.admin).isFalse();
        assertThat(session.data.adminSource).isEqualTo(DiscordAdminAccessService.SOURCE_NONE);
        assertThat(player.admin).isFalse();
        verify(Vars.netServer.admins).unAdminPlayer("uuid-1");
        verify(playerDisplayService).refresh(session);
    }

    @Test
    @DisplayName("revokeDiscordAdminAccess clears repository for offline player without refresh")
    void revokeDiscordAdminAccess_clearsRepositoryForOfflinePlayerWithoutRefresh() {
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        SessionService sessionService = mock(SessionService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService service = new DiscordAdminAccessService(playerDataRepository, sessionService, playerDisplayService, mock(AuthStatusBroadcaster.class));

        PlayerData stored = PlayerData.builder().uuid("uuid-1").admin(true).adminSource(DiscordAdminAccessService.SOURCE_DISCORD_ROLE).build();

        when(playerDataRepository.findByUuid("uuid-1")).thenReturn(stored);
        when(playerDataRepository.clearAdminAccess("uuid-1")).thenReturn(true);
        when(sessionService.get("uuid-1")).thenReturn(null);

        boolean result = service.revokeDiscordAdminAccess("uuid-1");

        assertThat(result).isTrue();
        verify(Vars.netServer.admins).unAdminPlayer("uuid-1");
        verify(playerDisplayService, never()).refresh(org.mockito.ArgumentMatchers.any());
    }
}
