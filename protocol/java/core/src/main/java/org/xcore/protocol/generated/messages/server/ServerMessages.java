package org.xcore.protocol.generated.messages.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public final class ServerMessages {
    private ServerMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.server.ServerMessages instances");
    }

    public record PlayerDataCacheReloadCommandV1(
            String server
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "player-data-cache.reload.command";
    public static final int MESSAGE_VERSION = 1;

        public PlayerDataCacheReloadCommandV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            return payload;
        }
    }

    public record ServerCommandExecuteCommandV1(
            String command,
            List<String> targetServers,
            boolean exclusion
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "server-command.execute.command";
    public static final int MESSAGE_VERSION = 1;

        public ServerCommandExecuteCommandV1 {
            Objects.requireNonNull(command, "command must not be null");
            if (command.length() < 1) {
                throw new IllegalArgumentException("command must be at least 1 characters");
            }
            targetServers = Objects.requireNonNull(targetServers, "targetServers must not be null");
            targetServers = List.copyOf(targetServers);
            for (String item : targetServers) {
                Objects.requireNonNull(item, "targetServers[] must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("command", command);
            payload.put("targetServers", List.copyOf(targetServers));
            payload.put("exclusion", exclusion);
            return payload;
        }
    }

    public record ServerActionV1(
            String message,
            String server
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "server.action";
    public static final int MESSAGE_VERSION = 1;

        public ServerActionV1 {
            Objects.requireNonNull(message, "message must not be null");
            if (message.length() < 1) {
                throw new IllegalArgumentException("message must be at least 1 characters");
            }
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("message", message);
            payload.put("server", server);
            return payload;
        }
    }

    public record ServerHeartbeatV1(
            String serverName,
            long discordChannelId,
            int players,
            int maxPlayers,
            String version,
            String host,
            Integer port
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "server.heartbeat";
    public static final int MESSAGE_VERSION = 1;

        public ServerHeartbeatV1 {
            Objects.requireNonNull(serverName, "serverName must not be null");
            if (serverName.length() < 1) {
                throw new IllegalArgumentException("serverName must be at least 1 characters");
            }
            if (discordChannelId < 0) {
                throw new IllegalArgumentException("discordChannelId must be >= 0");
            }
            if (players < 0) {
                throw new IllegalArgumentException("players must be >= 0");
            }
            if (maxPlayers < 0) {
                throw new IllegalArgumentException("maxPlayers must be >= 0");
            }
            Objects.requireNonNull(version, "version must not be null");
            if (version.length() < 1) {
                throw new IllegalArgumentException("version must be at least 1 characters");
            }
            if (host != null) {
                Objects.requireNonNull(host, "host must not be null");
                if (host.length() < 1) {
                    throw new IllegalArgumentException("host must be at least 1 characters");
                }
            }
            if (port != null) {
                if (port < 0) {
                    throw new IllegalArgumentException("port must be >= 0");
                }
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("serverName", serverName);
            payload.put("discordChannelId", discordChannelId);
            payload.put("players", players);
            payload.put("maxPlayers", maxPlayers);
            payload.put("version", version);
            if (host != null) {
                payload.put("host", host);
            }
            if (port != null) {
                payload.put("port", port);
            }
            return payload;
        }
    }
}
