package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.model.AuthStatusPacket;
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

    public AuthResult authenticate(Player player, String password, boolean rememberDevice) {
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

        PlayerData data = session.data;

        // Security check: Only players with Discord admin role can authenticate or set an admin password
        if (!discordAdminAccessService.hasDiscordAdminAccess(data)) {
            return new AuthResult(AuthResultStatus.DISCORD_APPROVAL_REQUIRED, "commands-login-request-approval-discord");
        }

        boolean created = false;
        if (data.password == null || data.password.isEmpty()) {
            data.hashPassword(password);
            adminDataRepository.save(data);
            created = true;
        }

        if (data.verifyPassword(password)) {
            rateLimits.remove(player.uuid());

            player.admin(true);
            String usid = player.getInfo() != null ? player.getInfo().adminUsid : null;
            netServer.admins.adminPlayer(player.uuid(), usid);
            playerDisplayService.refresh(session);

            String mintedToken = null;
            if (rememberDevice) {
                mintedToken = generateDeviceToken();
                String tokenHash = hashToken(mintedToken);
                long expiresAt = System.currentTimeMillis() + TOKEN_TTL_MILLIS;
                data.addDeviceToken(tokenHash, expiresAt);
                adminDataRepository.save(data);
            }

            return new AuthResult(
                created ? AuthResultStatus.PASSWORD_CREATED : AuthResultStatus.SUCCESS,
                created ? "commands-login-admin-password-created" : "commands-login-success",
                mintedToken
            );
        } else {
            return new AuthResult(AuthResultStatus.WRONG_PASSWORD, "error-wrong-admin-password");
        }
    }

    public AuthResult authenticateToken(Player player, String token) {
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

            // Save in case expired tokens got purged during hasDeviceToken
            adminDataRepository.save(data);

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
