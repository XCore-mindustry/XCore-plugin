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

import java.util.concurrent.Callable;

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
     * target is not online here, so never call it on the game thread: that is what
     * {@code whenAllowed} is for.
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
        whenAllowed(actor, targetUuid, null, action, denied);
    }

    /**
     * As {@link #whenAllowed(Session, String, Runnable, Runnable)}, for an action that needs
     * {@code node}: if the store had to be read, the node is asked for again afterwards, since
     * the actor may have lost it in the meantime.
     */
    public void whenAllowed(Session actor, String targetUuid, String node, Runnable action, Runnable denied) {
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
        async.supply(() -> new Target(targetUuid, grants.weightOf(targetUuid)))
                .thenMain((read, error) -> decide(actor, read, error, node, action, denied));
    }

    /**
     * As {@link #whenAllowed(Session, String, String, Runnable, Runnable)} when finding out who
     * the target is takes I/O as well. {@code targetUuid} runs off the game thread and returns
     * null for nobody; {@code action} then runs, to report the unknown target the way it
     * always did.
     */
    public void whenAllowed(Session actor, Callable<String> targetUuid, String node, Runnable action, Runnable denied) {
        if (!permissions.rolesEnabled()) {
            action.run();
            return;
        }
        if (actor == null || actor.player == null) {
            denied.run();
            return;
        }
        async.supply(() -> {
            String uuid = targetUuid.call();
            return uuid == null ? null : new Target(uuid, grants.weightOf(uuid));
        }).thenMain((read, error) -> decide(actor, read, error, node, action, denied));
    }

    /** A target and what its roles weighed in the store. */
    private record Target(String uuid, int weight) {
    }

    /** Game thread, after the store was read: everything that may have changed meanwhile is asked again. */
    private void decide(Session actor, Target read, Throwable error, String node, Runnable action, Runnable denied) {
        if (actor.player == null || sessions.get().get(actor.player.uuid()) != actor) {
            // The actor left while the store was read; there is nobody to act for or to tell.
            return;
        }
        if (error != null) {
            PLog.err("[Permissions] Could not read the roles of a target for @: @", actor.player.uuid(), error.getMessage());
            denied.run();
            return;
        }
        if (node != null && !permissions.has(actor, node)) {
            denied.run();
            return;
        }
        if (read == null || read.uuid().equals(actor.player.uuid())) {
            action.run();
            return;
        }
        // The target may have joined meanwhile; what they hold here then counts, not the store.
        Session target = sessions.get().get(read.uuid());
        boolean allowed = target != null && target.player != null
                ? permissions.canTarget(actor.player, target.player)
                : permissions.canTarget(actor, read.weight());
        (allowed ? action : denied).run();
    }
}
