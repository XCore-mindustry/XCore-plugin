package org.xcore.plugin.security.ingress;

/**
 * Sealed interface representing the result of an ingress security check.
 */
public sealed interface AccessResult {

    /**
     * Connection is allowed to proceed.
     */
    record Allowed() implements AccessResult {
        public static final Allowed INSTANCE = new Allowed();
    }

    /**
     * Connection is denied.
     *
     * @param reason The reason message to show to the player
     * @param silent If true, connection is closed without sending a message (for bots)
     * @param kickDuration Duration in milliseconds before player can reconnect (0 = immediate)
     */
    record Denied(String reason, boolean silent, long kickDuration) implements AccessResult {
        public Denied(String reason) {
            this(reason, false, 0);
        }

        public Denied(String reason, boolean silent) {
            this(reason, silent, 0);
        }
    }

    default boolean isAllowed() {
        return this instanceof Allowed;
    }

    default boolean isDenied() {
        return this instanceof Denied;
    }
}
