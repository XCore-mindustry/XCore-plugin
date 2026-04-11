package org.xcore.plugin.event;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public class SocketEvents {
    public interface Event {}

    public static abstract class Response {}
    public static abstract class Request<T> {}

    public record MessageEvent(String authorName, String message, String server) {}

    public record ServerActionEvent(String message, String server) {}

    public record PlayerJoinLeaveEvent(String playerName, String server, Boolean join) {}

    public record GlobalChatEvent(String authorName, String message, String server) {}

    public record DiscordMessageEvent(String authorName, String message, String server) {}

    public record PrivateMessageEvent(
            String fromUuid,
            int fromPid,
            String fromName,
            String toUuid,
            int toPid,
            String message,
            String server
    ) {}

    public record ServerHeartbeatEvent(
            String serverName,
            long discordChannelId,
            int players,
            int maxPlayers,
            String version,
            String host,
            Integer port
    ) implements Event {}

    public record KickBannedPlayer(String uuid, String ip) {}

    public record PlayerCustomNicknameChanged(String uuid, String customNickname) {}

    public record PlayerActiveBadgeChanged(String uuid, String activeBadge) {}

    public record PlayerBadgeInventoryChanged(String uuid, String activeBadge, Set<String> unlockedBadges) {}

    public record PlayerPasswordReset(String uuid) {}

    public record DiscordLinkCodeCreatedEvent(
            String code,
            String playerUuid,
            int playerPid,
            String playerNickname,
            String server,
            long createdAt,
            long expiresAt
    ) {}

    public record DiscordLinkConfirmEvent(
            String code,
            String playerUuid,
            int playerPid,
            String discordId,
            String discordUsername,
            String server,
            long confirmedAt
    ) {}

    public record DiscordUnlinkEvent(
            String playerUuid,
            int playerPid,
            String discordId,
            String requestedBy,
            String server,
            long requestedAt
    ) {}

    public record DiscordLinkStatusChangedEvent(
            String playerUuid,
            int playerPid,
            String playerNickname,
            String discordId,
            String discordUsername,
            String action,
            String server,
            long occurredAt
    ) {}

    public record DiscordAdminAccessChanged(
            String playerUuid,
            int playerPid,
            String discordId,
            String discordUsername,
            boolean admin,
            String adminSource,
            String requestedBy,
            String reason,
            String server,
            long occurredAt
    ) {}

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
    ) implements Event {}

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
    ) implements Event {}

    public static class ReloadPlayerDataCache {}

    public record LoadMapsV2(FileURL[] urls, String server) {}

    public record FileURL(String url, String filename) {}

    public record ExecuteCommand(String command, String[] expectServers, boolean isExclusion) {
        public ExecuteCommand(String command, String[] expectServers) {
            this(command, expectServers, false);
        }
    }

    public record PardonPlayer(String uuid) {}

    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapsListRequest extends Request<MapsListResponse> {
        public String server;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapsListResponse extends Response {
        public MapEntry[] maps;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapEntry {
        public String name;
        public String fileName;
        public String author;
        public Integer width;
        public Integer height;
        public Long fileSizeBytes;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapRemoveRequest extends Request<MapRemoveResponse> {
        public String server, fileName;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapRemoveResponse extends Response {
        public String result;
    }
}
