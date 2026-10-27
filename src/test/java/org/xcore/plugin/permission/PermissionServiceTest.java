package org.xcore.plugin.permission;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.permission.grant.InMemoryGrantStore;
import org.xcore.plugin.session.Session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PermissionServiceTest {

    private static final String STAFF_NODE = PermissionNodes.MODERATION_BAN;
    private static final String CONSOLE_NODE = PermissionNodes.PERMISSIONS_MANAGE;
    private static final String TYPO = "xcore.moderation.bann";

    private RolesWorld world;
    private PermissionService permissions;

    @BeforeEach
    void setUp() {
        // The game's admin list is trusted, so an entry of it joins logged in with the role admin.
        world = new RolesWorld("main", new RolesWorld.MutableClock(), new InMemoryGrantStore(), true);
        permissions = world.permissions;
    }

    /** A connected admin, logged in as staff. */
    private Session staff(String uuid) {
        return world.join(uuid, true);
    }

    /** A connected player without any role. */
    private Session regular(String uuid) {
        return world.join(uuid);
    }

    private XCoreSender console(Actor origin) {
        return new XCoreSender(new MindustrySender.ConsoleSender(), mock(Bundle.class), () -> world.sessionService, origin);
    }

    @Test
    @DisplayName("A staff node follows the roles of a logged-in session")
    void staffNode() {
        assertThat(permissions.has(staff("a").player, STAFF_NODE)).isTrue();
        assertThat(permissions.has(regular("b").player, STAFF_NODE)).isFalse();
    }

    @Test
    @DisplayName("A staff role gives nothing until its holder logs in")
    void staffNode_needsLogin() {
        world.grants.addRole(RolesWorld.data("c"), "admin", null, null, "r", Actor.LOCAL_CONSOLE);
        Session holder = world.join("c");

        assertThat(permissions.has(holder, STAFF_NODE)).isFalse();

        world.staff.logIn(holder);
        assertThat(permissions.has(holder, STAFF_NODE)).isTrue();
    }

    @Test
    @DisplayName("The game's admin flag gives nothing by itself")
    void adminFlagAlone() {
        Session session = regular("b");
        session.player.admin = true;

        assertThat(permissions.has(session, STAFF_NODE)).isFalse();
    }

    @Test
    @DisplayName("An undeclared node is denied to everyone, the local console included")
    void undeclaredNode() {
        Session admin = staff("a");

        assertThat(permissions.has(admin.player, TYPO)).isFalse();
        assertThat(permissions.has(admin, TYPO)).isFalse();
        assertThat(permissions.has(console(Actor.LOCAL_CONSOLE), TYPO)).isFalse();
    }

    @Test
    @DisplayName("A console-only node is out of reach of an admin")
    void consoleOnlyNode_player() {
        assertThat(permissions.has(staff("a").player, CONSOLE_NODE)).isFalse();
    }

    @Test
    @DisplayName("The local console has every declared node")
    void localConsole() {
        XCoreSender local = console(Actor.LOCAL_CONSOLE);

        for (PermissionNode node : PermissionNodes.all()) {
            assertThat(permissions.has(local, node.name())).as(node.name()).isTrue();
        }
    }

    @Test
    @DisplayName("A relayed console command has the staff nodes but not the console-only ones")
    void remoteConsole() {
        XCoreSender remote = console(new Actor.RemoteConsole("hub"));

        assertThat(permissions.has(remote, STAFF_NODE)).isTrue();
        assertThat(permissions.has(remote, CONSOLE_NODE)).isFalse();
    }

    @Test
    @DisplayName("A command that asks for nothing is open to everyone")
    void emptyPermission() {
        XCoreSender sender = new XCoreSender(new MindustrySender.PlayerSender(regular("a").player),
                mock(Bundle.class), () -> world.sessionService);

        assertThat(permissions.has(sender, "")).isTrue();
    }

    @Test
    @DisplayName("The old 'admin' string is answered like the node it stands for")
    void legacyAlias() {
        assertThat(permissions.has(staff("a").player, PermissionNodes.LEGACY_ADMIN)).isTrue();
        assertThat(permissions.has(regular("b").player, PermissionNodes.LEGACY_ADMIN)).isFalse();
    }

    @Test
    @DisplayName("A session speaks for its player while the registry holds that very connection")
    void currentSession() {
        assertThat(permissions.has(staff("a"), STAFF_NODE)).isTrue();
    }

    @Test
    @DisplayName("A session rebuilt for the same connection keeps the old one valid")
    void sessionRebuiltForSamePlayer() {
        Session registered = staff("a");
        Session held = mock(Session.class);
        held.player = registered.player;

        assertThat(permissions.has(held, STAFF_NODE)).isTrue();
    }

    @Test
    @DisplayName("A session kept past a reconnect or a disconnect grants nothing")
    void staleSession() {
        Session held = staff("a");
        world.leave("a");

        assertThat(permissions.has(held, STAFF_NODE)).as("left the server").isFalse();

        staff("a");
        assertThat(permissions.has(held, STAFF_NODE)).as("rejoined as a new connection").isFalse();
        assertThat(permissions.explain(held, STAFF_NODE).reason()).isEqualTo("stale session");
    }

    @Test
    @DisplayName("A closed connection grants nothing")
    void disconnected() {
        Player admin = staff("a").player;
        admin.con = mock(NetConnection.class);
        admin.con.hasDisconnected = true;

        assertThat(permissions.has(admin, STAFF_NODE)).isFalse();
    }

    @Test
    @DisplayName("Nobody, and no session, has nothing")
    void absent() {
        assertThat(permissions.has((Player) null, STAFF_NODE)).isFalse();
        assertThat(permissions.has((Session) null, STAFF_NODE)).isFalse();
        assertThat(permissions.has(mock(Session.class), STAFF_NODE)).isFalse();
        assertThat(permissions.has(RolesWorld.player("not-joined"), STAFF_NODE)).isFalse();
    }

    @Test
    @DisplayName("require turns a refusal into the access-denied command error")
    void require() {
        Session regular = regular("b");

        assertThatThrownBy(() -> permissions.require(regular, STAFF_NODE))
                .isInstanceOf(XCoreCommandException.class)
                .hasMessage("error-access-denied");

        permissions.require(staff("a"), STAFF_NODE);
    }

    @Test
    @DisplayName("Staff can act on themselves and on players, not on other staff of the same weight")
    void canTarget() {
        Player admin = staff("a").player;
        Player otherAdmin = staff("c").player;
        Player regular = regular("b").player;

        assertThat(permissions.canTarget(admin, admin)).isTrue();
        assertThat(permissions.canTarget(admin, regular)).isTrue();
        assertThat(permissions.canTarget(admin, otherAdmin)).isFalse();
        assertThat(permissions.canTarget(regular, admin)).isFalse();
        assertThat(permissions.canTarget(admin, null)).isFalse();
    }

    @Test
    @DisplayName("Without a registry a session is taken at its word")
    void standalone() {
        Session session = mock(Session.class);
        session.player = RolesWorld.player("a");
        // A standalone service reads the wall clock, so the set is loaded now.
        RolesWorld.asAdmin(session, java.time.Instant.now());

        assertThat(new PermissionService().has(session, STAFF_NODE)).isTrue();

        session.staffAuthenticated = false;
        assertThat(new PermissionService().has(session, STAFF_NODE)).isFalse();
    }
}
