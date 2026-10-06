package org.xcore.plugin.permission;

/**
 * Who a {@link PermissionNode} is open to before any grant is looked at.
 */
public enum Access {
    /** Every player has it. */
    PLAYER,
    /** Needs a grant and an active staff login. */
    STAFF,
    /** The local server console only. Never reachable by a player, a wildcard or a relayed command. */
    CONSOLE_ONLY
}
