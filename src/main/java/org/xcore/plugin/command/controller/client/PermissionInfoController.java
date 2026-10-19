package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.permission.StaffAccess;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.permission.role.PermissionSet;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.time.Instant;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

/** What a player holds themselves. Reads only the session, so it stays on the game thread. */
@Singleton
public class PermissionInfoController implements CloudClientController {

    private final SessionService sessionService;
    private final PermissionRoles roles;
    private final StaffAccess staffAccess;

    @Inject
    public PermissionInfoController(SessionService sessionService, PermissionRoles roles, StaffAccess staffAccess) {
        this.sessionService = sessionService;
        this.roles = roles;
        this.staffAccess = staffAccess;
    }

    @Command("perm me")
    @CommandDescription("Shows the roles you hold on this server.")
    public void me(XCoreSender sender) {
        Session session = resolveSession(sender, sessionService);
        if (session == null || session.player == null) return;

        if (!roles.enabled()) {
            session.locale().send("perm-me-legacy", args("admin", session.player.admin ? "yes" : "no"));
            return;
        }

        Instant now = roles.clock().instant();
        PermissionSet set = session.permissionSet;
        List<String> origins = set.origins(now);
        session.locale().send("perm-me-header", args("weight", set.weight(now)));
        if (origins.isEmpty()) {
            session.locale().send("perm-me-none", args());
        }
        for (String origin : origins) {
            session.player.sendMessage("[lightgray] - [white]" + origin);
        }
        if (set.canLogInAsStaff(now)) {
            session.locale().send(staffAccess.isLoggedIn(session)
                    ? (set.isStale(now) ? "perm-me-stale" : "perm-me-logged-in")
                    : "perm-me-not-logged-in", args());
        }
    }
}
