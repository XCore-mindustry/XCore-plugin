package org.xcore.plugin.service;

import com.google.gson.Gson;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.AccountOperationRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAccountOperationRequestV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAccountOperationResponseV1;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.xcore.protocol.generated.messages.discord.DiscordAccountOperationRequestV1Action.LINK;
import static org.xcore.protocol.generated.messages.discord.DiscordAccountOperationRequestV1Action.UNLINK;
import static org.xcore.protocol.generated.messages.discord.DiscordAccountOperationResponseV1Status.*;

@Singleton
public class DiscordAccountOperationService {
    private static final long MAX_LIFETIME_MS = 10 * 60_000L;
    private final AccountOperationRepository receipts;
    private final DiscordLinkService links;
    private final PlayerDataRepository players;
    private final TomlXcoreConfig config;
    private final Gson gson;

    public DiscordAccountOperationService(AccountOperationRepository receipts, DiscordLinkService links,
                                         PlayerDataRepository players, TomlXcoreConfig config, @Named("redis") Gson gson) {
        this.receipts = receipts;
        this.links = links;
        this.players = players;
        this.config = config;
        this.gson = gson;
    }

    public DiscordAccountOperationResponseV1 execute(DiscordAccountOperationRequestV1 request, String requester) {
        if (!"web".equals(requester) && !"discord-bot".equals(requester)) return response(request, REJECTED, "requester-not-allowed", null);
        if (!valid(request)) return response(request, REJECTED, "invalid-request", null);
        if (!config.server.name.equals(request.server())) return response(request, REJECTED, "wrong-server", null);
        try {
            String operationId = UUID.fromString(request.operationId()).toString();
            var payload = request.toPayload();
            payload.remove("operationId");
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((requester + "\n" + gson.toJson(payload)).getBytes(StandardCharsets.UTF_8)));
            boolean readOnly = players.isReadOnly();
            var claim = readOnly ? receipts.find(operationId, fingerprint) : receipts.claim(operationId, fingerprint);
            if (claim == null) return response(request, REJECTED, "read-only", null);
            if (claim.conflict()) return response(request, REJECTED, "operation-id-conflict", null);
            if (!claim.acquired()) {
                if (claim.response() == null) return response(request, UNKNOWN, "operation-in-progress", null);
                var known = gson.fromJson(claim.response(), DiscordAccountOperationResponseV1.class);
                return response(request, known.status(), known.reason(), known.linkedAt());
            }
            var result = perform(request);
            if (!receipts.complete(operationId, fingerprint, gson.toJson(result.toPayload()))) {
                return response(request, UNKNOWN, "receipt-not-confirmed", null);
            }
            return result;
        } catch (Exception exception) {
            // Redis/Mongo timeouts can occur after their write took effect. Keep the pending receipt.
            return response(request, UNKNOWN, "storage-outcome-unknown", null);
        }
    }

    private DiscordAccountOperationResponseV1 perform(DiscordAccountOperationRequestV1 request) {
        long now = System.currentTimeMillis();
        if (request.issuedAt() > now + 5_000L || request.expiresAt() <= now
                || request.expiresAt() <= request.issuedAt() || request.expiresAt() - request.issuedAt() > MAX_LIFETIME_MS) {
            return response(request, REJECTED, "request-expired", null);
        }
        if (request.action() == LINK) {
            var result = links.confirmLink(request.code(), request.player().playerUuid(), request.player().playerPid(),
                    request.discord().discordId(), request.discord().discordUsername(), request.expiresAt());
            return result.success() ? response(request, SUCCESS, null, result.playerData().discordLinkedAt)
                    : response(request, REJECTED, result.errorKey(), null);
        }
        boolean unlinked = links.unlink(request.player().playerUuid(), request.discord().discordId(), request.expectedLinkedAt(), request.expiresAt());
        return response(request, unlinked ? SUCCESS : REJECTED, unlinked ? null : "binding-mismatch", null);
    }

    private boolean valid(DiscordAccountOperationRequestV1 request) {
        try {
            if (!UUID.fromString(request.operationId()).toString().equalsIgnoreCase(request.operationId())) return false;
            if (request.player() == null || request.player().playerUuid() == null || request.player().playerUuid().isBlank()
                    || request.discord() == null || request.discord().discordId() == null || request.discord().discordId().isBlank()
                    || request.issuedAt() < 0 || request.expiresAt() < 0) return false;
            return request.action() == LINK ? request.code() != null && !request.code().isBlank()
                    && request.player().playerPid() != null && request.player().playerPid() > 0
                    : request.action() == UNLINK && request.expectedLinkedAt() != null && request.expectedLinkedAt() >= 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private DiscordAccountOperationResponseV1 response(DiscordAccountOperationRequestV1 request,
            org.xcore.protocol.generated.messages.discord.DiscordAccountOperationResponseV1Status status, String reason, Long linkedAt) {
        return new DiscordAccountOperationResponseV1(config.server.name, request.operationId(), status, reason, linkedAt);
    }
}
