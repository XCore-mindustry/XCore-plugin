package org.xcore.plugin.listeners;

import fr.xpdustry.javelin.JavelinEvent;

public class SocketEvents {
    public static final class MessageEvent implements JavelinEvent {
        public String authorName;
        public String message;
        public String server;

        public MessageEvent(String author, String message, String server) {
            this.authorName = author;
            this.message = message;
            this.server = server;
        }
    }

    public static final class ServerActionEvent implements JavelinEvent {
        public String message;
        public String server;

        ServerActionEvent(String message, String server) {
            this.message = message;
            this.server = server;
        }
    }

    public static final class PlayerJoinLeaveEvent implements JavelinEvent {
        public String playerName;
        public String server;

        /** true if is join event, false if is leave event */
        public boolean join;

        public PlayerJoinLeaveEvent(String playerName, String server, Boolean join) {
            this.playerName = playerName;
            this.server = server;
            this.join = join;
        }
    }

    public static final class DiscordMessageEvent implements JavelinEvent {
        public String authorName;
        public String message;
        public String server;

        public DiscordMessageEvent(String authorName, String message, String server) {
            this.authorName = authorName;
            this.message = message;
            this.server = server;
        }
    }
}
