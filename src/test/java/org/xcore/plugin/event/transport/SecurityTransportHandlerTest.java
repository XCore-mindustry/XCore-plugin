package org.xcore.plugin.event.transport;

import arc.util.Log;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.AuthResultStatus;
import org.xcore.plugin.permission.Actor;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.permission.RolesWorld;
import org.xcore.plugin.permission.StaffCredentials;
import org.xcore.plugin.permission.TargetHierarchy;
import org.xcore.plugin.permission.grant.InMemoryGrantStore;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.service.AdminAuthService;
import org.xcore.plugin.service.AuthStatusBroadcaster;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.protocol.generated.messages.security.SecurityMessages.PlayerPasswordResetCommandV1;
import org.xcore.protocol.generated.messages.security.SecurityMessages.SecurityPermissionsChangedV1;
import org.xcore.protocol.generated.messages.security.SecurityMessages.SecurityStaffResetPasswordRequestV1;
import org.xcore.protocol.generated.messages.security.SecurityMessages.SecurityStaffResetPasswordResponseV1;
import org.xcore.protocol.generated.messages.security.SecurityMessages.SecurityStaffSyncRequestV1;
import org.xcore.protocol.generated.messages.security.SecurityMessages.SecurityStaffSyncResponseV1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bot's requests, and the whole staff story on two servers that share one store and hear
 * each other's announcements.
 */
class SecurityTransportHandlerTest {

    private static final String MODERATOR = "mod-uuid";
    private static final String ADMIN = "admin-uuid";
    private static final String DISCORD_ID = "900000000000000001";

    /** One server: its world, its handler and what its network was asked to send. */
    private final class Server {
        final RolesWorld world;
        final NetworkService network = mock(NetworkService.class);
        final SecurityTransportHandler handler;
        final AdminAuthService auth;

        Server(String name) {
            world = new RolesWorld(name, clock, store, false);
            StaffCredentials credentials = new StaffCredentials(players, world.grants, world.roles, network);
            handler = new SecurityTransportHandler(network, world.roles, world.grants, world.sessions, credentials, players);
            DiscordAdminAccessService access = new DiscordAdminAccessService(players, world.sessionService,
                    mock(PlayerDisplayService.class), mock(AuthStatusBroadcaster.class), mock(Async.class), world.staff);
            auth = new AdminAuthService(mock(AdminDataRepository.class), world.sessionService,
                    mock(PlayerDisplayService.class), access, mock(AuthStatusBroadcaster.class), world.staff);
            // What one server posts, every server hears - the sender included.
            doAnswer(call -> {
                posted.add(call.getArgument(0));
                return null;
            }).when(network).post(any());
            servers.add(this);
        }
    }

    private final RolesWorld.MutableClock clock = new RolesWorld.MutableClock();
    private final InMemoryGrantStore store = new InMemoryGrantStore();
    private final PlayerDataRepository players = mock(PlayerDataRepository.class);
    private final Map<String, PlayerData> stored = new HashMap<>();
    private final List<Server> servers = new ArrayList<>();
    private final List<Object> posted = new ArrayList<>();
    private NetServer previousNetServer;
    private Log.LogHandler previousLogger;
    private Server main;
    private Server pvp;

    @BeforeEach
    void setUp() {
        previousLogger = Log.logger;
        Log.logger = (level, text) -> { };
        previousNetServer = Vars.netServer;
        Vars.netServer = mock(NetServer.class);
        Vars.netServer.admins = mock(Administration.class);

        when(players.findByUuid(anyString())).thenAnswer(call -> stored.get(call.<String>getArgument(0)));
        when(players.clearCredentials(anyString())).thenReturn(true);
        for (String uuid : List.of(MODERATOR, ADMIN)) {
            PlayerData data = RolesWorld.data(uuid);
            data.discordId = DISCORD_ID;
            stored.put(uuid, data);
        }
        main = new Server("main");
        pvp = new Server("pvp");
    }

    @AfterEach
    void tearDown() {
        Vars.netServer = previousNetServer;
        Log.logger = previousLogger;
    }

    /** Hands every posted announcement to every server, then lets them all finish. */
    private void deliver() {
        List<Object> batch = new ArrayList<>(posted);
        posted.clear();
        for (Object event : batch) {
            for (Server server : servers) {
                if (event instanceof SecurityPermissionsChangedV1 changed) {
                    server.world.sessions.onChanged(changed.playerUuid(), changed.revision());
                }
            }
        }
        servers.forEach(server -> server.world.settle());
    }

