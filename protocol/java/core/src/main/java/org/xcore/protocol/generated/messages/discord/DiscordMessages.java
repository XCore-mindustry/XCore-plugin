package org.xcore.protocol.generated.messages.discord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

public final class DiscordMessages {
    private DiscordMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.discord.DiscordMessages instances");
    }

    public record DiscordAdminAccessChangedCommandV1(
            PlayerRefV1 player,
            DiscordIdentityRefV1 discord,
            boolean admin,
            ActorRefV1 source,
            ActorRefV1 actor,
            String reason,
            String server,
            String occurredAt
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "discord.admin-access.changed.command";
        public static final int MESSAGE_VERSION = 1;

        public DiscordAdminAccessChangedCommandV1 {
            Objects.requireNonNull(player, "player must not be null");
            Objects.requireNonNull(discord, "discord must not be null");
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (reason.length() < 1) {
                throw new IllegalArgumentException("reason must be at least 1 characters");
            }
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("player", player.toPayload());
            payload.put("discord", discord.toPayload());
            payload.put("admin", admin);
            payload.put("source", source.toPayload());
            payload.put("actor", actor.toPayload());
            payload.put("reason", reason);
            payload.put("server", server);
            payload.put("occurredAt", occurredAt);
            return payload;
        }
    }

    public record DiscordLinkCodeCreatedV1(
            String code,
            PlayerRefV1 player,
            String server,
            String createdAt,
            String expiresAt
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "discord.link-code-created";
        public static final int MESSAGE_VERSION = 1;

        public DiscordLinkCodeCreatedV1 {
            Objects.requireNonNull(code, "code must not be null");
            if (code.length() < 1) {
                throw new IllegalArgumentException("code must be at least 1 characters");
            }
            Objects.requireNonNull(player, "player must not be null");
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("code", code);
            payload.put("player", player.toPayload());
            payload.put("server", server);
            payload.put("createdAt", createdAt);
            payload.put("expiresAt", expiresAt);
            return payload;
        }
    }

    public record DiscordLinkConfirmCommandV1(
            String code,
            PlayerRefV1 player,
            DiscordIdentityRefV1 discord,
            String server,
            String confirmedAt
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "discord.link.confirm.command";
        public static final int MESSAGE_VERSION = 1;

        public DiscordLinkConfirmCommandV1 {
            Objects.requireNonNull(code, "code must not be null");
            if (code.length() < 1) {
                throw new IllegalArgumentException("code must be at least 1 characters");
            }
            Objects.requireNonNull(player, "player must not be null");
            Objects.requireNonNull(discord, "discord must not be null");
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("code", code);
            payload.put("player", player.toPayload());
            payload.put("discord", discord.toPayload());
            payload.put("server", server);
            payload.put("confirmedAt", confirmedAt);
            return payload;
        }
    }

    public record DiscordLinkStatusChangedV1(
            PlayerRefV1 player,
            DiscordIdentityRefV1 discord,
            DiscordLinkStatusChangedV1Action action,
            String server,
            String occurredAt
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "discord.link.status-changed";
        public static final int MESSAGE_VERSION = 1;

        public DiscordLinkStatusChangedV1 {
            Objects.requireNonNull(player, "player must not be null");
            Objects.requireNonNull(discord, "discord must not be null");
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("player", player.toPayload());
            payload.put("discord", discord.toPayload());
            if (action != null) {
                payload.put("action", action.toString());
            }
            payload.put("server", server);
            payload.put("occurredAt", occurredAt);
            return payload;
        }
    }

    public record DiscordUnlinkCommandV1(
            PlayerRefV1 player,
            DiscordIdentityRefV1 discord,
            ActorRefV1 actor,
            String server,
            String requestedAt
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "discord.unlink.command";
        public static final int MESSAGE_VERSION = 1;

        public DiscordUnlinkCommandV1 {
            Objects.requireNonNull(player, "player must not be null");
            Objects.requireNonNull(discord, "discord must not be null");
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("player", player.toPayload());
            payload.put("discord", discord.toPayload());
            payload.put("actor", actor.toPayload());
            payload.put("server", server);
            payload.put("requestedAt", requestedAt);
            return payload;
        }
    }
}
