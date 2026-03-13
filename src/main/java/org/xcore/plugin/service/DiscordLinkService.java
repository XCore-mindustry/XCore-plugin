package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.network.RedisDiscordLinkCodeStore;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.security.SecureRandom;
import java.util.Locale;

@Singleton
public class DiscordLinkService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final long CODE_TTL_MILLIS = 10 * 60 * 1000L;

    private final RedisDiscordLinkCodeStore discordLinkCodeStore;
    private final PlayerDataRepository playerDataRepository;
    private final SessionService sessionService;
    private final NetworkService networkService;
    private final Config config;
    private final DiscordAdminAccessService discordAdminAccessService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    public DiscordLinkService(RedisDiscordLinkCodeStore discordLinkCodeStore,
                              PlayerDataRepository playerDataRepository,
                              SessionService sessionService,
                              NetworkService networkService,
                              Config config,
                              DiscordAdminAccessService discordAdminAccessService) {
        this.discordLinkCodeStore = discordLinkCodeStore;
        this.playerDataRepository = playerDataRepository;
        this.sessionService = sessionService;
        this.networkService = networkService;
        this.config = config;
        this.discordAdminAccessService = discordAdminAccessService;
    }

    public LinkCodeResult createCode(Session session) {
        PlayerData data = playerData(session);
        if (data == null) {
            return LinkCodeResult.error("session-missing");
        }

        if (isLinked(data)) {
            return LinkCodeResult.error("already-linked");
        }

        discordLinkCodeStore.invalidatePendingByPlayerUuid(data.uuid);

        String code = nextCode();
        long now = System.currentTimeMillis();
        long expiresAt = now + CODE_TTL_MILLIS;

        var linkCode = new RedisDiscordLinkCodeStore.LinkCodePayload(
                code,
                data.uuid,
                data.pid,
                data.nickname,
                config.server,
                now,
                expiresAt
        );

        if (!discordLinkCodeStore.store(linkCode)) {
            return LinkCodeResult.error("save-failed");
        }

        networkService.post(new SocketEvents.DiscordLinkCodeCreatedEvent(
                code,
                data.uuid,
                data.pid,
                data.nickname,
                config.server,
                now,
                expiresAt
        ));

        return LinkCodeResult.success(code, expiresAt);
    }

    public LinkCodeResult getOrCreateActiveCode(Session session) {
        PlayerData data = playerData(session);
        if (data == null) {
            return LinkCodeResult.error("session-missing");
        }

        if (isLinked(data)) {
            return LinkCodeResult.error("already-linked");
        }

        long now = System.currentTimeMillis();
        var pending = discordLinkCodeStore.findPendingByPlayerUuid(data.uuid);
        if (pending != null) {
            if (pending.expiresAt() <= now) {
                discordLinkCodeStore.invalidatePendingByPlayerUuid(data.uuid);
            } else {
                return LinkCodeResult.success(pending.code(), pending.expiresAt());
            }
        }

        return createCode(session);
    }

    public LinkStatusResult status(Session session) {
        PlayerData data = playerData(session);
        if (data == null || !isLinked(data)) {
            return LinkStatusResult.notLinked();
        }

        return LinkStatusResult.linked(data.discordId, data.discordUsername, data.discordLinkedAt);
    }

    public boolean unlink(Session session) {
        PlayerData data = playerData(session);
        if (data == null || !isLinked(data)) {
            return false;
        }

        String discordId = data.discordId;
        if (!playerDataRepository.clearDiscordLink(data.uuid)) {
            return false;
        }

        String discordUsername = data.discordUsername;
        clearDiscordState(data);
        boolean revoked = discordAdminAccessService.revokeDiscordAdminAccess(data.uuid);
        if (!revoked) {
            return false;
        }
        publishAdminAccessChanged(data.uuid, data.pid, discordId, discordUsername, false, DiscordAdminAccessService.SOURCE_NONE, "plugin/unlink", "discord unlink");
        publishStatusChanged(data, discordId, "", "unlinked", System.currentTimeMillis());
        return true;
    }

    public boolean unlink(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }

        PlayerData data = playerDataRepository.findByUuid(playerUuid);
        if (data == null || !isLinked(data)) {
            return false;
        }

        String discordId = data.discordId;
        if (!playerDataRepository.clearDiscordLink(playerUuid)) {
            return false;
        }

        var session = sessionService.get(playerUuid);
        if (session != null) {
            clearDiscordState(session.data);
        }

        String discordUsername = data.discordUsername;
        clearDiscordState(data);
        boolean revoked = discordAdminAccessService.revokeDiscordAdminAccess(playerUuid);
        if (!revoked) {
            return false;
        }
        publishAdminAccessChanged(playerUuid, data.pid, discordId, discordUsername, false, DiscordAdminAccessService.SOURCE_NONE, "plugin/unlink", "discord unlink");

        publishStatusChanged(data, discordId, "", "unlinked", System.currentTimeMillis());
        return true;
    }

    public ConfirmResult confirmLink(String code,
                                     String playerUuid,
                                     int playerPid,
                                     String discordId,
                                     String discordUsername) {
        long now = System.currentTimeMillis();

        var linkCode = discordLinkCodeStore.findByCode(code);
        if (linkCode == null) {
            return ConfirmResult.error("not-found");
        }
        if (linkCode.expiresAt() <= now) {
            discordLinkCodeStore.consumeCode(code);
            return ConfirmResult.error("expired");
        }
        if (!linkCode.playerUuid().equals(playerUuid) || linkCode.playerPid() != playerPid) {
            return ConfirmResult.error("player-mismatch");
        }

        PlayerData data = playerDataRepository.findByUuid(playerUuid);
        if (data == null) {
            return ConfirmResult.error("player-not-found");
        }
        if (isLinked(data) && !data.discordId.equals(discordId)) {
            return ConfirmResult.error("already-linked-other-discord");
        }

        boolean consumed = discordLinkCodeStore.consumeCode(code);
        if (!consumed) {
            return ConfirmResult.error("consume-failed");
        }

        boolean linked = playerDataRepository.updateDiscordLink(playerUuid, discordId, discordUsername, now);
        if (!linked) {
            return ConfirmResult.error("link-failed");
        }

        applyDiscordLink(data, discordId, discordUsername, now);
        sessionService.update(data);

        publishStatusChanged(data, discordId, data.discordUsername, "linked", now);

        return ConfirmResult.success(data);
    }

    private PlayerData playerData(Session session) {
        return session == null ? null : session.data;
    }

    private boolean isLinked(PlayerData data) {
        return data != null && data.discordId != null && !data.discordId.isBlank();
    }

    private void applyDiscordLink(PlayerData data, String discordId, String discordUsername, long linkedAt) {
        if (data == null) {
            return;
        }

        data.discordId = discordId == null ? "" : discordId;
        data.discordUsername = discordUsername == null ? "" : discordUsername;
        data.discordLinkedAt = linkedAt;
    }

    private void clearDiscordState(PlayerData data) {
        applyDiscordLink(data, "", "", 0L);
    }

    private void publishStatusChanged(PlayerData data,
                                      String discordId,
                                      String discordUsername,
                                      String status,
                                      long timestamp) {
        networkService.post(new SocketEvents.DiscordLinkStatusChangedEvent(
                data.uuid,
                data.pid,
                data.nickname,
                discordId,
                discordUsername,
                status,
                config.server,
                timestamp
        ));
    }

    private void publishAdminAccessChanged(String playerUuid,
                                           int playerPid,
                                           String discordId,
                                           String discordUsername,
                                           boolean admin,
                                           String adminSource,
                                           String requestedBy,
                                           String reason) {
        networkService.post(new SocketEvents.DiscordAdminAccessChanged(
                playerUuid,
                playerPid,
                discordId,
                discordUsername,
                admin,
                adminSource,
                requestedBy,
                reason,
                config.server,
                System.currentTimeMillis()
        ));
    }

    private String nextCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
            }
            String code = builder.toString().toUpperCase(Locale.ROOT);
            if (discordLinkCodeStore.findByCode(code) == null) {
                return code;
            }
        }

        throw new IllegalStateException("Failed to generate unique Discord link code");
    }

    public record LinkCodeResult(boolean success, String code, long expiresAt, String errorKey) {
        public static LinkCodeResult success(String code, long expiresAt) {
            return new LinkCodeResult(true, code, expiresAt, "");
        }

        public static LinkCodeResult error(String errorKey) {
            return new LinkCodeResult(false, "", 0L, errorKey);
        }

        public boolean isError(String key) {
            return !success && key.equals(errorKey);
        }

        public long remainingMinutes(long now) {
            return Math.max(1L, (expiresAt - now + 59_999L) / 60_000L);
        }
    }

    public record LinkStatusResult(boolean linked, String discordId, String discordUsername, long linkedAt) {
        public static LinkStatusResult notLinked() {
            return new LinkStatusResult(false, "", "", 0L);
        }

        public static LinkStatusResult linked(String discordId, String discordUsername, long linkedAt) {
            return new LinkStatusResult(true, discordId, discordUsername == null ? "" : discordUsername, linkedAt);
        }

        public String displayName() {
            return discordUsername.isBlank() ? discordId : discordUsername;
        }
    }

    public record ConfirmResult(boolean success, String errorKey, PlayerData playerData) {
        public static ConfirmResult success(PlayerData playerData) {
            return new ConfirmResult(true, "", playerData);
        }

        public static ConfirmResult error(String errorKey) {
            return new ConfirmResult(false, errorKey, null);
        }
    }
}