    private static SecurityStaffSyncRequestV1 sync(String server, String uuid, boolean complete, String... roleIds) {
        return new SecurityStaffSyncRequestV1(server, "op-" + System.nanoTime(), uuid, DISCORD_ID, List.of(roleIds), complete);
    }

    private SecurityStaffSyncResponseV1 syncResponse(Server server, SecurityStaffSyncRequestV1 request) {
        ArgumentCaptor<SecurityStaffSyncResponseV1> response = ArgumentCaptor.forClass(SecurityStaffSyncResponseV1.class);
        verify(server.network).respond(eq(request), response.capture());
        return response.getValue();
    }

    /** A session that shares the stored player record, as a real one does. */
    private Session join(Server server, String uuid) {
        Session session = server.world.join(uuid);
        session.data = stored.get(uuid);
        return session;
    }

    @Test
    @DisplayName("Moderator through Discord: first login sets the password, mute and kick work, ban and an admin are out of reach, and losing the Discord role ends it on both servers")
    void wholeStory() {
        main.world.grants.addRole(stored.get(ADMIN), "admin", null, null, "owner", Actor.LOCAL_CONSOLE);
        deliver();
        Session onMain = join(main, MODERATOR);
        Session onPvp = pvp.world.join(MODERATOR);
        onPvp.data = stored.get(MODERATOR);
        Session admin = join(main, ADMIN);

        // The bot reports the Discord role to one server.
        SecurityStaffSyncRequestV1 given = sync("main", MODERATOR, true, RolesWorld.DISCORD_MODERATOR);
        main.handler.sync(given);
        deliver();
        assertThat(syncResponse(main, given).changed()).isTrue();
        assertThat(syncResponse(main, given).operationId()).isEqualTo(given.operationId());

        // A Discord role logs nobody in.
        assertThat(main.world.has(onMain, PermissionNodes.MODERATION_MUTE)).isFalse();
        assertThat(pvp.world.staff.mayLogIn(onPvp)).as("the other server heard of it").isTrue();

        // The first login sets the password.
        assertThat(main.auth.authenticate(onMain.player, "first-password").status()).isEqualTo(AuthResultStatus.PASSWORD_CREATED);
        assertThat(stored.get(MODERATOR).password).isNotEmpty();
        assertThat(main.world.has(onMain, PermissionNodes.MODERATION_MUTE)).isTrue();
        assertThat(main.world.has(onMain, PermissionNodes.MODERATION_KICK)).isTrue();
        assertThat(onMain.player.admin).isFalse();
        verify(Vars.netServer.admins, never()).adminPlayer(anyString(), any());

        // No right to ban, and no acting on an admin.
        assertThat(main.world.has(onMain, PermissionNodes.MODERATION_BAN)).isFalse();
        TargetHierarchy hierarchy = new TargetHierarchy(main.world.permissions, main.world.grants,
                () -> main.world.sessionService, null);
        assertThat(hierarchy.mayTarget(onMain, ADMIN)).isFalse();
        assertThat(hierarchy.mayTarget(admin, MODERATOR)).isTrue();

        // The other server asks for its own login: a wrong password gives nothing, the right one does.
        assertThat(pvp.world.has(onPvp, PermissionNodes.MODERATION_MUTE)).isFalse();
        assertThat(pvp.auth.authenticate(onPvp.player, "wrong-password").status()).isEqualTo(AuthResultStatus.WRONG_PASSWORD);
        assertThat(pvp.world.has(onPvp, PermissionNodes.MODERATION_MUTE)).isFalse();
        assertThat(pvp.auth.authenticate(onPvp.player, "first-password").status()).isEqualTo(AuthResultStatus.SUCCESS);
        assertThat(pvp.world.has(onPvp, PermissionNodes.MODERATION_MUTE)).isTrue();

        // The Discord role is taken away; the bot happens to tell the other server.
        SecurityStaffSyncRequestV1 taken = sync("pvp", MODERATOR, true);
        pvp.handler.sync(taken);
        deliver();
        assertThat(syncResponse(pvp, taken).changed()).isTrue();

        // A menu opened before that asks again when its button is pressed, and is refused.
        assertThat(main.world.has(onMain, PermissionNodes.MODERATION_MUTE)).isFalse();
        assertThat(pvp.world.has(onPvp, PermissionNodes.MODERATION_MUTE)).isFalse();
        assertThat(main.world.staff.mayLogIn(onMain)).isFalse();
        assertThat(main.auth.authenticate(onMain.player, "first-password").status())
                .isEqualTo(AuthResultStatus.DISCORD_APPROVAL_REQUIRED);
    }

