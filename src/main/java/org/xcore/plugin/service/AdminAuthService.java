package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.protocol.packet.auth.AuthStatusPacket;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.AuthResultStatus;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static mindustry.Vars.netServer;

@Singleton
public class AdminAuthService {
    public record AuthResult(AuthResultStatus status, String messageKey, String token) {
        public AuthResult(AuthResultStatus status, String messageKey) {
            this(status, messageKey, null);
        }

        public boolean isSuccess() {
            return status == AuthResultStatus.SUCCESS || status == AuthResultStatus.PASSWORD_CREATED;
        }
    }

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final long TOKEN_TTL_MILLIS = 60L * 24 * 3600 * 1000L; // 60 days

    private final AdminDataRepository adminDataRepository;
    private final SessionService sessionService;
    private final PlayerDisplayService playerDisplayService;
    private final DiscordAdminAccessService discordAdminAccessService;
    private final AuthStatusBroadcaster authStatusBroadcaster;
    private final SecureRandom secureRandom = new SecureRandom();

    // Rate limiting: max 5 attempts per minute per player UUID
    private final Map<String, RateLimitTracker> rateLimits = new ConcurrentHashMap<>();

    private static class RateLimitTracker {
        long windowStart = System.currentTimeMillis();
        int attempts = 0;

