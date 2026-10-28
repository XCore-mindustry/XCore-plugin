package org.xcore.plugin.service;

import org.xcore.plugin.concurrent.Async;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1;
import org.xcore.plugin.service.network.DiscordProtocolMapper;
import org.xcore.plugin.service.network.RedisDiscordLinkCodeStore;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.security.SecureRandom;


@Singleton
public class DiscordLinkService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final long CODE_TTL_MILLIS = 10 * 60 * 1000L;

    private final RedisDiscordLinkCodeStore discordLinkCodeStore;
    private final PlayerDataRepository playerDataRepository;
    private final SessionService sessionService;
    private final NetworkService networkService;
    private final TomlXcoreConfig config;
    private final Async async;
    private final java.util.concurrent.ConcurrentMap<String, Object> refreshes = new java.util.concurrent.ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    public DiscordLinkService(RedisDiscordLinkCodeStore discordLinkCodeStore,
                              PlayerDataRepository playerDataRepository,
                              SessionService sessionService,
                               NetworkService networkService,
                               TomlXcoreConfig config, Async async) {
        this.discordLinkCodeStore = discordLinkCodeStore;
        this.playerDataRepository = playerDataRepository;
        this.sessionService = sessionService;
        this.networkService = networkService;
        this.config = config;
        this.async = async;
    }

    public LinkCodeResult createCode(Session session) {
        return createCode(playerData(session));
    }

    private LinkCodeResult createCode(PlayerData data) {
        if (data == null) {
            return LinkCodeResult.error("session-missing");
        }

        if (isLinked(data)) {
            return LinkCodeResult.error("already-linked");
        }

        if (playerDataRepository.isReadOnly()) return LinkCodeResult.error("read-only");
        PlayerData authoritative = playerDataRepository.findByUuid(data.uuid);
        if (isLinked(authoritative)) return LinkCodeResult.error("already-linked");

        String code = nextCode();
        long now = System.currentTimeMillis();
        long expiresAt = now + CODE_TTL_MILLIS;

        var linkCode = new RedisDiscordLinkCodeStore.LinkCodePayload(
                code,
                data.uuid,
                data.pid,
                data.nickname,
                config.server.name,
                now,
                expiresAt
        );

        for (int attempt = 0; !discordLinkCodeStore.store(linkCode); attempt++) {
            if (attempt >= 9) return LinkCodeResult.error("save-failed");
            code = nextCode();
            linkCode = new RedisDiscordLinkCodeStore.LinkCodePayload(code, data.uuid, data.pid, data.nickname,
                    config.server.name, now, expiresAt);
        }

        networkService.post(DiscordProtocolMapper.toLinkCodeCreated(
                code,
                data.uuid,
                data.pid,
                data.nickname,
                config.server.name,
                now,
                expiresAt
        ));

        return LinkCodeResult.success(code, expiresAt);
    }

    public LinkCodeResult getOrCreateActiveCode(Session session) {
        return getOrCreateActiveCode(playerData(session));
    }

    private LinkCodeResult getOrCreateActiveCode(PlayerData data) {
        if (data == null) {
            return LinkCodeResult.error("session-missing");
        }

        if (isLinked(data)) {
            return LinkCodeResult.error("already-linked");
        }

        long now = System.currentTimeMillis();
        var pending = discordLinkCodeStore.findPendingByPlayerUuid(data.uuid);
        if (pending != null) {
            if (pending.expiresAt() > now) return LinkCodeResult.success(pending.code(), pending.expiresAt());
        }

        return createCode(data);
    }

    public void requestCode(Session session, boolean regenerate, java.util.function.Consumer<LinkCodeResult> callback) {
        var source = session.data;
        var snapshot = PlayerData.builder().uuid(source.uuid).pid(source.pid).nickname(source.nickname)
                .discordId(source.discordId).build();
        async.supply(() -> regenerate ? createCode(snapshot) : getOrCreateActiveCode(snapshot)).thenMain((result, error) -> {
            if (sessionService.get(snapshot.uuid) == session) callback.accept(error == null ? result : LinkCodeResult.error("save-failed"));
        });
    }

    public void requestUnlink(Session session, java.util.function.Consumer<Boolean> callback) {
        String uuid = session.data.uuid;
        String owner = session.data.discordId;
        long linkedAt = session.data.discordLinkedAt;
        async.supply(() -> unlink(uuid, owner, linkedAt)).thenMain((result, error) -> {
            if (sessionService.get(uuid) != session) return;
            boolean success = error == null && Boolean.TRUE.equals(result);
            if (success && owner.equals(session.data.discordId) && linkedAt == session.data.discordLinkedAt) clearDiscordState(session.data);
            callback.accept(success);
        });
    }

    public LinkStatusResult status(Session session) {
        PlayerData data = playerData(session);
        if (data == null || !isLinked(data)) {
            return LinkStatusResult.notLinked();
        }

        return LinkStatusResult.linked(data.discordId, data.discordUsername, data.discordLinkedAt);
    }

    public boolean unlink(Session session) {
        PlayerData cached = playerData(session);
        return cached != null && unlink(cached.uuid, cached.discordId, cached.discordLinkedAt);
    }

    public boolean unlink(String playerUuid, String expectedDiscordId, long expectedLinkedAt) {
        return unlink(playerUuid, expectedDiscordId, expectedLinkedAt, Long.MAX_VALUE);
    }

    public boolean unlink(String playerUuid, String expectedDiscordId, long expectedLinkedAt, long deadline) {
        if (playerDataRepository.isReadOnly()) return false;
        PlayerData data = playerDataRepository.findByUuid(playerUuid);
        if (!isLinked(data) || !data.discordId.equals(expectedDiscordId) || data.discordLinkedAt != expectedLinkedAt) return false;
        if (System.currentTimeMillis() >= deadline) return false;
        if (!playerDataRepository.clearDiscordLink(playerUuid, expectedDiscordId, expectedLinkedAt)) return false;
        refreshCachedBinding(playerUuid);
        clearDiscordState(data);
        publishStatusChanged(data, expectedDiscordId, "", "unlinked", System.currentTimeMillis());
        return true;
    }

    /** Legacy commands lack a generation: only accept an intent issued after the current binding. */
    public boolean unlinkLegacy(String playerUuid, String expectedDiscordId, long issuedAt) {
        PlayerData data = playerDataRepository.findByUuid(playerUuid);
        long now = System.currentTimeMillis();
        return issuedAt <= now + 5_000L && now - issuedAt <= CODE_TTL_MILLIS
                && isLinked(data) && issuedAt >= data.discordLinkedAt
                && unlink(playerUuid, expectedDiscordId, data.discordLinkedAt);
    }

    public ConfirmResult confirmLink(String code,
                                     String playerUuid,
                                     int playerPid,
                                     String discordId,
                                     String discordUsername) {
        return confirmLink(code, playerUuid, playerPid, discordId, discordUsername, Long.MAX_VALUE);
    }

    public ConfirmResult confirmLink(String code, String playerUuid, int playerPid, String discordId,
                                     String discordUsername, long deadline) {
        if (playerDataRepository.isReadOnly()) return ConfirmResult.error("read-only");
        if (discordId == null || discordId.isBlank()) return ConfirmResult.error("discord-missing");
        long now = System.currentTimeMillis();

        var linkCode = discordLinkCodeStore.findByCode(code);
        if (linkCode == null) {
            return ConfirmResult.error("not-found");
        }
        if (!config.server.name.equals(linkCode.server())) return ConfirmResult.error("wrong-server");
        if (linkCode.expiresAt() <= now) return ConfirmResult.error("expired");
        if (!linkCode.playerUuid().equals(playerUuid) || linkCode.playerPid() != playerPid) {
            return ConfirmResult.error("player-mismatch");
        }

        PlayerData data = playerDataRepository.findByUuid(playerUuid);
        if (data == null) {
            return ConfirmResult.error("player-not-found");
        }
        if (data.pid != playerPid) return ConfirmResult.error("player-mismatch");
        if (isLinked(data)) return ConfirmResult.error("already-linked-other-discord");

        if (System.currentTimeMillis() >= Math.min(linkCode.expiresAt(), deadline)) return ConfirmResult.error("expired");
        var claimed = discordLinkCodeStore.claimCode(linkCode);
        if (claimed == null) {
            return ConfirmResult.error("consume-failed");
        }

        PlayerData linked = playerDataRepository.updateDiscordLink(playerUuid, playerPid, discordId, discordUsername, now);
        if (linked == null) {
            return ConfirmResult.error("link-failed");
        }

        refreshCachedBinding(playerUuid);
        publishStatusChanged(linked, discordId, linked.discordUsername, "linked", linked.discordLinkedAt);
        return ConfirmResult.success(linked);
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
        networkService.post(DiscordProtocolMapper.toLinkStatusChanged(
                data,
                discordId,
                discordUsername,
                status,
                config.server.name,
                timestamp
        ));
    }

    public DiscordUnlinkCommandV1 toUnlinkCommand(PlayerData data, String requestedBy) {
        // DiscordLinkService only has requester name strings here, so actor metadata falls back to SYSTEM.
        var actor = DiscordProtocolMapper.toRequesterActor(requestedBy);
        return DiscordProtocolMapper.toUnlinkCommand(
                data.uuid,
                data.pid,
                data.nickname,
                data.discordId,
                data.discordUsername,
                actor,
                config.server.name,
                System.currentTimeMillis()
        );
    }

    private String nextCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) builder.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        return builder.toString();
    }

    public void refreshCachedBinding(String uuid) {
        Object token = new Object();
        refreshes.put(uuid, token);
        // Read on storage executor, then patch only binding fields on the game thread.
        async.supply(() -> playerDataRepository.findByUuid(uuid)).thenMain((latest, error) -> {
            if (!refreshes.remove(uuid, token) || error != null) return;
            var session = sessionService.get(uuid);
            if (latest != null && session != null && session.data != null) {
                applyDiscordLink(session.data, latest.discordId, latest.discordUsername, latest.discordLinkedAt);
            }
        });
    }

    public String websiteLinkUrl(String code) {
        String base = config.discord.websiteLinkUrl;
        if (base == null || base.isBlank()) return "";
        return base + (base.contains("?") ? "&" : "?") + "code="
                + java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8) + "&server="
                + java.net.URLEncoder.encode(config.server.name, java.nio.charset.StandardCharsets.UTF_8);
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
