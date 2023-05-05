package org.xcore.plugin.listeners;

import org.xcore.plugin.utils.models.PlayerData;

public class SocketEvents {
    public record MessageEvent(String authorName, String message, String server) {
    }

    public record ServerActionEvent(String message, String server) {
    }

    public record PlayerJoinLeaveEvent(String playerName, String server, Boolean join) {
    }

    public record DiscordMessageEvent(String authorName, String message, String server) {
    }

    public record AdminRequestEvent(String uuid, String name, String server) {
    }

    public record AdminRequestConfirmEvent(String uuid, String server) {
    }

    public record SyncPlayerData(PlayerData data) {
    }
}