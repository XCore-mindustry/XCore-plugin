package org.xcore.plugin.permission;

import org.xcore.plugin.session.Session;

/**
 * Who is behind an action. Permission checks and audit records both start from it, so a missing
 * actor is never silently read as the console.
 */
public sealed interface Actor {

    LocalConsole LOCAL_CONSOLE = new LocalConsole();

    /** The name the audit log stores for this actor. */
    String auditName();

    /** Whether a player's name reads as one of the non-player actors, which the audit log tells apart by name. */
    static boolean isReserved(String playerName) {
        String name = playerName.strip().toLowerCase(java.util.Locale.ROOT);
        return name.equals(LocalConsole.AUDIT_NAME)
                || name.startsWith(RemoteConsole.AUDIT_PREFIX)
                || name.startsWith(SystemActor.AUDIT_PREFIX);
    }

    /**
     * @param session the player's session when the action started; may be gone by the time it is checked
     */
    record PlayerActor(String uuid, Session session) implements Actor {
        @Override
        public String auditName() {
            String name = session != null && session.player != null ? session.player.plainName() : null;
            return name == null || name.isBlank() || isReserved(name) ? uuid : name;
        }
    }

    /** The console of this server process. */
    record LocalConsole() implements Actor {
        public static final String AUDIT_NAME = "console";

        @Override
        public String auditName() {
            return AUDIT_NAME;
        }
    }

    /** A console command relayed from another server of the network. */
    record RemoteConsole(String sourceServer) implements Actor {
        public static final String AUDIT_PREFIX = "remote-console@";
        /** The relay message does not carry its origin yet. */
        public static final String UNKNOWN_SOURCE = "unknown";

        public RemoteConsole {
            if (sourceServer == null || sourceServer.isBlank()) {
                sourceServer = UNKNOWN_SOURCE;
            }
        }

        @Override
        public String auditName() {
            return AUDIT_PREFIX + sourceServer;
        }
    }

    /** The plugin acting on its own, such as an automatic sanction. */
    record SystemActor(String reason) implements Actor {
        public static final String AUDIT_PREFIX = "system:";

        @Override
        public String auditName() {
            return AUDIT_PREFIX + reason;
        }
    }
}
