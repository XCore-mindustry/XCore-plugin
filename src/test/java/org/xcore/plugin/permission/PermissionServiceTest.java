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
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionServiceTest {

    private static final String STAFF_NODE = PermissionNodes.MODERATION_BAN;
    private static final String CONSOLE_NODE = PermissionNodes.PERMISSIONS_MANAGE;
    private static final String TYPO = "xcore.moderation.bann";

    private SessionService sessions;
    private PermissionService permissions;

    @BeforeEach
    void setUp() {
        sessions = mock(SessionService.class);
        permissions = new PermissionService(() -> sessions);
    }

    private static Player player(String uuid, boolean admin) {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn(uuid);
        player.admin = admin;
        return player;
    }

    private Session registered(Player player) {
        Session session = mock(Session.class);
        session.player = player;
        when(sessions.get(player.uuid())).thenReturn(session);
        return session;
    }

    private XCoreSender console(Actor origin) {
        return new XCoreSender(new MindustrySender.ConsoleSender(), mock(Bundle.class), () -> sessions, origin);
    }

    @Test
    @DisplayName("A staff node follows the admin flag")
    void staffNode() {
        assertThat(permissions.has(player("a", true), STAFF_NODE)).isTrue();
        assertThat(permissions.has(player("b", false), STAFF_NODE)).isFalse();
    }

    @Test
    @DisplayName("An undeclared node is denied to everyone, the local console included")
    void undeclaredNode() {
        assertThat(permissions.has(player("a", true), TYPO)).isFalse();
        assertThat(permissions.has(registered(player("a", true)), TYPO)).isFalse();
        assertThat(permissions.has(console(Actor.LOCAL_CONSOLE), TYPO)).isFalse();
    }

    @Test
    @DisplayName("A console-only node is out of reach of an admin")
    void consoleOnlyNode_player() {
        assertThat(permissions.has(player("a", true), CONSOLE_NODE)).isFalse();
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
        XCoreSender sender = new XCoreSender(new MindustrySender.PlayerSender(player("a", false)),
                mock(Bundle.class), () -> sessions);

        assertThat(permissions.has(sender, "")).isTrue();
    }

    @Test
    @DisplayName("The old 'admin' string is answered like the node it stands for")
    void legacyAlias() {
        assertThat(permissions.has(player("a", true), PermissionNodes.LEGACY_ADMIN)).isTrue();
        assertThat(permissions.has(player("b", false), PermissionNodes.LEGACY_ADMIN)).isFalse();
    }

    @Test
    @DisplayName("A session speaks for its player while the registry holds that very connection")
    void currentSession() {
        Player admin = player("a", true);
        Session session = registered(admin);

        assertThat(permissions.has(session, STAFF_NODE)).isTrue();
    }

    @Test
    @DisplayName("A session rebuilt for the same connection keeps the old one valid")
    void sessionRebuiltForSamePlayer() {
        Player admin = player("a", true);
        Session held = mock(Session.class);
        held.player = admin;
        registered(admin);

        assertThat(permissions.has(held, STAFF_NODE)).isTrue();
    }

    @Test
    @DisplayName("A session kept past a reconnect or a disconnect grants nothing")
    void staleSession() {
        Player before = player("a", true);
        Session held = mock(Session.class);
        held.player = before;

        assertThat(permissions.has(held, STAFF_NODE)).as("left the server").isFalse();

        registered(player("a", true));
        assertThat(permissions.has(held, STAFF_NODE)).as("rejoined as a new connection").isFalse();
        assertThat(permissions.explain(held, STAFF_NODE).reason()).isEqualTo("stale session");
    }

    @Test
    @DisplayName("A closed connection grants nothing")
    void disconnected() {
        Player admin = player("a", true);
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
    }

    @Test
    @DisplayName("require turns a refusal into the access-denied command error")
    void require() {
        Session regular = registered(player("b", false));

        assertThatThrownBy(() -> permissions.require(regular, STAFF_NODE))
                .isInstanceOf(XCoreCommandException.class)
                .hasMessage("error-access-denied");

        permissions.require(registered(player("a", true)), STAFF_NODE);
    }

    @Test
    @DisplayName("Staff can act on themselves and on players, not on other staff")
    void canTarget() {
        Player admin = player("a", true);
        Player otherAdmin = player("c", true);
        Player regular = player("b", false);

        assertThat(permissions.canTarget(admin, admin)).isTrue();
        assertThat(permissions.canTarget(admin, regular)).isTrue();
        assertThat(permissions.canTarget(admin, otherAdmin)).isFalse();
        assertThat(permissions.canTarget(admin, null)).isFalse();
    }

    @Test
    @DisplayName("Without a registry a session is taken at its word")
    void standalone() {
        Session session = mock(Session.class);
        session.player = player("a", true);

        assertThat(new PermissionService().has(session, STAFF_NODE)).isTrue();
    }
}
