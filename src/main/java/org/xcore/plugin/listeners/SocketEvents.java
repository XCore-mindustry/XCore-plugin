package org.xcore.plugin.listeners;

import fr.xpdustry.javelin.JavelinEvent;

public class SocketEvents {
    /**
     * Событие сообщения из чата
     */
    public static final class MessageEvent implements JavelinEvent {
        public String authorName, message, server;

        /**
         * Конструктор сообщения чата
         * @param author автор сообщения
         * @param message текст сообщения
         * @param server целевой сервер
         */
        public MessageEvent(String author, String message, String server) {
            this.authorName = author;
            this.message = message;
            this.server = server;
        }
    }

    /**
     * Событие сервера
     */
    public static final class ServerActionEvent implements JavelinEvent {
        public String message, server;

        /**
         * Конструктор серверного события
         * @param message сообщение
         * @param server целевой сервер
         */
        public ServerActionEvent(String message, String server) {
            this.message = message;
            this.server = server;
        }
    }

    /**
     * Событие подключения/отключения игрока
     */
    public static final class PlayerJoinLeaveEvent implements JavelinEvent {
        public String playerName, server;

        /**
         * true if is join event, false if is leave event
         */
        public boolean join;

        /**
         * Констректор подключения/отключения
         * @param playerName Ник игрока
         * @param server целевой сервер
         * @param join true - при входе, false - при выходе
         */
        public PlayerJoinLeaveEvent(String playerName, String server, Boolean join) {
            this.playerName = playerName;
            this.server = server;
            this.join = join;
        }
    }

    /**
     * Событие входящего сообщения из Discord
     */
    public static final class DiscordMessageEvent implements JavelinEvent {
        public String authorName, message, server;

        /**
         * Конструктор входящего сообщения из Discord
         * @param authorName Ник автора
         * @param message Текст сообщения
         * @param server Целевой сервер
         */
        public DiscordMessageEvent(String authorName, String message, String server) {
            this.authorName = authorName;
            this.message = message;
            this.server = server;
        }
    }
}
