package org.xcore.plugin.permission;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.permission.role.PermissionSet;
import org.xcore.plugin.session.Session;

import java.time.Instant;

/**
 * Logging in and out as staff, and what that does to the game's own admin flag. Game thread
 * only, no I/O.
 * <p>
 * This is the only place that writes {@code Player.admin}: the flag is computed from
 * {@link PermissionNodes#MINDUSTRY_ADMIN} and the game's admin list is never touched.
 */
@Singleton
public class StaffAccess {

    private final PermissionRoles roles;

    @Inject
    public StaffAccess(PermissionRoles roles) {
        this.roles = roles;
    }

    /** Whether something the player holds right now is worth logging in for. */
    public boolean mayLogIn(Session session) {
        return session != null && session.permissionSet.canLogInAsStaff(roles.clock().instant());
    }

    public boolean isLoggedIn(Session session) {
        return session != null && session.player != null && session.staffAuthenticated;
    }

    /** The player has proven who they are on this connection. */
    public void logIn(Session session) {
        if (session == null || session.player == null) {
            return;
        }
        session.staffAuthenticated = true;
        apply(session);
    }

    public void logOut(Session session) {
        if (session == null) {
            return;
        }
        session.staffAuthenticated = false;
        apply(session);
    }

    /**
     * The player's password was reset. Whoever holds this connection proved themselves with
     * the password that is gone, so the login ends and nothing cached can bring it back.
     */
    public void forgetCredentials(Session session) {
        if (session == null) {
            return;
        }
        if (session.data != null) {
            session.data.password = "";
            session.data.clearDeviceTokens();
        }
        logOut(session);
    }

    /**
     * Brings the admin flag in line with what the session holds now. Call it after every change
     * to the session's grants or login.
     */
    public void apply(Session session) {
        if (session == null || session.player == null) {
            return;
        }
        Instant now = roles.clock().instant();
        PermissionSet set = session.permissionSet;
        // Losing the last staff role ends the login: getting a role back later must not hand
        // the rights to whoever happens to be connected. A set that merely went stale keeps
        // the login, so the rights come back by themselves once the store answers again.
        if (session.staffAuthenticated && !set.isPlaceholder() && !set.canLogInAsStaff(now)) {
            session.staffAuthenticated = false;
        }
        boolean admin = PermissionNodes.find(PermissionNodes.MINDUSTRY_ADMIN)
                .map(node -> set.check(node, session.staffAuthenticated, now).granted())
                .orElse(false);
        if (session.player.admin != admin) {
            session.player.admin(admin);
        }
    }
}
