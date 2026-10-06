package org.xcore.plugin.permission;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.permission.grant.PermissionGrants;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

/**
 * Who may act on whom: only somebody whose roles weigh strictly more than the target's.
 * Without roles there is no hierarchy and everything here says yes.
 */
@Singleton
public class TargetHierarchy {

    public static final String DENIED_KEY = "error-target-outranks";

    private final PermissionService permissions;
    private final PermissionGrants grants;
    private final Provider<SessionService> sessions;
    private final Async async;

    @Inject
    public TargetHierarchy(PermissionService permissions, PermissionGrants grants, Provider<SessionService> sessions, Async async) {
        this.permissions = permissions;
        this.grants = grants;
        this.sessions = sessions;
        this.async = async;
    }

    /** No hierarchy at all; for wiring without roles. */
    public static TargetHierarchy none() {
        return new TargetHierarchy(new PermissionService(), null, null, null);
    }

    public boolean enabled() {
        return permissions.rolesEnabled();
    }

    /** For two players who are both online here. No I/O. */
    public boolean outranks(Player actor, Player target) {
        if (!permissions.rolesEnabled()) {
            return true;
        }
        return permissions.canTarget(actor, target);
    }

    /**
     * Whether {@code actor} may act on the player with this uuid. Reads the store when the
     * target is not online here, so keep it off the game thread unless the caller is doing
     * I/O there anyway.
     */
    public boolean mayTarget(Session actor, String targetUuid) {
        if (!permissions.rolesEnabled()) {
            return true;
        }
        if (actor == null || actor.player == null || targetUuid == null) {
            return false;
        }
        if (targetUuid.equals(actor.player.uuid())) {
            return true;
        }
        Session target = sessions.get().get(targetUuid);
        if (target != null && target.player != null) {
            return permissions.canTarget(actor.player, target.player);
        }
        return permissions.canTarget(actor, grants.weightOf(targetUuid));
    }

    /**
     * Runs {@code action} on the game thread if {@code actor} may act on the target, and
     * {@code denied} otherwise. Game thread; the store is read elsewhere when it has to be.
     */
    public void whenAllowed(Session actor, String targetUuid, Runnable action, Runnable denied) {
        if (!permissions.rolesEnabled()) {
            action.run();
            return;
        }
        if (actor == null || actor.player == null || targetUuid == null) {
            denied.run();
            return;
        }
        Session target = sessions.get().get(targetUuid);
        if (targetUuid.equals(actor.player.uuid()) || (target != null && target.player != null)) {
            (mayTarget(actor, targetUuid) ? action : denied).run();
            return;
        }
        async.supply(() -> grants.weightOf(targetUuid)).thenMain((weight, error) -> {
            if (error != null) {
                PLog.err("[Permissions] Could not read the weight of @: @", targetUuid, error.getMessage());
                denied.run();
                return;
            }
            // Checked again here: the actor's own roles may have changed while the store was read.
            (permissions.canTarget(actor, weight) ? action : denied).run();
        });
    }
}
