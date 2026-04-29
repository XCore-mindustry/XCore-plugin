package org.xcore.plugin.service.network;

import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;
import org.xcore.plugin.model.Punishment;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationAuditAppendedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationKickBannedCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationMuteCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationPardonCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationVoteKickCreatedV1;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ExpirationInfoV1;
import org.xcore.protocol.generated.shared.ModerationTargetRefV1;
import org.xcore.protocol.generated.shared.PlayerCommandTargetV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;
import org.xcore.protocol.generated.shared.VoteKickParticipantV1;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModerationProtocolMapper {
    private ModerationProtocolMapper() {
    }

    public static ModerationBanCreatedV1 toBanCreated(BanData ban, String server, Instant occurredAt) {
        return new ModerationBanCreatedV1(
                new PlayerRefV1(ban.uuid, null, ban.name, normalizeOptional(ban.ip)),
                new ActorRefV1(resolveActorName(ban.adminName), normalizeOptional(ban.adminDiscordId), resolveActorType(ban.adminDiscordId)),
                resolveReason(ban.reason),
                toExpirationInfo(ban),
                normalizeOptional(server),
                occurredAt.toString()
        );
    }

    public static ModerationMuteCreatedV1 toMuteCreated(MuteData mute, String server, Instant occurredAt) {
        return new ModerationMuteCreatedV1(
                toPlayerRef(mute),
                toActorRef(mute),
                resolveReason(mute.reason),
                toExpirationInfo(mute),
                normalizeOptional(server),
                toOccurredAt(occurredAt)
        );
    }

    public static ModerationVoteKickCreatedV1 toVoteKickCreated(
            String targetUuid,
            Integer targetPid,
            String targetName,
            String starterName,
            Integer starterPid,
            String starterDiscordId,
            String reason,
            List<VoteKickParticipantV1> votesFor,
            List<VoteKickParticipantV1> votesAgainst,
            String server,
            Instant occurredAt
    ) {
        return new ModerationVoteKickCreatedV1(
                new PlayerRefV1(requireNonBlank(targetUuid, "targetUuid"), normalizeOptionalPid(targetPid), requirePlayerName(targetName), null),
                new ActorRefV1(resolveActorName(starterName), normalizeOptional(starterDiscordId), resolveActorType(starterDiscordId)),
                resolveReason(reason),
                votesFor == null ? List.of() : List.copyOf(votesFor),
                votesAgainst == null ? List.of() : List.copyOf(votesAgainst),
                normalizeOptional(server),
                toOccurredAt(occurredAt)
        );
    }

    public static VoteKickParticipantV1 toVoteKickParticipant(String name, Integer pid, String discordId) {
        return new VoteKickParticipantV1(resolveActorName(name), normalizeOptionalPid(pid), normalizeOptional(discordId));
    }

    public static ModerationKickBannedCommandV1 toKickBannedCommand(
            String playerUuid,
            Integer playerPid,
            String playerName,
            String ip,
            String server,
            Instant requestedAt
    ) {
        return new ModerationKickBannedCommandV1(
                new PlayerCommandTargetV1(
                        normalizeOptional(playerUuid),
                        normalizeOptionalPid(playerPid),
                        normalizeOptional(playerName),
                        normalizeOptional(ip)
                ),
                requireNonBlank(server, "server"),
                toOccurredAt(requestedAt)
        );
    }

    public static ModerationPardonCommandV1 toPardonCommand(
            String playerUuid,
            Integer playerPid,
            String playerName,
            String ip,
            String server,
            Instant requestedAt
    ) {
        return new ModerationPardonCommandV1(
                new PlayerCommandTargetV1(
                        normalizeOptional(playerUuid),
                        normalizeOptionalPid(playerPid),
                        normalizeOptional(playerName),
                        normalizeOptional(ip)
                ),
                requireNonBlank(server, "server"),
                toOccurredAt(requestedAt)
        );
    }

    public static ModerationAuditAppendedV1 toAuditAppended(AuditRecord record, String server) {
        Objects.requireNonNull(record, "record must not be null");

        return new ModerationAuditAppendedV1(
                toAuditEntryType(record.action),
                new ModerationTargetRefV1(
                        normalizeOptional(record.target == null ? null : record.target.uuid),
                        record.target == null ? null : normalizeOptionalPid(record.target.pid),
                        normalizeOptional(record.target == null ? null : record.target.nameSnapshot),
                        normalizeOptional(record.target == null ? null : record.target.ipSnapshot)
                ),
                new ActorRefV1(
                        resolveActorName(record.actor == null ? null : record.actor.nameSnapshot),
                        normalizeOptional(record.actor == null ? null : record.actor.discordId),
                        toProtocolActorType(record.actor == null ? null : record.actor.type)
                ),
                resolveReason(record.reason),
                normalizeOptional(resolveAuditServer(record, server)),
                toOccurredAt(record.occurredAt),
                toAuditDetails(record)
        );
    }

    private static PlayerRefV1 toPlayerRef(Punishment punishment) {
        return new PlayerRefV1(
                requireNonBlank(punishment.uuid, "playerUuid"),
                null,
                requirePlayerName(punishment.name),
                punishment instanceof BanData banData ? normalizeOptional(banData.ip) : null
        );
    }

    private static ActorRefV1 toActorRef(Punishment punishment) {
        return new ActorRefV1(
                resolveActorName(punishment.adminName),
                normalizeOptional(punishment.adminDiscordId),
                resolveActorType(punishment.adminDiscordId)
        );
    }

    private static ExpirationInfoV1 toExpirationInfo(Punishment punishment) {
        if (punishment.expireDate == null) {
            return new ExpirationInfoV1(null, true);
        }
        return new ExpirationInfoV1(punishment.expireDate.toString(), false);
    }

    private static Map<String, Object> toAuditDetails(AuditRecord record) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        if (record.details != null) {
            putIfNotNull(details, "durationMs", record.details.durationMs);
            putIfNotNull(details, "expiresAt", record.details.expiresAt == null ? null : record.details.expiresAt.toString());
            putIfNotNull(details, "visibility", normalizeOptional(record.details.visibility));
            if (record.details.extra != null) {
                record.details.extra.forEach((key, value) -> putIfNotNull(details, key, normalizeOptional(value)));
            }
        }
        putIfNotNull(details, "relatedAuditId", normalizeOptional(record.relatedAuditId));
        return details.isEmpty() ? null : Map.copyOf(details);
    }

    private static void putIfNotNull(Map<String, Object> details, String key, Object value) {
        if (value != null) {
            details.put(key, value);
        }
    }

    private static String resolveAuditServer(AuditRecord record, String server) {
        String auditServer = record.origin == null ? null : normalizeOptional(record.origin.serverId);
        return auditServer != null ? auditServer : server;
    }

    private static String toAuditEntryType(AuditAction action) {
        if (action == null) {
            return "other";
        }
        return switch (action) {
            case BAN -> "ban";
            case MUTE -> "mute";
            case UNBAN, UNMUTE -> "pardon";
            default -> "other";
        };
    }

    private static String toProtocolActorType(AuditActorType actorType) {
        if (actorType == null) {
            return "system";
        }
        return switch (actorType) {
            case DISCORD_USER -> "discord";
            case PLAYER_ADMIN -> "player_admin";
            case SERVER_CONSOLE -> "server_console";
            case SYSTEM -> "system";
        };
    }

    private static String resolveActorName(String actorName) {
        String normalized = normalizeOptional(actorName);
        return normalized == null ? "Unknown" : normalized;
    }

    private static String resolveActorType(String actorDiscordId) {
        return normalizeOptional(actorDiscordId) == null ? "unknown" : "discord";
    }

    private static String resolveReason(String reason) {
        String normalized = normalizeOptional(reason);
        return normalized == null ? "Not Specified" : normalized;
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

    private static String toOccurredAt(Instant occurredAt) {
        return Objects.requireNonNull(occurredAt, "occurredAt must not be null").toString();
    }

    private static Integer normalizeOptionalPid(Integer pid) {
        return pid == null || pid < 0 ? null : pid;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
