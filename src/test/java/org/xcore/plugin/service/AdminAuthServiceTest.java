package org.xcore.plugin.service;

import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.AuthResultStatus;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminAuthServiceTest {

    private NetServer previousNetServer;
    private AdminDataRepository adminDataRepository;
    private SessionService sessionService;
    private PlayerDisplayService playerDisplayService;
    private DiscordAdminAccessService discordAdminAccessService;
    private AuthStatusBroadcaster authStatusBroadcaster;
    private AdminAuthService authService;

    @BeforeEach
    void setUp() {
        previousNetServer = Vars.netServer;
        NetServer netServer = mock(NetServer.class);
        netServer.admins = mock(Administration.class);
        Vars.netServer = netServer;

        adminDataRepository = mock(AdminDataRepository.class);
        sessionService = mock(SessionService.class);
        playerDisplayService = mock(PlayerDisplayService.class);
        discordAdminAccessService = mock(DiscordAdminAccessService.class);
        authStatusBroadcaster = mock(AuthStatusBroadcaster.class);

        authService = new AdminAuthService(
                adminDataRepository,
                sessionService,
                playerDisplayService,
                discordAdminAccessService,
                authStatusBroadcaster
        );
    }

    @AfterEach
    void tearDown() {
        Vars.netServer = previousNetServer;
    }

    @Test
    @DisplayName("Short password rejected")
    void shortPasswordRejected() {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("uuid-1");
        Session session = mock(Session.class);
        session.data = new PlayerData();
        when(sessionService.get("uuid-1")).thenReturn(session);

        var result = authService.authenticate(player, "123");
        assertThat(result.status()).isEqualTo(AuthResultStatus.PASSWORD_TOO_SHORT);
    }

    @Test
    @DisplayName("First login creates password and authorizes if discord role present")
    void firstLoginCreatesPassword() {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("uuid-1");
        Administration.PlayerInfo info = new Administration.PlayerInfo();
        info.adminUsid = "usid-1";
        when(player.getInfo()).thenReturn(info);

        PlayerData data = new PlayerData();
        data.uuid = "uuid-1";
        data.password = ""; // No password set yet

        Session session = mock(Session.class);
        session.data = data;
        when(sessionService.get("uuid-1")).thenReturn(session);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(true);

        var result = authService.authenticate(player, "secret123");
        assertThat(result.status()).isEqualTo(AuthResultStatus.PASSWORD_CREATED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(data.password).isNotEmpty();

        verify(adminDataRepository).save(data);
        verify(player).admin(true);
    }

    @Test
    @DisplayName("Wrong password returns WRONG_PASSWORD")
    void wrongPassword() {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("uuid-1");

        PlayerData data = new PlayerData();
        data.uuid = "uuid-1";
        data.hashPassword("correctPassword");

        Session session = mock(Session.class);
        session.data = data;
        when(sessionService.get("uuid-1")).thenReturn(session);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(true);

        var result = authService.authenticate(player, "wrongPassword");
        assertThat(result.status()).isEqualTo(AuthResultStatus.WRONG_PASSWORD);
        assertThat(result.isSuccess()).isFalse();
        verify(player, never()).admin(true);
    }

    @Test
    @DisplayName("Unprivileged user without Discord role cannot create password")
    void unprivilegedUserCannotCreatePassword() {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("uuid-1");

        PlayerData data = new PlayerData();
        data.uuid = "uuid-1";
        data.password = "";

        Session session = mock(Session.class);
        session.data = data;
        when(sessionService.get("uuid-1")).thenReturn(session);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(false);

        var result = authService.authenticate(player, "secret123");
        assertThat(result.status()).isEqualTo(AuthResultStatus.DISCORD_APPROVAL_REQUIRED);
        assertThat(data.password).isEmpty();
        verify(adminDataRepository, never()).save(any());
        verify(player, never()).admin(true);
    }

    @Test
    @DisplayName("Correct password without Discord role returns DISCORD_APPROVAL_REQUIRED")
    void correctPasswordWithoutDiscord() {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("uuid-1");

        PlayerData data = new PlayerData();
        data.uuid = "uuid-1";
        data.hashPassword("correctPassword");

        Session session = mock(Session.class);
        session.data = data;
        when(sessionService.get("uuid-1")).thenReturn(session);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(false);

        var result = authService.authenticate(player, "correctPassword");
        assertThat(result.status()).isEqualTo(AuthResultStatus.DISCORD_APPROVAL_REQUIRED);
        verify(player, never()).admin(true);
    }

    @Test
    @DisplayName("Login with rememberDevice mints token and authenticateToken succeeds")
    void rememberDeviceMintsTokenAndAllowsTokenLogin() {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("uuid-1");
        Administration.PlayerInfo info = new Administration.PlayerInfo();
        info.adminUsid = "usid-1";
        when(player.getInfo()).thenReturn(info);

        PlayerData data = new PlayerData();
        data.uuid = "uuid-1";
        data.hashPassword("correctPassword");

        Session session = mock(Session.class);
        session.data = data;
        when(sessionService.get("uuid-1")).thenReturn(session);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(true);

        var result = authService.authenticate(player, "correctPassword", true);
        assertThat(result.status()).isEqualTo(AuthResultStatus.SUCCESS);
        assertThat(result.token()).isNotNull().isNotEmpty();
        assertThat(data.deviceTokenHashes).isNotEmpty();

        // Now test token login
        String token = result.token();
        var tokenResult = authService.authenticateToken(player, token);
        assertThat(tokenResult.status()).isEqualTo(AuthResultStatus.SUCCESS);
        assertThat(tokenResult.isSuccess()).isTrue();

        // Logout with token revokes it
        authService.logout(player, token);
        assertThat(data.deviceTokenHashes).isEmpty();
        verify(player).admin(false);

        // Next token login must fail
        var failedResult = authService.authenticateToken(player, token);
        assertThat(failedResult.status()).isEqualTo(AuthResultStatus.TOKEN_INVALID);
    }
}
