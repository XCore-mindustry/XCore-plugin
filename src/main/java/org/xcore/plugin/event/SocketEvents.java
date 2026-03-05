package org.xcore.plugin.event;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.xcore.plugin.model.PlayerData;

public class SocketEvents {
    public interface Event {}

    public static abstract class Response {}
    public static abstract class Request<T> {}

    public record MessageEvent(String authorName, String message, String server) {}

    public record ServerActionEvent(String message, String server) {}

    public record PlayerJoinLeaveEvent(String playerName, String server, Boolean join) {}

    public record GlobalChatEvent(String authorName, String message, String server) {}

    public record DiscordMessageEvent(String authorName, String message, String server) {}

    public record ServerHeartbeatEvent(
            String serverName,
            long discordChannelId,
            int players,
            int maxPlayers,
            String version,
            String host,
            Integer port
    ) implements Event {}

    public record AdminRequestEvent(int pid, String server) {}

    public record AdminRequestConfirmEvent(String uuid, String server) {}

    public record KickBannedPlayer(String uuid, String ip) {}

    public record SyncPlayerData(PlayerData data) {}

    public static class ReloadPlayerDataCache {}

    public record LoadMapsV2(FileURL[] urls, String server) {}

    public record FileURL(String url, String filename) {}

    public record ExecuteCommand(String command, String[] expectServers, boolean isExclusion) {
        public ExecuteCommand(String command, String[] expectServers) {
            this(command, expectServers, false);
        }
    }

    public record PardonPlayer(String uuid) {}

    public record RemoveAdmin(String uuid) {}

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
