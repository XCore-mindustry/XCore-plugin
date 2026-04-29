package org.xcore.plugin.event;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public class TransportEvents {
    public interface Event {}

    public interface ServerScopedEvent {
        String server();
    }

    public static abstract class Response {}
    public static abstract class Request<T> {}

    public record MessageEvent(String authorName, String message, String server) implements ServerScopedEvent {}

    public record ServerActionEvent(String message, String server) implements ServerScopedEvent {}

    public record PlayerJoinLeaveEvent(String playerName, String server, Boolean join) implements ServerScopedEvent {}

    public record GlobalChatEvent(String authorName, String message, String server) implements ServerScopedEvent {}

    public record DiscordMessageEvent(String authorName, String message, String server) implements ServerScopedEvent {}

    public record PrivateMessageEvent(
            String fromUuid,
            int fromPid,
            String fromName,
            String toUuid,
            int toPid,
            String message,
            String server
    ) implements ServerScopedEvent {}

    public record KickBannedPlayer(String uuid, String ip) {}

    public record PlayerCustomNicknameChanged(String uuid, String customNickname) {}

    public record PlayerActiveBadgeChanged(String uuid, String activeBadge) {}

    public record PlayerBadgeSymbolColorModeChanged(String uuid, String badgeSymbolColorMode) {}

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

    public record LoadMapsV2(FileURL[] urls, String server) implements ServerScopedEvent {}

    public record FileURL(String url, String filename) {}

    public record ExecuteCommand(String command, String[] expectServers, boolean isExclusion) {
        public ExecuteCommand(String command, String[] expectServers) {
            this(command, expectServers, false);
        }
    }

    public record PardonPlayer(String uuid) {}

    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapsListRequest extends Request<MapsListResponse> implements ServerScopedEvent {
        public String server;

        @Override
        public String server() {
            return server;
        }
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
        public Integer like;
        public Integer dislike;
        public Integer reputation;
        public Double popularity;
        public Double interest;
        public String gameMode;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapRemoveRequest extends Request<MapRemoveResponse> implements ServerScopedEvent {
        public String server, fileName;

        @Override
        public String server() {
            return server;
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapRemoveResponse extends Response {
        public String result;
    }
}
