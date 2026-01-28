package org.xcore.plugin.event;

import com.ospx.sock.EventBus.Request;
import com.ospx.sock.EventBus.Response;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.xcore.plugin.model.PlayerData;

public class SocketEvents {

    public record MessageEvent(String authorName, String message, String server) {}

    public record ServerActionEvent(String message, String server) {}

    public record PlayerJoinLeaveEvent(String playerName, String server, Boolean join) {}

    public record GlobalChatEvent(String authorName, String message, String server) {}

    public record DiscordMessageEvent(String authorName, String message, String server) {}

    public record AdminRequestEvent(int pid, String server) {}

    public record AdminRequestConfirmEvent(String uuid, String server) {}

    public record KickBannedPlayer(String uuid, String ip) {}

    public record SyncPlayerData(PlayerData data) {}

    public static class ReloadPlayerDataCache {}

    public record LoadMapsV2(FileURL[] urls, String server) {}

    public record FileURL(String url, String filename) {}

    public record ExecuteCommand(String command, String[] expectServers) {}

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
        public String[] maps;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapRemoveRequest extends Request<MapRemoveResponse> {
        public String server, map;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapRemoveResponse extends Response {
        public String result;
    }
}