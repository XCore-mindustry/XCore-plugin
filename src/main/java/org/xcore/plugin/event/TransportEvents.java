package org.xcore.plugin.event;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public class TransportEvents {
    public interface Event {}

    public interface ServerScopedEvent {
        String server();
    }

    public record KickBannedPlayer(String uuid, String ip) {}

    public record PlayerBadgeInventoryChanged(String uuid, String activeBadge, Set<String> unlockedBadges) {}

    public record PlayerPasswordReset(String uuid) {}

    public record VoteKickParticipant(
            String name,
            Integer pid,
            String discordId
    ) {}

    public record VoteKickEvent(
            String targetName,
            Integer targetPid,
            String targetUuid,
            String starterName,
            Integer starterPid,
            String starterDiscordId,
            String reason,
            List<VoteKickParticipant> votesFor,
            List<VoteKickParticipant> votesAgainst,
            String status,
            String server,
            long occurredAt
    ) implements Event, ServerScopedEvent {}

    public record ModerationAuditAppendedEvent(
            String auditId,
            String action,
            String targetUuid,
            Integer targetPid,
            String targetName,
            String actorType,
            String actorId,
            String actorName,
            String reason,
            Long durationMs,
            Instant expiresAt,
            String relatedAuditId,
            String server,
            Instant occurredAt
    ) implements Event, ServerScopedEvent {}

    public static class ReloadPlayerDataCache {}

    public record ExecuteCommand(String command, String[] expectServers, boolean isExclusion) {
        public ExecuteCommand(String command, String[] expectServers) {
            this(command, expectServers, false);
        }
    }

    public record PardonPlayer(String uuid) {}

}