        synchronized boolean isRateLimited() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60_000) {
                windowStart = now;
                attempts = 0;
            }
            attempts++;
            return attempts > 5;
        }

        synchronized boolean isExpired() {
            return System.currentTimeMillis() - windowStart > 300_000;
        }
    }

    @Inject
    public AdminAuthService(AdminDataRepository adminDataRepository,
                            SessionService sessionService,
                            PlayerDisplayService playerDisplayService,
                            DiscordAdminAccessService discordAdminAccessService,
                            AuthStatusBroadcaster authStatusBroadcaster) {
        this.adminDataRepository = adminDataRepository;
        this.sessionService = sessionService;
        this.playerDisplayService = playerDisplayService;
        this.discordAdminAccessService = discordAdminAccessService;
        this.authStatusBroadcaster = authStatusBroadcaster;
    }

    public static String hashToken(String token) {
        if (token == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String generateDeviceToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public void clearRateLimit(String playerUuid) {
        if (playerUuid != null) {
            rateLimits.remove(playerUuid);
        }
    }

    private void cleanupExpiredRateLimits() {
        if (rateLimits.size() > 100) {
            rateLimits.entrySet().removeIf(e -> e.getValue().isExpired());
        }
    }

    public AuthStatusPacket buildStatus(Player player) {
        long rev = authStatusBroadcaster.nextRevision();
        if (player == null) return new AuthStatusPacket(false, "", false, false, false, rev);
        Session session = sessionService.get(player.uuid());
        if (session == null || session.data == null) {
            return new AuthStatusPacket(false, "", false, false, player.admin, rev);
        }
        PlayerData data = session.data;
        boolean isLinked = data.discordId != null && !data.discordId.isBlank();
        boolean hasDiscordAdmin = discordAdminAccessService.hasDiscordAdminAccess(data);
        boolean hasPassword = data.password != null && !data.password.isEmpty();
        return new AuthStatusPacket(isLinked, data.discordUsername, hasDiscordAdmin, hasPassword, player.admin, rev);
    }

    public void pushStatus(Player player) {
        if (player == null) return;
        Session session = sessionService.get(player.uuid());
        if (session == null || session.data == null) {
            authStatusBroadcaster.pushStatus(player, false, "", false, false, player.admin);
            return;
        }
        PlayerData data = session.data;
        boolean isLinked = data.discordId != null && !data.discordId.isBlank();
        boolean hasDiscordAdmin = discordAdminAccessService.hasDiscordAdminAccess(data);
        boolean hasPassword = data.password != null && !data.password.isEmpty();
        authStatusBroadcaster.pushStatus(player, isLinked, data.discordUsername, hasDiscordAdmin, hasPassword, player.admin);
    }

    public AuthResult authenticate(Player player, String password) {
        return authenticate(player, password, false);
    }

    public record PasswordVerificationResult(boolean success, boolean created, String messageKey) {}

    /**
     * Performs password hashing or verification (CPU-intensive BCrypt operation) and saves updated
     * password data. This method contains NO Mindustry main-thread mutations and is safe to run
     * on a background executor.
     */
    public PasswordVerificationResult verifyOrSetPassword(PlayerData data, String password) {
        PasswordVerificationResult result = checkPassword(data, password);
        if (result.created()) {
            adminDataRepository.save(data);
        }
        return result;
    }

    /**
     * The BCrypt half of a login on its own: verifies {@code password} against
     * {@code credentials.password}, or hashes it into that field when there is none yet.
     * <p>
     * It touches neither the repository nor game state, so it can run on a storage thread
     * against a detached copy that carries nothing but the uuid and the password. Saving such
     * a copy would replace the stored player with it; the caller stores the new hash through
     * the live record instead.
     */
    public PasswordVerificationResult checkPassword(PlayerData credentials, String password) {
        boolean created = false;
        if (credentials.password == null || credentials.password.isEmpty()) {
            credentials.hashPassword(password);
            created = true;
        }

        if (credentials.verifyPassword(password)) {
            return new PasswordVerificationResult(
                true,
                created,
                created ? "commands-login-admin-password-created" : "commands-login-success"
            );
        } else {
            return new PasswordVerificationResult(false, false, "error-wrong-admin-password");
        }
    }

    /**
     * Checks if player data has Discord admin role access.
     */
    public boolean hasDiscordAdminAccess(PlayerData data) {
        return discordAdminAccessService.hasDiscordAdminAccess(data);
    }

    /**
     * Applies admin privileges to a player and updates server state on the Mindustry tick thread.
     *
     * <p>This is the admin path for people who do not have the admin mod, so unlike the
     * Discord-admin grant it is a real registration rather than a revocable session overlay.
     * That is why {@code logout} takes it back out again: the point of logout is to drop the
     * privileges, and the password stays in {@code PlayerData.password} so the holder can
     * grant themselves admin again.
     *
     * <p>Note what it deliberately does <em>not</em> do: it never writes
     * {@code PlayerData.admin}. That flag is the Discord-admin grant, which is a different
     * source with different rules, and overwriting it here would conflate the two. The
     * consequence is that this grant lives only in the in-memory {@code netServer.admins}
     * registry, so it does not survive a reconnect or a restart and the player grants it to
     * themselves again by logging in. That is the intended trade - the credential is
     * remembered, the privilege is not.
     */
    public void grantAdmin(Player player, Session session) {
        player.admin(true);
        String usid = player.getInfo() != null ? player.getInfo().adminUsid : null;
        netServer.admins.adminPlayer(player.uuid(), usid);
        playerDisplayService.refresh(session);
    }

    /**
     * Everything about a password login that can be decided without hashing: the session, the
     * length of the password, the rate limit and the Discord approval.
     *
     * @return why the attempt is refused, or null when the password is worth checking
     */
    public AuthResult rejectLogin(Player player, String password) {
        if (player == null) {
            return new AuthResult(AuthResultStatus.SESSION_NOT_FOUND, "error-processing-request");
        }

        Session session = sessionService.get(player.uuid());
        if (session == null || session.data == null) {
            return new AuthResult(AuthResultStatus.SESSION_NOT_FOUND, "error-processing-request");
        }

        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return new AuthResult(AuthResultStatus.PASSWORD_TOO_SHORT, "error-admin-password-too-short");
        }

        cleanupExpiredRateLimits();
        RateLimitTracker tracker = rateLimits.computeIfAbsent(player.uuid(), k -> new RateLimitTracker());
        if (tracker.isRateLimited()) {
            return new AuthResult(AuthResultStatus.RATE_LIMITED, "error-wrong-admin-password");
        }

        // Security check: Only players with Discord admin role can authenticate or set an admin password
        if (!hasDiscordAdminAccess(session.data)) {
            return new AuthResult(AuthResultStatus.DISCORD_APPROVAL_REQUIRED, "commands-login-request-approval-discord");
        }

        return null;
    }

    /**
     * Finishes a login whose password was checked off the game thread by {@link #checkPassword}.
     * Game thread only, and free of I/O: when the result is a success that created a password
     * or minted a token, the caller still has to store the player's data.
     *
     * @param credentials the detached copy {@link #checkPassword} worked on
     */
    public AuthResult applyLogin(Player player,
                                 PlayerData credentials,
                                 PasswordVerificationResult verification,
                                 boolean rememberDevice) {
        Session session = player != null ? sessionService.get(player.uuid()) : null;
        if (session == null || session.data == null || session.player != player) {
            return new AuthResult(AuthResultStatus.SESSION_NOT_FOUND, "error-processing-request");
        }
        if (!verification.success()) {
            return new AuthResult(AuthResultStatus.WRONG_PASSWORD, verification.messageKey());
        }

        PlayerData data = session.data;
        // The approval may have been withdrawn while the password was being hashed.
        if (!hasDiscordAdminAccess(data)) {
            return new AuthResult(AuthResultStatus.DISCORD_APPROVAL_REQUIRED, "commands-login-request-approval-discord");
        }

        if (!credentialsUnchanged(data, credentials, verification)) {
            return new AuthResult(AuthResultStatus.SESSION_NOT_FOUND, "error-processing-request");
        }

        rateLimits.remove(player.uuid());
        if (verification.created()) {
            data.password = credentials.password;
        }
        grantAdmin(player, session);

        String mintedToken = null;
        if (rememberDevice) {
            mintedToken = generateDeviceToken();
            data.addDeviceToken(hashToken(mintedToken), System.currentTimeMillis() + TOKEN_TTL_MILLIS);
        }

        return new AuthResult(
                verification.created() ? AuthResultStatus.PASSWORD_CREATED : AuthResultStatus.SUCCESS,
                verification.messageKey(),
                mintedToken
        );
    }

    /**
     * Whether the password {@link #checkPassword} worked from is still the player's password.
     * <p>
     * The check runs off the game thread, and meanwhile another login may have created the
     * password or a reset may have cleared it. A result computed from the old state proves
     * nothing about the new one, so it must not be applied.
     *
     * @param credentials the detached copy {@link #checkPassword} worked on
     */
    public static boolean credentialsUnchanged(PlayerData live, PlayerData credentials, PasswordVerificationResult verification) {
        boolean liveHasPassword = live.password != null && !live.password.isEmpty();
        if (verification.created()) {
            return !liveHasPassword;
        }
        return liveHasPassword && live.password.equals(credentials.password);
    }

    public AuthResult authenticate(Player player, String password, boolean rememberDevice) {
        AuthResult rejected = rejectLogin(player, password);
        if (rejected != null) {
            return rejected;
        }

        Session session = sessionService.get(player.uuid());
        PlayerData data = session.data;

        PasswordVerificationResult verification = verifyOrSetPassword(data, password);

        if (verification.success()) {
            rateLimits.remove(player.uuid());

            grantAdmin(player, session);

            String mintedToken = null;
            if (rememberDevice) {
                mintedToken = generateDeviceToken();
                String tokenHash = hashToken(mintedToken);
                long expiresAt = System.currentTimeMillis() + TOKEN_TTL_MILLIS;
                data.addDeviceToken(tokenHash, expiresAt);
                adminDataRepository.save(data);
            }

            return new AuthResult(
                verification.created() ? AuthResultStatus.PASSWORD_CREATED : AuthResultStatus.SUCCESS,
                verification.messageKey(),
                mintedToken
            );
        } else {
            return new AuthResult(AuthResultStatus.WRONG_PASSWORD, verification.messageKey());
        }
    }

    public AuthResult authenticateToken(Player player, String token) {
        AuthResult result = resumeWithToken(player, token);
        if (result.isSuccess()) {
            // Save in case expired tokens got purged during hasDeviceToken
            adminDataRepository.save(sessionService.get(player.uuid()).data);
        }
        return result;
    }

    /**
     * {@link #authenticateToken} without the write: nothing here leaves the game thread or
     * waits on the database. After a success the caller stores the player's data, because
     * looking the token up drops the expired ones.
     */
    public AuthResult resumeWithToken(Player player, String token) {
        if (player == null) {
            return new AuthResult(AuthResultStatus.SESSION_NOT_FOUND, "error-processing-request");
        }

        Session session = sessionService.get(player.uuid());
        if (session == null || session.data == null) {
            return new AuthResult(AuthResultStatus.SESSION_NOT_FOUND, "error-processing-request");
        }

        if (token == null || token.isBlank()) {
            return new AuthResult(AuthResultStatus.TOKEN_INVALID, "error-token-invalid");
        }

        cleanupExpiredRateLimits();
        RateLimitTracker tracker = rateLimits.computeIfAbsent(player.uuid(), k -> new RateLimitTracker());
        if (tracker.isRateLimited()) {
            return new AuthResult(AuthResultStatus.RATE_LIMITED, "error-wrong-admin-password");
        }

        PlayerData data = session.data;

        // Security check: Must have Discord admin role to resume admin session
        if (!discordAdminAccessService.hasDiscordAdminAccess(data)) {
            return new AuthResult(AuthResultStatus.DISCORD_APPROVAL_REQUIRED, "commands-login-request-approval-discord");
        }

        String tokenHash = hashToken(token);
        if (data.hasDeviceToken(tokenHash)) {
            rateLimits.remove(player.uuid());

            player.admin(true);
            String usid = player.getInfo() != null ? player.getInfo().adminUsid : null;
            netServer.admins.adminPlayer(player.uuid(), usid);
            playerDisplayService.refresh(session);

            return new AuthResult(AuthResultStatus.SUCCESS, "commands-login-success", token);
        } else {
            return new AuthResult(AuthResultStatus.TOKEN_INVALID, "error-token-invalid");
        }
    }

    public void logout(Player player, String tokenToRevoke) {
        if (player == null) return;
        Session session = sessionService.get(player.uuid());
        if (session != null && session.data != null) {
            if (tokenToRevoke != null && !tokenToRevoke.isBlank()) {
                String tokenHash = hashToken(tokenToRevoke);
                session.data.removeDeviceToken(tokenHash);
                adminDataRepository.save(session.data);
            }
        }

        // 1. Runtime de-admin
        player.admin(false);
        netServer.admins.unAdminPlayer(player.uuid());

        // 2. Display refresh
        if (session != null) {
            playerDisplayService.refresh(session);
        }

        // 3. Push authoritative status
        pushStatus(player);
    }

    public void logoutAll(Player player) {
        if (player == null) return;
        Session session = sessionService.get(player.uuid());
        if (session != null && session.data != null) {
            session.data.clearDeviceTokens();
            adminDataRepository.save(session.data);
        }

        player.admin(false);
        netServer.admins.unAdminPlayer(player.uuid());

        if (session != null) {
            playerDisplayService.refresh(session);
        }

        pushStatus(player);
    }
}
