package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.MainThreadDispatcher;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.AdminAuthService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class AuthController implements CloudClientController {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCKOUT_DURATION_MS = 60_000L; // 1 minute lockout
    public static final long ATTEMPT_EXPIRY_MS = 300_000L;  // 5 minutes failure window
    /** Only sweep the tracker map once it has grown past roughly a full lobby's worth of players. */
    private static final int TRACKER_EVICTION_THRESHOLD = 256;

    private final AdminAuthService adminAuthService;
    private final SessionService sessionService;
    private final PlayerDisplayService playerDisplayService;
    private final StorageExecutor storageExecutor;
    private final MainThreadDispatcher mainThread;

    private final Set<String> inFlightLogins = ConcurrentHashMap.newKeySet();
    private final Map<String, BruteForceTracker> bruteForceMap = new ConcurrentHashMap<>();

    static class BruteForceTracker {
        int failedAttempts = 0;
        long lockedUntil = 0L;
        long lastAttemptTime = System.currentTimeMillis();

        synchronized boolean isLockedOut(long now) {
            if (now < lockedUntil) {
                return true;
            }
            if (now - lastAttemptTime > ATTEMPT_EXPIRY_MS) {
                failedAttempts = 0;
            }
            return false;
        }

        synchronized void recordFailure(long now) {
            lastAttemptTime = now;
            failedAttempts++;
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockedUntil = now + LOCKOUT_DURATION_MS;
            }
        }

        synchronized void reset() {
            failedAttempts = 0;
            lockedUntil = 0L;
        }

        synchronized boolean isExpired(long now) {
            return now - lastAttemptTime > ATTEMPT_EXPIRY_MS && now >= lockedUntil;
        }
    }

    @Inject
    public AuthController(AdminAuthService adminAuthService,
                          SessionService sessionService,
                          PlayerDisplayService playerDisplayService,
                          StorageExecutor storageExecutor) {
        this(adminAuthService, sessionService, playerDisplayService, storageExecutor, MainThreadDispatcher.mindustry());
    }

    AuthController(AdminAuthService adminAuthService,
                   SessionService sessionService,
                   PlayerDisplayService playerDisplayService,
                   StorageExecutor storageExecutor,
                   MainThreadDispatcher mainThread) {
        this.adminAuthService = adminAuthService;
        this.sessionService = sessionService;
        this.playerDisplayService = playerDisplayService;
        this.storageExecutor = storageExecutor;
        this.mainThread = mainThread;
    }

    @Command("login <password>")
    public void login(XCoreSender sender, @Argument("password") String password) {
        Session session = resolveSession(sender, sessionService);
        if (session == null || session.player == null) return;
        Player player = session.player;
        String uuid = player.uuid();
        Localization local = session.locale();

        // 1. Guard against concurrent /login submissions for the same player
        if (!inFlightLogins.add(uuid)) {
            local.send("commands-login-already-processing", args());
            return;
        }

        // 2. Anti-brute-force gate: prevent CPU denial-of-service via BCrypt
        long now = System.currentTimeMillis();
        evictExpiredTrackers(now);
        BruteForceTracker tracker = bruteForceMap.computeIfAbsent(uuid, k -> new BruteForceTracker());
        if (tracker.isLockedOut(now)) {
            inFlightLogins.remove(uuid);
            local.send("commands-login-rate-limited", args());
            return;
        }

        // 3. Fast validation before offloading to background thread
        if (password == null || password.length() < AdminAuthService.MIN_PASSWORD_LENGTH) {
            inFlightLogins.remove(uuid);
            local.send("error-admin-password-too-short", args());
            return;
        }

        PlayerData data = session.data;
        if (data == null) {
            inFlightLogins.remove(uuid);
            local.send("error-processing-request", args());
            return;
        }

        if (!adminAuthService.hasDiscordAdminAccess(data)) {
            inFlightLogins.remove(uuid);
            local.send("commands-login-request-approval-discord", args());
            return;
        }

        // 4. Send immediate acknowledgement so player UI does not feel frozen
        local.send("commands-login-verifying", args());

        // 5. Offload CPU-intensive BCrypt hashing / verification to StorageExecutor
        //
        // checkPassword hashes into the PlayerData it is handed, and
        // session.data is live state: the tick loop and every command read it, and
        // SessionService can replace it wholesale while a reload is in flight. Hashing
        // into the live object from a storage thread is a cross-thread write to shared
        // state, and if the session's data was swapped meanwhile the new hash would be
        // written to an object nothing reads any more. So the storage stage gets a
        // detached copy carrying only the two fields it needs, and the result is applied
        // back on the game thread.
        PlayerData authSnapshot = PlayerData.builder()
                .uuid(data.uuid)
                .password(data.password)
                .build();
        try {
            storageExecutor.supply(() -> adminAuthService.checkPassword(authSnapshot, password))
                    .whenComplete((result, error) -> {
                        mainThread.execute(() -> {
                            try {
                                // 6. Guard: do not mutate or message disconnected player
                                if (!Async.isPlayerOnline(player)) {
                                    return;
                                }

                                if (error != null) {
                                    PLog.err("Error verifying password for @: @", player.plainName(), error.getMessage());
                                    local.send("error-processing-request", args());
                                    return;
                                }

                                // The password may have been created or reset while this
                                // one was being checked; the result is then about a
                                // credential that no longer exists.
                                if (result.success() && !AdminAuthService.credentialsUnchanged(session.data, authSnapshot, result)) {
                                    local.send("error-processing-request", args());
                                    return;
                                }

                                if (result.success()) {
                                    tracker.reset();
                                    bruteForceMap.remove(uuid);

                                    // A newly created password lives on the snapshot, since
                                    // that is the object the storage stage was allowed to
                                    // write. Copy it onto the live session before granting
                                    // admin, or the grant would outlive a password that was
                                    // never recorded against this session.
                                    if (result.created()) {
                                        session.data.password = authSnapshot.password;
                                        storageExecutor.supply(() -> sessionService.persistData(session.data));
                                    }

                                    // Mutate Mindustry state on the tick thread
                                    adminAuthService.grantAdmin(player, session);

                                    local.send(result.messageKey(), args());
                                } else {
                                    tracker.recordFailure(System.currentTimeMillis());
                                    local.send(result.messageKey(), args());
                                }
                            } finally {
                                inFlightLogins.remove(uuid);
                            }
                        });
                    });
        } catch (Throwable t) {
            inFlightLogins.remove(uuid);
            PLog.err("Failed to dispatch auth task for @: @", player.plainName(), t.getMessage());
            local.send("error-processing-request", args());
        }
    }

    @Command("logout")
    public void logout(XCoreSender sender) {
        Session session = resolveSession(sender, sessionService);
        if (session == null || session.data == null || session.player == null) return;
        Localization local = session.locale();

        // The unAdminPlayer call is not a leftover and not a stray write to the persistent
        // registry: /login is the admin path for players without the admin mod, so this
        // grant is a real registration and logging out is meant to take it back out. The
        // password is untouched, so /login restores it. See AdminAuthService#grantAdmin.
        if (adminAuthService.isLoggedIn(session)) {
            adminAuthService.dropAdmin(session.player, session);
            playerDisplayService.refresh(session);
            local.send("commands-logout-successful", args());
        }
    }

    boolean isInFlight(String uuid) {
        return inFlightLogins.contains(uuid);
    }

    boolean isLockedOut(String uuid) {
        BruteForceTracker tracker = bruteForceMap.get(uuid);
        return tracker != null && tracker.isLockedOut(System.currentTimeMillis());
    }

    int getFailedAttempts(String uuid) {
        BruteForceTracker tracker = bruteForceMap.get(uuid);
        return tracker != null ? tracker.failedAttempts : 0;
    }

    /**
     * Keeps the tracker map bounded. Entries are created per UUID on every attempt, so without
     * this a player cycling through fresh UUIDs would grow the map for the life of the server.
     */
    private void evictExpiredTrackers(long now) {
        if (bruteForceMap.size() < TRACKER_EVICTION_THRESHOLD) return;
        bruteForceMap.values().removeIf(tracker -> tracker.isExpired(now));
    }

    void resetForTesting() {
        inFlightLogins.clear();
        bruteForceMap.clear();
    }
}
