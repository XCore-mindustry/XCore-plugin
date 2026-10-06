package org.xcore.plugin.permission.grant;

import java.time.Instant;
import java.util.Objects;

/**
 * One entry of a player's grants: either a role, or a single rule given or denied directly.
 *
 * @param id        stable within the player's document; what {@code perm ... remove} names
 * @param role      the role, or null for a direct rule
 * @param node      the rule's node or wildcard, or null for a role
 * @param allow     for a direct rule, false when it is a denial; always true for a role
 * @param server    the only server it applies on, or null for all of them
 * @param expiresAt when it stops applying, or null for never
 * @param source    {@link #SOURCE_MANUAL}, {@link #SOURCE_LEGACY} or {@code discord:<roleId>}
 * @param by        who issued it, as the audit names them
 */
public record Grant(String id, String role, String node, boolean allow, String server,
                    Instant expiresAt, String source, String by, Instant at, String reason) {

    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_LEGACY = "legacy";
    public static final String SOURCE_DISCORD_PREFIX = "discord:";

    public Grant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        if ((role == null) == (node == null)) {
            throw new IllegalArgumentException("A grant is either a role or a node");
        }
        if (role != null && !allow) {
            throw new IllegalArgumentException("A role cannot be denied; deny its nodes instead");
        }
    }

    public static String discordSource(String discordRoleId) {
        return SOURCE_DISCORD_PREFIX + discordRoleId;
    }

    public boolean isRole() {
        return role != null;
    }

    public boolean isFromDiscord() {
        return source.startsWith(SOURCE_DISCORD_PREFIX);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean appliesOn(String serverName) {
        return server == null || server.equalsIgnoreCase(serverName);
    }

    /** Whether the two give the same thing in the same place from the same source. */
    public boolean sameSubject(Grant other) {
        return Objects.equals(role, other.role)
                && Objects.equals(node, other.node)
                && allow == other.allow
                && Objects.equals(server, other.server)
                && source.equals(other.source);
    }
}
