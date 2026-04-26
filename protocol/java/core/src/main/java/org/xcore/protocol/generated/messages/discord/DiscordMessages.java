package org.xcore.protocol.generated.messages.discord;

import java.util.Objects;
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
            String adminSource,
            String requestedBy,
            String reason,
            String server,
            String occurredAt
    ) {
        public static final String MESSAGE_TYPE = "discord.admin-access.changed.command";
        public static final int MESSAGE_VERSION = 1;

        public DiscordAdminAccessChangedCommandV1 {
            Objects.requireNonNull(player, "player must not be null");
            Objects.requireNonNull(discord, "discord must not be null");
            Objects.requireNonNull(adminSource, "adminSource must not be null");
            if (adminSource.length() < 1) {
                throw new IllegalArgumentException("adminSource must be at least 1 characters");
            }
            Objects.requireNonNull(requestedBy, "requestedBy must not be null");
            if (requestedBy.length() < 1) {
                throw new IllegalArgumentException("requestedBy must be at least 1 characters");
            }
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
    }

    public record DiscordLinkConfirmCommandV1(
            String code,
            PlayerRefV1 player,
            DiscordIdentityRefV1 discord,
            String server,
            String confirmedAt
    ) {
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
    }

    public record DiscordLinkStatusChangedV1(
            PlayerRefV1 player,
            DiscordIdentityRefV1 discord,
            String action,
            String server,
            String occurredAt
    ) {
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
    }

    public record DiscordUnlinkCommandV1(
            PlayerRefV1 player,
            DiscordIdentityRefV1 discord,
            String requestedBy,
            String server,
            String requestedAt
    ) {
        public static final String MESSAGE_TYPE = "discord.unlink.command";
        public static final int MESSAGE_VERSION = 1;

        public DiscordUnlinkCommandV1 {
            Objects.requireNonNull(player, "player must not be null");
            Objects.requireNonNull(discord, "discord must not be null");
            Objects.requireNonNull(requestedBy, "requestedBy must not be null");
            if (requestedBy.length() < 1) {
                throw new IllegalArgumentException("requestedBy must be at least 1 characters");
            }
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        }
    }
}
