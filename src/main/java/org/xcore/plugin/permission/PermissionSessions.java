package org.xcore.plugin.permission;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.concurrent.MainThreadDispatcher;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.permission.grant.GrantDocument;
import org.xcore.plugin.permission.grant.GrantStore;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.permission.role.PermissionSet;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Keeps the {@link PermissionSet} of every online player in step with the store.
 * <p>
 * A set is read when the player joins, again when a change is announced, and for everybody
 * once a minute in case an announcement was lost. When the store cannot be read the sets stay
 * as they are: {@link PermissionSet} itself stops giving staff rights once it is too old, and
 * never stops denying.
 */
@Singleton
public class PermissionSessions {

    /** How often {@link #refreshAll()} is meant to be called. */
    public static final float REFRESH_SECONDS = 60f;

    private final PermissionRoles roles;
    private final GrantStore store;
    private final Provider<SessionService> sessions;
    private final StaffAccess staffAccess;
    private final Executor io;
    private final Executor main;

    @Inject
    public PermissionSessions(PermissionRoles roles, GrantStore store, Provider<SessionService> sessions,
                              StaffAccess staffAccess, StorageExecutor storage) {
        this(roles, store, sessions, staffAccess, storage::execute, MainThreadDispatcher.mindustry()::execute);
    }

    public PermissionSessions(PermissionRoles roles, GrantStore store, Provider<SessionService> sessions,
                              StaffAccess staffAccess, Executor io, Executor main) {
        this.roles = roles;
        this.store = store;
        this.sessions = sessions;
        this.staffAccess = staffAccess;
        this.io = io;
        this.main = main;
    }

    public boolean enabled() {
        return roles.enabled();
    }

    /**
     * Reads what a joining player is granted. I/O; a failure is the caller's to handle, since a
     * player let in without their grants would be let in without their denials too.
     *
     * @param nativeAdmin whether the game's admin list vouches for this connection
     */
    public PermissionSet load(String uuid, boolean nativeAdmin) {
        if (!roles.enabled()) {
            return PermissionSet.EMPTY;
        }
        return compile(store.find(uuid), nativeAdmin);
    }

    /** Whether this server takes the game's admin list at its word. */
    public boolean trustsNativeAdmin(boolean nativeAdmin) {
        return roles.enabled() && roles.trustNativeAdmins() && nativeAdmin;
    }

    /**
     * Gives a session the set read for it while the player was joining. Game thread.
     * A trusted entry of the admin list was checked by the game against the connection, so it
     * counts as logged in.
     */
    public void attach(Session session, PermissionSet set, boolean nativeAdmin) {
        if (!roles.enabled() || session == null) {
            return;
        }
        session.permissionSet = set;
        session.nativeAdmin = nativeAdmin;
        session.staffAuthenticated = nativeAdmin;
        staffAccess.apply(session);
    }

    /**
     * Somebody's grants changed, here or on another server. Any thread.
     *
     * @param revision the revision the change produced
     */
    public void onChanged(String uuid, long revision) {
        if (!roles.enabled() || uuid == null) {
            return;
        }
        main.execute(() -> {
            Session session = sessions.get().get(uuid);
            if (session == null) {
                return;
            }
            PermissionSet current = session.permissionSet;
            if (!current.isPlaceholder() && current.revision() >= revision) {
                // A duplicate, or an announcement that arrived after a newer one.
                return;
            }
            boolean nativeAdmin = session.nativeAdmin;
            io.execute(() -> {
                PermissionSet loaded;
                try {
                    loaded = compile(store.find(uuid), nativeAdmin);
                } catch (RuntimeException e) {
                    PLog.err("[Permissions] Could not read the changed grants of @: @", uuid, e.getMessage());
                    return;
                }
                main.execute(() -> replace(uuid, loaded));
            });
        });
    }

    /**
     * Reads the grants of everybody online again. Game thread; the reading itself is handed
     * to the storage executor.
     */
    public void refreshAll() {
        if (!roles.enabled()) {
            return;
        }
        List<String> uuids = new ArrayList<>();
        List<Boolean> nativeAdmins = new ArrayList<>();
        for (Session session : sessions.get().getAllCachedSnapshot()) {
            if (session.player != null) {
                uuids.add(session.player.uuid());
                nativeAdmins.add(session.nativeAdmin);
            }
        }
        if (uuids.isEmpty()) {
            return;
        }
        io.execute(() -> {
            List<PermissionSet> loaded = new ArrayList<>();
            try {
                Map<String, GrantDocument> documents = store.findAll(uuids);
                for (int i = 0; i < uuids.size(); i++) {
                    GrantDocument document = documents.get(uuids.get(i));
                    loaded.add(compile(document != null ? document : GrantDocument.empty(uuids.get(i)), nativeAdmins.get(i)));
                }
            } catch (RuntimeException e) {
                PLog.err("[Permissions] Could not refresh grants, keeping the last ones: @", e.getMessage());
                // The sets age on their own; the admin flag has to follow them.
                main.execute(() -> uuids.forEach(uuid -> staffAccess.apply(sessions.get().get(uuid))));
                return;
            }
            main.execute(() -> {
                for (int i = 0; i < uuids.size(); i++) {
                    replace(uuids.get(i), loaded.get(i));
                }
            });
        });
    }

    /** Game thread. A set older than the one in use is dropped: it was read before a newer change. */
    private void replace(String uuid, PermissionSet loaded) {
        Session session = sessions.get().get(uuid);
        if (session == null) {
            return;
        }
        PermissionSet current = session.permissionSet;
        if (current.isPlaceholder() || loaded.revision() >= current.revision()) {
            session.permissionSet = loaded;
        }
        staffAccess.apply(session);
    }

    private PermissionSet compile(GrantDocument document, boolean nativeAdmin) {
        Instant now = roles.clock().instant();
        return PermissionSet.compile(roles.model(), document, roles.serverName(), nativeAdmin, now);
    }
}
