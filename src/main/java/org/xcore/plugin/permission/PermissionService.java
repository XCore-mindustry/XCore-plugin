package org.xcore.plugin.permission;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Optional;

/**
 * The single place that decides whether somebody may do something.
 * <p>
 * Until roles exist, every {@link Access#STAFF} node follows the player's admin flag, so the
 * outcome is the same as the scattered {@code player.admin} reads this replaces.
 */
@Singleton
public class PermissionService {

    public static final String ACCESS_DENIED_KEY = "error-access-denied";

    /** The outcome of a check and why, for diagnostics. */
    public record Explanation(String node, boolean granted, String reason) {
    }

    private final Provider<SessionService> sessions;

    @Inject
    public PermissionService(Provider<SessionService> sessions) {
        this.sessions = sessions;
    }

    /** Without a session registry a session is trusted to be current as long as its player is connected. */
    public PermissionService() {
        this(null);
    }

    /**
     * The check Cloud runs for a command. An empty permission is Cloud's way of saying the
     * command asks for nothing.
     */
    public boolean has(XCoreSender sender, String node) {
        if (node == null || node.isEmpty()) {
            return true;
        }
        return explain(sender, node).granted();
    }

    public boolean has(Session session, String node) {
        return explain(session, node).granted();
    }

    /** For the places that only know the player, such as packet handlers and vote targets. */
    public boolean has(Player player, String node) {
        return explain(player, node).granted();
    }

    public void require(Session session, String node) {
        if (!has(session, node)) {
            throw new XCoreCommandException(ACCESS_DENIED_KEY);
        }
    }

    /**
     * Whether {@code actor} may use a staff action on {@code target}: anyone on themselves,
     * and nobody on another member of staff.
     */
    public boolean canTarget(Player actor, Player target) {
        if (actor == null || target == null) {
            return false;
        }
        return actor == target || !has(target, PermissionNodes.MINDUSTRY_ADMIN);
    }

    public Explanation explain(XCoreSender sender, String node) {
        if (sender == null) {
            return new Explanation(node, false, "no sender");
        }
        if (sender.isPlayer()) {
            return explain(sender.player(), node);
        }
        Optional<PermissionNode> declared = PermissionNodes.find(node);
        if (declared.isEmpty()) {
            return undeclared(node);
        }
        return switch (sender.actor()) {
            case Actor.LocalConsole _ -> new Explanation(node, true, "local console");
            case Actor.RemoteConsole _, Actor.SystemActor _ -> declared.get().access() == Access.CONSOLE_ONLY
                    ? new Explanation(node, false, "local console only")
                    : new Explanation(node, true, "relayed console");
            case Actor.PlayerActor _ -> new Explanation(node, false, "player actor without a player");
        };
    }

    public Explanation explain(Session session, String node) {
        if (session == null || session.player == null) {
            return new Explanation(node, false, "no session");
        }
        if (!isCurrent(session)) {
            return new Explanation(node, false, "stale session");
        }
        return explain(session.player, node);
    }

    public Explanation explain(Player player, String node) {
        if (player == null) {
            return new Explanation(node, false, "no player");
        }
        Optional<PermissionNode> declared = PermissionNodes.find(node);
        if (declared.isEmpty()) {
            return undeclared(node);
        }
        if (player.con != null && player.con.hasDisconnected) {
            return new Explanation(node, false, "disconnected");
        }
        return switch (declared.get().access()) {
            case PLAYER -> new Explanation(node, true, "open to every player");
            case STAFF -> player.admin
                    ? new Explanation(node, true, "admin")
                    : new Explanation(node, false, "not an admin");
            case CONSOLE_ONLY -> new Explanation(node, false, "local console only");
        };
    }

    /**
     * A session outlives its player in menu callbacks and async continuations. It still speaks
     * for the player only while the registry holds a session of the very same connection.
     */
    private boolean isCurrent(Session session) {
        SessionService registry = sessions != null ? sessions.get() : null;
        if (registry == null) {
            return true;
        }
        Session registered = registry.get(session.player.uuid());
        return registered != null && registered.player == session.player;
    }

    private static Explanation undeclared(String node) {
        return new Explanation(node, false, "undeclared node");
    }
}
