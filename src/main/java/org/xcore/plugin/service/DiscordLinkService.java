package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.DiscordLinkCodeRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.DiscordLinkCode;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.security.SecureRandom;
import java.util.Locale;

@Singleton
public class DiscordLinkService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final long CODE_TTL_MILLIS = 10 * 60 * 1000L;

    private final DiscordLinkCodeRepository discordLinkCodeRepository;
    private final PlayerDataRepository playerDataRepository;
    private final SessionService sessionService;
    private final NetworkService networkService;
    private final Config config;
    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    public DiscordLinkService(DiscordLinkCodeRepository discordLinkCodeRepository,
                              PlayerDataRepository playerDataRepository,
                              SessionService sessionService,
                              NetworkService networkService,
                              Config config) {
        this.discordLinkCodeRepository = discordLinkCodeRepository;
        this.playerDataRepository = playerDataRepository;
        this.sessionService = sessionService;
        this.networkService = networkService;
        this.config = config;
    }

    public LinkCodeResult createCode(Session session) {
        if (session == null || session.data == null) {
            return LinkCodeResult.error("session-missing");
        }

        PlayerData data = session.data;
        if (data.discordId != null && !data.discordId.isBlank()) {
            return LinkCodeResult.error("already-linked");
        }

        discordLinkCodeRepository.invalidatePendingByPlayerUuid(data.uuid);

        String code = nextCode();
        long now = System.currentTimeMillis();
        long expiresAt = now + CODE_TTL_MILLIS;

        DiscordLinkCode linkCode = DiscordLinkCode.builder()
                .code(code)
                .playerUuid(data.uuid)
                .playerPid(data.pid)
                .playerNickname(data.nickname)
                .server(config.server)
                .createdModelTime(now)
                .editModelTime(now)
                .expiresAt(expiresAt)
                .status("pending")
                .build();

        if (!discordLinkCodeRepository.save(linkCode)) {
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
        if (session == null || session.data == null) {
            return LinkCodeResult.error("session-missing");
        }

        PlayerData data = session.data;
        if (data.discordId != null && !data.discordId.isBlank()) {
            return LinkCodeResult.error("already-linked");
        }

        long now = System.currentTimeMillis();
        for (var pending : discordLinkCodeRepository.findPendingByPlayerUuid(data.uuid)) {
            if (pending.isExpired(now)) {
                discordLinkCodeRepository.expireCode(pending.code);
                continue;
            }

            return LinkCodeResult.success(pending.code, pending.expiresAt);
        }

        return createCode(session);
    }

    public LinkStatusResult status(Session session) {
        if (session == null || session.data == null) {
            return LinkStatusResult.notLinked();
        }

        PlayerData data = session.data;
        if (data.discordId == null || data.discordId.isBlank()) {
            return LinkStatusResult.notLinked();
        }

        return LinkStatusResult.linked(data.discordId, data.discordUsername, data.discordLinkedAt);
    }

    public boolean unlink(Session session) {
        if (session == null || session.data == null) {
            return false;
        }

        PlayerData data = session.data;
        if (data.discordId == null || data.discordId.isBlank()) {
            return false;
        }

        String discordId = data.discordId;
        boolean updated = playerDataRepository.clearDiscordLink(data.uuid);
        if (!updated) {
            return false;
        }

        data.discordId = "";
        data.discordUsername = "";
        data.discordLinkedAt = 0L;
        networkService.post(new SocketEvents.DiscordLinkStatusChangedEvent(
                data.uuid,
                data.pid,
                data.nickname,
                discordId,
                "",
                "unlinked",
                config.server,
                System.currentTimeMillis()
        ));
        return true;
    }

    public boolean unlink(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }

        PlayerData data = playerDataRepository.findByUuid(playerUuid);
        if (data == null || data.discordId == null || data.discordId.isBlank()) {
            return false;
        }

        String discordId = data.discordId;
        boolean updated = playerDataRepository.clearDiscordLink(playerUuid);
        if (!updated) {
            return false;
        }

        var session = sessionService.get(playerUuid);
        if (session != null && session.data != null) {
            session.data.discordId = "";
            session.data.discordUsername = "";
            session.data.discordLinkedAt = 0L;
        }

        networkService.post(new SocketEvents.DiscordLinkStatusChangedEvent(
                data.uuid,
                data.pid,
                data.nickname,
                discordId,
                "",
                "unlinked",
                config.server,
                System.currentTimeMillis()
        ));
        return true;
    }

    public ConfirmResult confirmLink(String code,
                                     String playerUuid,
                                     int playerPid,
                                     String discordId,
                                     String discordUsername) {
        long now = System.currentTimeMillis();

        DiscordLinkCode linkCode = discordLinkCodeRepository.findByCode(code);
        if (linkCode == null) {
            return ConfirmResult.error("not-found");
        }
        if (!linkCode.isPending()) {
            return ConfirmResult.error("already-consumed");
        }
        if (linkCode.isExpired(now)) {
            discordLinkCodeRepository.expireCode(code);
            return ConfirmResult.error("expired");
        }
        if (!linkCode.playerUuid.equals(playerUuid) || linkCode.playerPid != playerPid) {
            return ConfirmResult.error("player-mismatch");
        }

        PlayerData data = playerDataRepository.findByUuid(playerUuid);
        if (data == null) {
            return ConfirmResult.error("player-not-found");
        }
        if (data.discordId != null && !data.discordId.isBlank() && !data.discordId.equals(discordId)) {
            return ConfirmResult.error("already-linked-other-discord");
        }

        boolean consumed = discordLinkCodeRepository.consumeCode(code, discordId, now);
        if (!consumed) {
            return ConfirmResult.error("consume-failed");
        }

        boolean linked = playerDataRepository.updateDiscordLink(playerUuid, discordId, discordUsername, now);
        if (!linked) {
            return ConfirmResult.error("link-failed");
        }

        data.discordId = discordId;
        data.discordUsername = discordUsername == null ? "" : discordUsername;
        data.discordLinkedAt = now;
        sessionService.update(data);

        networkService.post(new SocketEvents.DiscordLinkStatusChangedEvent(
                data.uuid,
                data.pid,
                data.nickname,
                discordId,
                data.discordUsername,
                "linked",
                config.server,
                now
        ));

        return ConfirmResult.success(data);
    }

    private String nextCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
            }
            String code = builder.toString().toUpperCase(Locale.ROOT);
            if (discordLinkCodeRepository.findByCode(code) == null) {
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
    }

    public record LinkStatusResult(boolean linked, String discordId, String discordUsername, long linkedAt) {
        public static LinkStatusResult notLinked() {
            return new LinkStatusResult(false, "", "", 0L);
        }

        public static LinkStatusResult linked(String discordId, String discordUsername, long linkedAt) {
            return new LinkStatusResult(true, discordId, discordUsername == null ? "" : discordUsername, linkedAt);
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