    @Test
    @DisplayName("A login that finishes after the player reconnected does not log the new connection in")
    void reconnectDuringLogin() {
        main.world.grants.addRole(stored.get(MODERATOR), "moderator", null, null, "r", Actor.LOCAL_CONSOLE);
        Session old = join(main, MODERATOR);
        PlayerData credentials = PlayerData.builder().uuid(MODERATOR).password("").build();
        var verification = main.auth.checkPassword(credentials, "some-password");

        main.world.leave(MODERATOR);
        Session fresh = join(main, MODERATOR);

        assertThat(main.auth.applyLogin(old.player, credentials, verification, false).status())
                .isEqualTo(AuthResultStatus.SESSION_NOT_FOUND);
        assertThat(fresh.staffAuthenticated).isFalse();
        assertThat(old.staffAuthenticated).isFalse();
        assertThat(main.world.has(fresh, PermissionNodes.MODERATION_MUTE)).isFalse();
    }

    @Test
    @DisplayName("An incomplete snapshot takes nothing away; a complete empty one does")
    void incompleteSnapshot() {
        main.handler.sync(sync("main", MODERATOR, true, RolesWorld.DISCORD_MODERATOR));

        SecurityStaffSyncRequestV1 partial = sync("main", MODERATOR, false);
        main.handler.sync(partial);
        assertThat(syncResponse(main, partial).changed()).isFalse();
        assertThat(store.find(MODERATOR).grants()).hasSize(1);

        SecurityStaffSyncRequestV1 full = sync("main", MODERATOR, true);
        main.handler.sync(full);
        assertThat(syncResponse(main, full).changed()).isTrue();
        assertThat(store.find(MODERATOR).grants()).isEmpty();
    }

    @Test
    @DisplayName("The same sync repeated answers with the same revision and announces nothing new")
    void repeatedSync() {
        SecurityStaffSyncRequestV1 first = sync("main", MODERATOR, true, RolesWorld.DISCORD_MODERATOR);
        main.handler.sync(first);
        posted.clear();

        main.handler.sync(first);

        ArgumentCaptor<SecurityStaffSyncResponseV1> responses = ArgumentCaptor.forClass(SecurityStaffSyncResponseV1.class);
        verify(main.network, org.mockito.Mockito.times(2)).respond(eq(first), responses.capture());
        assertThat(responses.getAllValues()).extracting(SecurityStaffSyncResponseV1::revision).containsExactly(1, 1);
        assertThat(responses.getAllValues()).extracting(SecurityStaffSyncResponseV1::changed).containsExactly(true, false);
        assertThat(posted).isEmpty();
    }

    @Test
    @DisplayName("Roles are given only to the linked account, but taken from an unlinked one")
    void link() {
        stored.get(MODERATOR).discordId = "";

        SecurityStaffSyncRequestV1 gives = sync("main", MODERATOR, true, RolesWorld.DISCORD_MODERATOR);
        main.handler.sync(gives);
        verify(main.network).respondError(eq(gives), eq(SecurityTransportHandler.NOT_FOUND), anyString());
        assertThat(store.find(MODERATOR).grants()).isEmpty();

        main.world.grants.syncDiscord(stored.get(MODERATOR), List.of(RolesWorld.DISCORD_MODERATOR), true, Actor.LOCAL_CONSOLE);
        SecurityStaffSyncRequestV1 takes = sync("main", MODERATOR, true);
        main.handler.sync(takes);
        assertThat(syncResponse(main, takes).changed()).isTrue();
        assertThat(store.find(MODERATOR).grants()).isEmpty();
    }

