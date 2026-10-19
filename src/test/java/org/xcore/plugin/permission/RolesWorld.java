package org.xcore.plugin.permission;

import mindustry.gen.Player;
import org.xcore.plugin.database.MongoTransactions;
import org.xcore.plugin.model.AuditAppendCommand;
import org.xcore.plugin.model.AuditAppendResult;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.grant.InMemoryGrantStore;
import org.xcore.plugin.permission.grant.PermissionGrants;
import org.xcore.plugin.permission.role.PermissionModelLoader;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.permission.role.PermissionSet;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One server running with roles, wired from the real classes over an in-memory store. The
 * clock only moves when told to, and work handed to the storage executor or the game thread
 * waits in a queue until the test runs it, so races are laid out step by step.
 */
public final class RolesWorld {

    public static final String DISCORD_MODERATOR = "111111111111111111";
    public static final String DISCORD_ADMIN = "222222222222222222";

    public static final String ROLES = """
            schemaVersion = 1

            [roles.moderator]
            weight = 10
            permissions = ["xcore.moderation.mute", "xcore.moderation.unmute", "xcore.moderation.kick"]

            [roles.admin]
            weight = 50
            parents = ["moderator"]
            permissions = ["xcore.*", "-xcore.permissions.manage", "mindustry.admin"]

            [discord]
            guildId = "123456789012345678"

            [[discord.bindings]]
            roleId = "111111111111111111"
            role = "moderator"

            [[discord.bindings]]
            roleId = "222222222222222222"
            role = "admin"
            """;

    /** A clock that stands still until moved. */
    public static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-06T12:00:00Z");

        public void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    /** Collects tasks instead of running them. */
    public static final class Queue implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        public int size() {
            return tasks.size();
        }

        /** Takes the oldest task out without running it, to run it late. */
        public Runnable take() {
            return tasks.removeFirst();
        }

        /** Runs what is queued now, not what those tasks queue in turn. */
        public void runQueued() {
            List<Runnable> batch = new ArrayList<>(tasks);
            tasks.clear();
            batch.forEach(Runnable::run);
        }
    }

    public final String serverName;
    public final MutableClock clock;
    public final InMemoryGrantStore store;
    public final PermissionRoles roles;
    public final SessionService sessionService = mock(SessionService.class);
    public final Map<String, Session> online = new LinkedHashMap<>();
    public final PermissionService permissions;
    public final StaffAccess staff;
    public final Queue io = new Queue();
    public final Queue main = new Queue();
    public final PermissionSessions sessions;
    public final AuditService audit = mock(AuditService.class);
    public final List<AuditAppendCommand> audited = new ArrayList<>();
    public final PermissionGrants grants;

    public RolesWorld() {
        this("main", new MutableClock(), new InMemoryGrantStore(), false);
    }

    public RolesWorld(String serverName, MutableClock clock, InMemoryGrantStore store, boolean trustNativeAdmins) {
        this.serverName = serverName;
        this.clock = clock;
        this.store = store;
        this.roles = PermissionRoles.of(PermissionModelLoader.parse(ROLES), serverName, trustNativeAdmins, clock);
        this.permissions = new PermissionService(() -> sessionService, roles);
        this.staff = new StaffAccess(roles);
        this.sessions = new PermissionSessions(roles, store, () -> sessionService, staff, io, main);

        when(sessionService.get(anyString())).thenAnswer(call -> online.get(call.<String>getArgument(0)));
        when(sessionService.get(any(Player.class))).thenAnswer(call -> online.get(call.<Player>getArgument(0).uuid()));
        when(sessionService.getAllCachedSnapshot()).thenAnswer(_ -> new ArrayList<>(online.values()));

        when(audit.append(any(), any(AuditAppendCommand.class))).thenAnswer(call -> {
            audited.add(call.getArgument(1));
            return AuditAppendResult.success(null);
        });
        when(audit.append(any(AuditAppendCommand.class))).thenAnswer(call -> {
            audited.add(call.getArgument(0));
            return AuditAppendResult.success(null);
        });
        this.grants = new PermissionGrants(store, MongoTransactions.none(), audit, roles);
    }

    public static PlayerData data(String uuid) {
        PlayerData data = new PlayerData();
        data.uuid = uuid;
        data.nickname = uuid;
        data.exists = true;
        return data;
    }

    /** A connected player whose admin flag can be written, as the game's own would be. */
    public static Player player(String uuid) {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn(uuid);
        doAnswer(call -> {
            player.admin = call.getArgument(0);
            return null;
        }).when(player).admin(anyBoolean());
        return player;
    }

    /** Joins the way the connection handler does: grants are read first, then the session appears. */
    public Session join(String uuid) {
        return join(uuid, false);
    }

    public Session join(String uuid, boolean nativeAdmin) {
        boolean trusted = sessions.trustsNativeAdmin(nativeAdmin);
        PermissionSet loaded = sessions.load(uuid, trusted);
        Session session = mock(Session.class);
        session.player = player(uuid);
        session.data = data(uuid);
        session.permissionSet = PermissionSet.EMPTY;
        online.put(uuid, session);
        sessions.attach(session, loaded, trusted);
        return session;
    }

    public void leave(String uuid) {
        online.remove(uuid);
    }

    /** Runs queued work on both sides until nothing is left. */
    public void settle() {
        while (io.size() > 0 || main.size() > 0) {
            main.runQueued();
            io.runQueued();
        }
    }

    public boolean has(Session session, String node) {
        return permissions.has(session, node);
    }
}
