package org.xcore.plugin.listeners;

import fr.xpdustry.javelin.JavelinEvent;
import org.xcore.plugin.utils.models.PlayerData;

public class SocketEvents {
    public record MessageEvent(String authorName, String message, String server) implements JavelinEvent {
    }

    public record ServerActionEvent(String message, String server) implements JavelinEvent {
    }

    public record PlayerJoinLeaveEvent(String playerName, String server, Boolean join) implements JavelinEvent {
    }

    public record DiscordMessageEvent(String authorName, String message, String server) implements JavelinEvent {
    }

    public record AdminRequestEvent(String uuid, String name, String server) implements JavelinEvent {
    }

    public record AdminRequestConfirmEvent(String uuid, String server) implements JavelinEvent {
    }

    public record SyncPlayerData(PlayerData data) implements JavelinEvent {
    }
}