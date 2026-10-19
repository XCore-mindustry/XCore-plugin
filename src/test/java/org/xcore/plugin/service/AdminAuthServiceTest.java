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

    private Session liveSession(Player player, PlayerData data) {
        Session session = mock(Session.class);
        session.data = data;
        session.player = player;
        when(sessionService.get("uuid-1")).thenReturn(session);
        return session;
    }

    private Player loggingIn() {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("uuid-1");
        when(player.getInfo()).thenReturn(new Administration.PlayerInfo());
        return player;
    }

    @Test
    @DisplayName("checkPassword hashes into the copy it is given and never touches the repository")
    void checkPassword_isPure() {
        PlayerData credentials = PlayerData.builder().uuid("uuid-1").build();

        var created = authService.checkPassword(credentials, "newPassword123");
        var verified = authService.checkPassword(credentials, "newPassword123");
        var wrong = authService.checkPassword(credentials, "somethingElse1");

        assertThat(created.success()).isTrue();
        assertThat(created.created()).isTrue();
        assertThat(credentials.password).isNotBlank();
        assertThat(verified.success()).isTrue();
        assertThat(verified.created()).isFalse();
        assertThat(wrong.success()).isFalse();
        verifyNoInteractions(adminDataRepository);
    }

    @Test
    @DisplayName("applyLogin grants admin, records a created password on the live data and does no I/O")
    void applyLogin_success() {
        Player player = loggingIn();
        PlayerData data = new PlayerData();
        data.uuid = "uuid-1";
        Session session = liveSession(player, data);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(true);

        PlayerData credentials = PlayerData.builder().uuid("uuid-1").password("fresh-hash").build();
        var verification = new AdminAuthService.PasswordVerificationResult(true, true, "commands-login-admin-password-created");

        var result = authService.applyLogin(player, credentials, verification, true);

        assertThat(result.status()).isEqualTo(AuthResultStatus.PASSWORD_CREATED);
        assertThat(result.token()).isNotBlank();
        assertThat(data.password).isEqualTo("fresh-hash");
        assertThat(data.hasDeviceToken(AdminAuthService.hashToken(result.token()))).isTrue();
        verify(player).admin(true);
        verify(playerDisplayService).refresh(session);
        verifyNoInteractions(adminDataRepository);
    }

    @Test
    @DisplayName("applyLogin does not let a second first-time login replace the password the first one created")
    void applyLogin_passwordCreatedMeanwhile() {
        Player player = loggingIn();
        PlayerData data = new PlayerData();
        data.password = "first-hash";
        liveSession(player, data);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(true);

        PlayerData credentials = PlayerData.builder().uuid("uuid-1").password("second-hash").build();
        var verification = new AdminAuthService.PasswordVerificationResult(true, true, "commands-login-admin-password-created");

        var result = authService.applyLogin(player, credentials, verification, true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.token()).isNull();
        assertThat(data.password).isEqualTo("first-hash");
        verify(player, never()).admin(true);
    }

    @Test
    @DisplayName("applyLogin grants nothing when the password was reset or changed while it was checked")
    void applyLogin_passwordChangedMeanwhile() {
        Player player = loggingIn();
        PlayerData data = new PlayerData();
        liveSession(player, data);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(true);

        PlayerData credentials = PlayerData.builder().uuid("uuid-1").password("old-hash").build();
        var verification = new AdminAuthService.PasswordVerificationResult(true, false, "commands-login-success");

        data.password = null;
        assertThat(authService.applyLogin(player, credentials, verification, false).isSuccess()).isFalse();
        data.password = "new-hash";
        assertThat(authService.applyLogin(player, credentials, verification, false).isSuccess()).isFalse();
        data.password = "old-hash";
        assertThat(authService.applyLogin(player, credentials, verification, false).isSuccess()).isTrue();

        verify(player, times(1)).admin(true);
    }

    @Test
    @DisplayName("applyLogin refuses a wrong password without touching the player")
    void applyLogin_wrongPassword() {
        Player player = loggingIn();
        liveSession(player, new PlayerData());
        var verification = new AdminAuthService.PasswordVerificationResult(false, false, "error-wrong-admin-password");

        var result = authService.applyLogin(player, new PlayerData(), verification, false);

        assertThat(result.status()).isEqualTo(AuthResultStatus.WRONG_PASSWORD);
        verify(player, never()).admin(true);
    }

    @Test
    @DisplayName("applyLogin grants nothing when the Discord approval went away while the password was hashed")
    void applyLogin_approvalWithdrawn() {
        Player player = loggingIn();
        PlayerData data = new PlayerData();
        liveSession(player, data);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(false);
        var verification = new AdminAuthService.PasswordVerificationResult(true, false, "commands-login-success");

        var result = authService.applyLogin(player, new PlayerData(), verification, false);

        assertThat(result.status()).isEqualTo(AuthResultStatus.DISCORD_APPROVAL_REQUIRED);
        verify(player, never()).admin(true);
    }

    @Test
    @DisplayName("applyLogin grants nothing when the uuid now belongs to another connection")
    void applyLogin_reconnected() {
        Player player = loggingIn();
        PlayerData data = new PlayerData();
        liveSession(mock(Player.class), data);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(true);
        var verification = new AdminAuthService.PasswordVerificationResult(true, false, "commands-login-success");

        var result = authService.applyLogin(player, new PlayerData(), verification, false);

        assertThat(result.status()).isEqualTo(AuthResultStatus.SESSION_NOT_FOUND);
        verify(player, never()).admin(true);
    }

    @Test
    @DisplayName("resumeWithToken restores admin without writing to the database")
    void resumeWithToken_noWrite() {
        Player player = loggingIn();
        PlayerData data = new PlayerData();
        data.addDeviceToken(AdminAuthService.hashToken("device-token"), System.currentTimeMillis() + 60_000L);
        liveSession(player, data);
        when(discordAdminAccessService.hasDiscordAdminAccess(data)).thenReturn(true);

        var result = authService.resumeWithToken(player, "device-token");

        assertThat(result.status()).isEqualTo(AuthResultStatus.SUCCESS);
        verify(player).admin(true);
        verifyNoInteractions(adminDataRepository);
    }
}
