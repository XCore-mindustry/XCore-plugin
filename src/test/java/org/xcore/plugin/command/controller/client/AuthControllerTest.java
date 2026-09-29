package org.xcore.plugin.command.controller.client;

import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.concurrent.MainThreadDispatcher;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.AdminAuthService;
import org.xcore.plugin.service.AdminAuthService.PasswordVerificationResult;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private AdminAuthService adminAuthService;
    private SessionService sessionService;
    private PlayerDisplayService playerDisplayService;
    private StorageExecutor storageExecutor;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        adminAuthService = mock(AdminAuthService.class);
        sessionService = mock(SessionService.class);
        playerDisplayService = mock(PlayerDisplayService.class);
        // Use single-thread StorageExecutor for predictable testing
        storageExecutor = new StorageExecutor(4);

        // Synchronous main-thread dispatcher for immediate continuation in tests
        MainThreadDispatcher synchronousMainThread = Runnable::run;

        controller = new AuthController(
                adminAuthService,
                sessionService,
                playerDisplayService,
                storageExecutor,
                synchronousMainThread
        );
    }

    @AfterEach
    void tearDown() {
        if (storageExecutor != null) {
            storageExecutor.shutdown();
        }
    }

    private TestContext createContext(String uuid, boolean online) {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn(uuid);
        when(player.plainName()).thenReturn("Tester-" + uuid);
        when(player.isAdded()).thenReturn(online);

        NetConnection con = mock(NetConnection.class);
        when(con.isConnected()).thenReturn(online);
        player.con = con;

        PlayerData data = new PlayerData();
        data.uuid = uuid;

        Localization local = mock(Localization.class);
        Session session = mock(Session.class);
        session.player = player;
        session.data = data;
        when(session.locale()).thenReturn(local);

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.session()).thenReturn(session);
        when(sender.player()).thenReturn(player);

        when(sessionService.get(uuid)).thenReturn(session);

        return new TestContext(sender, session, player, data, local);
    }

    private record TestContext(
            XCoreSender sender,
            Session session,
            Player player,
            PlayerData data,
            Localization local
    ) {}

    @Test
    @DisplayName("successful login sends immediate ack, executes off-thread, and grants admin on tick thread")
    void login_successfulAuthentication() throws Exception {
        var ctx = createContext("uuid-1", true);
        when(adminAuthService.hasDiscordAdminAccess(ctx.data())).thenReturn(true);
        when(adminAuthService.verifyOrSetPassword(ctx.data(), "secret123"))
                .thenReturn(new PasswordVerificationResult(true, false, "commands-login-success"));

        controller.login(ctx.sender(), "secret123");

        // Wait briefly for background execution
        org.testcontainers.shaded.org.awaitility.Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(ctx.local()).send(eq("commands-login-verifying"), any());
                    verify(adminAuthService).grantAdmin(ctx.player(), ctx.session());
                    verify(ctx.local()).send(eq("commands-login-success"), any());
                    assertThat(controller.isInFlight("uuid-1")).isFalse();
                    assertThat(controller.getFailedAttempts("uuid-1")).isZero();
                });
    }

    @Test
    @DisplayName("first-time login creates password and sends password-created key")
    void login_firstTimePasswordCreated() {
        var ctx = createContext("uuid-create", true);
        when(adminAuthService.hasDiscordAdminAccess(ctx.data())).thenReturn(true);
        when(adminAuthService.verifyOrSetPassword(ctx.data(), "newPassword123"))
                .thenReturn(new PasswordVerificationResult(true, true, "commands-login-admin-password-created"));

        controller.login(ctx.sender(), "newPassword123");

        org.testcontainers.shaded.org.awaitility.Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(ctx.local()).send(eq("commands-login-verifying"), any());
                    verify(adminAuthService).grantAdmin(ctx.player(), ctx.session());
                    verify(ctx.local()).send(eq("commands-login-admin-password-created"), any());
                    assertThat(controller.isInFlight("uuid-create")).isFalse();
                });
    }

    @Test
    @DisplayName("wrong password increments failure counter, does not grant admin, and sends error")
    void login_wrongPassword() {
        var ctx = createContext("uuid-wrong", true);
        when(adminAuthService.hasDiscordAdminAccess(ctx.data())).thenReturn(true);
        when(adminAuthService.verifyOrSetPassword(ctx.data(), "wrongPassword123"))
                .thenReturn(new PasswordVerificationResult(false, false, "error-wrong-admin-password"));

        controller.login(ctx.sender(), "wrongPassword123");

        org.testcontainers.shaded.org.awaitility.Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(ctx.local()).send(eq("commands-login-verifying"), any());
                    verify(adminAuthService, never()).grantAdmin(any(), any());
                    verify(ctx.local()).send(eq("error-wrong-admin-password"), any());
                    assertThat(controller.getFailedAttempts("uuid-wrong")).isEqualTo(1);
                    assertThat(controller.isInFlight("uuid-wrong")).isFalse();
                });
    }

    @Test
    @DisplayName("concurrent login submissions for same player are rejected with in-flight message")
    void login_concurrentSubmissions_rejectsSecond() throws Exception {
        var ctx = createContext("uuid-concurrent", true);
        when(adminAuthService.hasDiscordAdminAccess(ctx.data())).thenReturn(true);

        CountDownLatch holdBackground = new CountDownLatch(1);
        CountDownLatch backgroundStarted = new CountDownLatch(1);

        when(adminAuthService.verifyOrSetPassword(ctx.data(), "password123")).thenAnswer(inv -> {
            backgroundStarted.countDown();
            holdBackground.await(3, TimeUnit.SECONDS);
            return new PasswordVerificationResult(true, false, "commands-login-success");
        });

        // First login starts in background
        controller.login(ctx.sender(), "password123");

        assertThat(backgroundStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(controller.isInFlight("uuid-concurrent")).isTrue();

        // Second concurrent login should immediately be rejected
        controller.login(ctx.sender(), "password123");
        verify(ctx.local()).send(eq("commands-login-already-processing"), any());

        // Release the background task
        holdBackground.countDown();

        org.testcontainers.shaded.org.awaitility.Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(controller.isInFlight("uuid-concurrent")).isFalse());
    }

    @Test
    @DisplayName("anti-brute-force gate locks out after N failures and skips BCrypt entirely")
    void login_bruteForceLockout() {
        var ctx = createContext("uuid-brute", true);
        when(adminAuthService.hasDiscordAdminAccess(ctx.data())).thenReturn(true);
        when(adminAuthService.verifyOrSetPassword(ctx.data(), "badPassword1"))
                .thenReturn(new PasswordVerificationResult(false, false, "error-wrong-admin-password"));

        // Fail 5 times sequentially
        for (int i = 0; i < AuthController.MAX_FAILED_ATTEMPTS; i++) {
            final int expectedFailures = i + 1;
            controller.login(ctx.sender(), "badPassword1");
            org.testcontainers.shaded.org.awaitility.Awaitility.await()
                    .atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(controller.getFailedAttempts("uuid-brute"))
                            .isEqualTo(expectedFailures));
        }

        assertThat(controller.isLockedOut("uuid-brute")).isTrue();
        reset(adminAuthService); // Clear mock invocations

        // 6th attempt while locked out: rejected immediately without invoking BCrypt
        controller.login(ctx.sender(), "badPassword1");

        verify(ctx.local()).send(eq("commands-login-rate-limited"), any());
        verify(adminAuthService, never()).verifyOrSetPassword(any(), any());
        assertThat(controller.isInFlight("uuid-brute")).isFalse();
    }

    @Test
    @DisplayName("player disconnect while verifying does not grant admin or touch player")
    void login_disconnectedPlayer_doesNotGrantAdmin() throws Exception {
        var ctx = createContext("uuid-disconnect", true);
        when(adminAuthService.hasDiscordAdminAccess(ctx.data())).thenReturn(true);

        CountDownLatch holdBackground = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);

        when(adminAuthService.verifyOrSetPassword(ctx.data(), "password123")).thenAnswer(inv -> {
            started.countDown();
            holdBackground.await(3, TimeUnit.SECONDS);
            return new PasswordVerificationResult(true, false, "commands-login-success");
        });

        controller.login(ctx.sender(), "password123");
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        // Player disconnects while background task is still running
        when(ctx.player().isAdded()).thenReturn(false);
        when(ctx.player().con.isConnected()).thenReturn(false);

        holdBackground.countDown();

        org.testcontainers.shaded.org.awaitility.Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(adminAuthService, never()).grantAdmin(any(), any());
                    verify(ctx.local(), never()).send(eq("commands-login-success"), any());
                    assertThat(controller.isInFlight("uuid-disconnect")).isFalse();
                });
    }

    @Test
    @DisplayName("short password rejected immediately before background execution")
    void login_shortPassword() {
        var ctx = createContext("uuid-short", true);

        controller.login(ctx.sender(), "short");

        verify(ctx.local()).send(eq("error-admin-password-too-short"), any());
        verify(adminAuthService, never()).verifyOrSetPassword(any(), any());
        assertThat(controller.isInFlight("uuid-short")).isFalse();
    }

    @Test
    @DisplayName("player without discord admin role rejected immediately before background execution")
    void login_noDiscordAdminRole() {
        var ctx = createContext("uuid-nodiscord", true);
        when(adminAuthService.hasDiscordAdminAccess(ctx.data())).thenReturn(false);

        controller.login(ctx.sender(), "validPassword123");

        verify(ctx.local()).send(eq("commands-login-request-approval-discord"), any());
        verify(adminAuthService, never()).verifyOrSetPassword(any(), any());
        assertThat(controller.isInFlight("uuid-nodiscord")).isFalse();
    }
}
