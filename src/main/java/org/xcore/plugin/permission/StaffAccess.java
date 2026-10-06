package org.xcore.plugin.permission;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.permission.role.PermissionSet;
import org.xcore.plugin.session.Session;

import java.time.Instant;

import static mindustry.Vars.netServer;

/**
 * Logging in and out as staff, and what that does to the game's own admin flag. Game thread
 * only, no I/O.
 * <p>
 * With roles on, this is the only place that writes {@code Player.admin}: the flag is computed
 * from {@link PermissionNodes#MINDUSTRY_ADMIN} and the game's admin list is never touched.
 * With roles off it keeps doing what a login always did: the flag and the admin list together.
 */
@Singleton
public class StaffAccess {

    private final PermissionRoles roles;

    @Inject
    public StaffAccess(PermissionRoles roles) {
        this.roles = roles;
    }

    /** Roles switched off. */
    public static StaffAccess legacy() {
        return new StaffAccess(PermissionRoles.legacy());
    }

    public boolean rolesEnabled() {
        return roles.enabled();
    }

    /** Roles mode: whether something the player holds right now is worth logging in for. */
    public boolean mayLogIn(Session session) {
        return roles.enabled() && session != null && session.permissionSet.canLogInAsStaff(roles.clock().instant());
    }

    public boolean isLoggedIn(Session session) {
        if (session == null || session.player == null) {
            return false;
        }
        return roles.enabled() ? session.staffAuthenticated : session.player.admin;
    }

    /** The player has proven who they are on this connection. */
    public void logIn(Session session) {
        Player player = session.player;
        if (player == null) {
            return;
        }
        if (roles.enabled()) {
            session.staffAuthenticated = true;
            apply(session);
            return;
        }
        player.admin(true);
        String usid = player.getInfo() != null ? player.getInfo().adminUsid : null;
        netServer.admins.adminPlayer(player.uuid(), usid);
    }

    public void logOut(Session session) {
        if (session == null) {
            return;
        }
        Player player = session.player;
        if (roles.enabled()) {
            session.staffAuthenticated = false;
            apply(session);
            return;
        }
        if (player != null) {
            player.admin(false);
            netServer.admins.unAdminPlayer(player.uuid());
        }
    }

    /**
     * Roles mode: brings the admin flag in line with what the session holds now. Call it after
     * every change to the session's grants or login.
     */
    public void apply(Session session) {
        if (!roles.enabled() || session == null || session.player == null) {
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
