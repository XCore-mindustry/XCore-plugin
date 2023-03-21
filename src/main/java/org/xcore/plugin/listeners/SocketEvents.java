package org.xcore.plugin.listeners;

import fr.xpdustry.javelin.JavelinEvent;
import lombok.AllArgsConstructor;

public class SocketEvents {
    @AllArgsConstructor
    public static final class MessageEvent implements JavelinEvent {
        public String authorName, message, server;
    }

    @AllArgsConstructor
    public static final class ServerActionEvent implements JavelinEvent {
        public String message, server;
    }

    @AllArgsConstructor
    public static final class PlayerJoinLeaveEvent implements JavelinEvent {
        public String playerName, server;

        /**
         * true if is join event, false if is leave event
         */
        public boolean join;
    }

    @AllArgsConstructor
    public static final class DiscordMessageEvent implements JavelinEvent {
        public String authorName, message, server;
    }
}