    @Test
    @DisplayName("Unknown player: NOT_FOUND. Store down or roles off: UNAVAILABLE. Another server's request: no answer")
    void errors() {
        SecurityStaffSyncRequestV1 unknown = sync("main", "nobody", true, RolesWorld.DISCORD_MODERATOR);
        main.handler.sync(unknown);
        verify(main.network).respondError(eq(unknown), eq(SecurityTransportHandler.NOT_FOUND), anyString());

        SecurityStaffSyncRequestV1 elsewhere = sync("hub", MODERATOR, true, RolesWorld.DISCORD_MODERATOR);
        main.handler.sync(elsewhere);
        verify(main.network, never()).respond(eq(elsewhere), any());
        verify(main.network, never()).respondError(eq(elsewhere), anyString(), anyString());

        store.failWith(new IllegalStateException("mongo is down"));
        SecurityStaffSyncRequestV1 down = sync("main", MODERATOR, true, RolesWorld.DISCORD_MODERATOR);
        main.handler.sync(down);
        verify(main.network).respondError(eq(down), eq(SecurityTransportHandler.UNAVAILABLE), anyString());
        store.failWith(null);

        NetworkService network = mock(NetworkService.class);
        PermissionRoles off = PermissionRoles.legacy();
        SecurityTransportHandler legacy = new SecurityTransportHandler(network, off, main.world.grants, main.world.sessions,
                mock(StaffCredentials.class), players);
        SecurityStaffSyncRequestV1 toLegacy = sync(off.serverName(), MODERATOR, true, RolesWorld.DISCORD_MODERATOR);
        legacy.sync(toLegacy);
        verify(network).respondError(eq(toLegacy), eq(SecurityTransportHandler.UNAVAILABLE), anyString());
    }

    @Test
    @DisplayName("Password reset: forgets the password, tells every server, logs the connection out, and is safe to repeat")
    void resetPassword() {
        main.world.grants.addRole(stored.get(MODERATOR), "moderator", null, null, "r", Actor.LOCAL_CONSOLE);
        Session session = join(main, MODERATOR);
        main.auth.authenticate(session.player, "first-password");
        assertThat(main.world.has(session, PermissionNodes.MODERATION_MUTE)).isTrue();
        int auditedBefore = main.world.audited.size();

        SecurityStaffResetPasswordRequestV1 request = new SecurityStaffResetPasswordRequestV1("main", "op-1", MODERATOR);
        main.handler.resetPassword(request);

        ArgumentCaptor<SecurityStaffResetPasswordResponseV1> response = ArgumentCaptor.forClass(SecurityStaffResetPasswordResponseV1.class);
        verify(main.network).respond(eq(request), response.capture());
        assertThat(response.getValue().changed()).isTrue();
        verify(players).clearCredentials(MODERATOR);
        assertThat(posted).anySatisfy(event -> assertThat(event).isEqualTo(new PlayerPasswordResetCommandV1(MODERATOR, "main")));
        assertThat(main.world.audited).hasSize(auditedBefore + 1);
        assertThat(main.world.audited.getLast().details().extra)
                .containsEntry("operation", "reset-password")
                .doesNotContainValue(stored.get(MODERATOR).password);

        // What every server does when it hears of the reset.
        DiscordAdminAccessService access = new DiscordAdminAccessService(players, main.world.sessionService,
                mock(PlayerDisplayService.class), mock(AuthStatusBroadcaster.class), mock(Async.class), main.world.staff);
        session.data.password = "";
        access.onPasswordReset(MODERATOR);
        assertThat(main.world.has(session, PermissionNodes.MODERATION_MUTE)).isFalse();

        // The stored record is empty now, so a repeat finds nothing to do.
        stored.get(MODERATOR).deviceTokens.clear();
        stored.get(MODERATOR).deviceTokenHashes.clear();
        SecurityStaffResetPasswordRequestV1 repeat = new SecurityStaffResetPasswordRequestV1("main", "op-1", MODERATOR);
        main.handler.resetPassword(repeat);
        verify(main.network, org.mockito.Mockito.times(2)).respond(eq(repeat), response.capture());
        assertThat(response.getValue().changed()).isFalse();

        SecurityStaffResetPasswordRequestV1 unknown = new SecurityStaffResetPasswordRequestV1("main", "op-2", "nobody");
        main.handler.resetPassword(unknown);
        verify(main.network).respondError(eq(unknown), eq(SecurityTransportHandler.NOT_FOUND), anyString());
    }
}
