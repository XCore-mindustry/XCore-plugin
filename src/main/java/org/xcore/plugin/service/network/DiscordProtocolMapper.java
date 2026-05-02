package org.xcore.plugin.service.network;

import org.xcore.plugin.model.PlayerData;
import org.xcore.protocol.generated.messages.discord.DiscordLinkStatusChangedV1Action;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkCodeCreatedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkStatusChangedV1;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ActorRefV1ActorType;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

import java.time.Instant;
import java.util.Objects;

public final class DiscordProtocolMapper {
    private DiscordProtocolMapper() {
    }

    public static DiscordLinkCodeCreatedV1 toLinkCodeCreated(
            String code,
            String playerUuid,
            int playerPid,
            String playerName,
            String server,
            long createdAt,
            long expiresAt
    ) {
        return new DiscordLinkCodeCreatedV1(
                requireNonBlank(code, "code"),
                toPlayerRef(playerUuid, playerPid, playerName),
                requireNonBlank(server, "server"),
                toOccurredAt(createdAt),
                toOccurredAt(expiresAt)
        );
    }

    public static DiscordLinkStatusChangedV1 toLinkStatusChanged(
            PlayerData playerData,
            String discordId,
            String discordUsername,
            String action,
            String server,
            long occurredAt
    ) {
        Objects.requireNonNull(playerData, "playerData must not be null");

        return new DiscordLinkStatusChangedV1(
                toPlayerRef(playerData.uuid, playerData.pid, playerData.nickname),
                toDiscordIdentity(discordId, discordUsername),
                toLinkStatusAction(action),
                requireNonBlank(server, "server"),
                toOccurredAt(occurredAt)
        );
    }

    public static DiscordAdminAccessChangedCommandV1 toAdminAccessChangedCommand(
            String playerUuid,
            int playerPid,
            String playerName,
            String discordId,
            String discordUsername,
            boolean admin,
            String adminSource,
            String requestedBy,
            String reason,
            String server,
            long occurredAt
    ) {
        return new DiscordAdminAccessChangedCommandV1(
                toPlayerRef(playerUuid, playerPid, playerName),
                toDiscordIdentity(discordId, discordUsername),
                admin,
                toSourceActor(adminSource),
                toRequesterActor(requestedBy),
                requireNonBlank(reason, "reason"),
                requireNonBlank(server, "server"),
                toOccurredAt(occurredAt)
        );
    }

    public static org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1 toUnlinkCommand(
            String playerUuid,
            int playerPid,
            String playerName,
            String discordId,
            String discordUsername,
            String requestedBy,
            String server,
            long requestedAt
    ) {
        return new org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1(
                toPlayerRef(playerUuid, playerPid, playerName),
                toDiscordIdentity(discordId, discordUsername),
                toRequesterActor(requestedBy),
                requireNonBlank(server, "server"),
                toOccurredAt(requestedAt)
        );
    }

    private static PlayerRefV1 toPlayerRef(String playerUuid, Integer playerPid, String playerName) {
        return new PlayerRefV1(
                requireNonBlank(playerUuid, "playerUuid"),
                normalizeOptionalPid(playerPid),
                requirePlayerName(playerName),
                null
        );
    }

    private static DiscordIdentityRefV1 toDiscordIdentity(String discordId, String discordUsername) {
        return new DiscordIdentityRefV1(
                requireNonBlank(discordId, "discordId"),
                normalizeOptional(discordUsername)
        );
    }

    private static ActorRefV1 toSourceActor(String adminSource) {
        String sourceName = requireNonBlank(adminSource, "adminSource");
        return new ActorRefV1(sourceName, null, resolveSourceActorType(sourceName));
    }

    private static ActorRefV1 toRequesterActor(String requestedBy) {
        return new ActorRefV1(requireNonBlank(requestedBy, "requestedBy"), null, ActorRefV1ActorType.SYSTEM);
    }

    private static ActorRefV1ActorType resolveSourceActorType(String adminSource) {
        return switch (adminSource) {
            case "DISCORD_ROLE" -> ActorRefV1ActorType.SYSTEM;
            case "NONE" -> ActorRefV1ActorType.SYSTEM;
            default -> ActorRefV1ActorType.SYSTEM;
        };
    }

    private static DiscordLinkStatusChangedV1Action toLinkStatusAction(String action) {
        return switch (requireNonBlank(action, "action").toLowerCase()) {
            case "linked" -> DiscordLinkStatusChangedV1Action.LINKED;
            case "unlinked" -> DiscordLinkStatusChangedV1Action.UNLINKED;
            default -> throw new IllegalArgumentException("Unsupported discord link status action: " + action);
        };
    }

    private static String requirePlayerName(String playerName) {
        String normalized = normalizeOptional(playerName);
        return normalized == null ? "Unknown" : normalized;
    }

    private static String requireNonBlank(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static Integer normalizeOptionalPid(Integer pid) {
        return pid == null || pid < 0 ? null : pid;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String toOccurredAt(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).toString();
    }
}
