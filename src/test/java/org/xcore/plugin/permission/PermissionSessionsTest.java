package org.xcore.plugin.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionSessionsTest {

    private static final String MUTE = PermissionNodes.MODERATION_MUTE;
    private static final String BAN = PermissionNodes.MODERATION_BAN;
    private static final String VOTE = PermissionNodes.MODERATION_AUDIT_OTHERS;

    private RolesWorld world;
    private PlayerData target;

    @BeforeEach
    void setUp() {
        world = new RolesWorld();
        target = RolesWorld.data("uuid-1");
    }

    @Test
    @DisplayName("A role without a login gives no staff node; the login turns it on and the logout off")
    void login() {
        world.grants.addRole(target, "moderator", null, null, "r", Actor.LOCAL_CONSOLE);
        Session session = world.join("uuid-1");

        assertThat(world.staff.mayLogIn(session)).isTrue();
        assertThat(world.has(session, MUTE)).isFalse();
        assertThat(world.permissions.explain(session, MUTE).reason()).contains("not logged in");

        world.staff.logIn(session);
        assertThat(world.has(session, MUTE)).isTrue();
        assertThat(world.has(session, BAN)).isFalse();
        assertThat(session.player.admin).as("a moderator is not a game admin").isFalse();

        world.staff.logOut(session);
        assertThat(world.has(session, MUTE)).isFalse();
    }

    @Test
    @DisplayName("Somebody without a staff role cannot log in, and the admin flag is computed from the grants")
    void adminFlag() {
        Session nobody = world.join("uuid-2");
        assertThat(world.staff.mayLogIn(nobody)).isFalse();

        world.grants.addRole(target, "admin", null, null, "r", Actor.LOCAL_CONSOLE);
        Session admin = world.join("uuid-1");
        assertThat(admin.player.admin).isFalse();

        world.staff.logIn(admin);
        assertThat(admin.player.admin).isTrue();

        world.staff.logOut(admin);
        assertThat(admin.player.admin).isFalse();
    }

    @Test
    @DisplayName("A new connection starts logged out, whatever the previous one was")
    void reconnect() {
        world.grants.addRole(target, "moderator", null, null, "r", Actor.LOCAL_CONSOLE);
        Session first = world.join("uuid-1");
        world.staff.logIn(first);

        world.leave("uuid-1");
        Session second = world.join("uuid-1");

        assertThat(world.has(second, MUTE)).isFalse();
        assertThat(world.has(first, MUTE)).as("the old session speaks for nobody").isFalse();
    }

    @Test
    @DisplayName("Revisions 12 and 11: the older announcement is ignored")
    void staleAnnouncement() {
        Session session = world.join("uuid-1");
        world.staff.logIn(session);
        world.grants.addRole(target, "moderator", null, null, "r", Actor.LOCAL_CONSOLE);
        long newer = world.store.find("uuid-1").revision();

        world.sessions.onChanged("uuid-1", newer);
        world.settle();
        assertThat(session.permissionSet.revision()).isEqualTo(newer);

        world.sessions.onChanged("uuid-1", newer - 1);
        world.main.runQueued();
        assertThat(world.io.size()).as("nothing is read for an old revision").isZero();
        assertThat(session.permissionSet.revision()).isEqualTo(newer);
    }

    @Test
    @DisplayName("A set read before a newer change does not replace the newer one")
    void slowRead() {
        Session session = world.join("uuid-1");
        world.grants.addRole(target, "moderator", null, null, "r", Actor.LOCAL_CONSOLE);

        // The periodic reread reads revision 1; its result is on its way to the game thread.
        world.sessions.refreshAll();
        world.io.runQueued();
        Runnable olderRead = world.main.take();

        // Meanwhile revision 2 is written, announced and applied.
        world.grants.addRole(target, "admin", null, null, "r", Actor.LOCAL_CONSOLE);
        world.sessions.onChanged("uuid-1", 2);
        world.settle();
        assertThat(session.permissionSet.revision()).isEqualTo(2);

        olderRead.run();

        assertThat(session.permissionSet.revision()).isEqualTo(2);
        assertThat(session.permissionSet.weight(world.clock.instant())).isEqualTo(50);
    }

    @Test
    @DisplayName("A duplicate announcement reads nothing")
    void duplicate() {
        Session session = world.join("uuid-1");
        world.grants.addRole(target, "moderator", null, null, "r", Actor.LOCAL_CONSOLE);
        world.sessions.onChanged("uuid-1", 1);
        world.settle();

        world.sessions.onChanged("uuid-1", 1);
        world.main.runQueued();

        assertThat(world.io.size()).isZero();
        assertThat(session.permissionSet.revision()).isEqualTo(1);
    }

    @Test
    @DisplayName("A lost announcement is caught by the periodic reread")
    void lostAnnouncement() {
        Session session = world.join("uuid-1");
        world.staff.logIn(session);
        world.grants.addRole(target, "moderator", null, null, "r", Actor.LOCAL_CONSOLE);
        assertThat(world.has(session, MUTE)).isFalse();

        world.sessions.refreshAll();
        world.settle();

        assertThat(world.has(session, MUTE)).as("logging in before the role arrived gives nothing").isFalse();
        world.staff.logIn(session);
        assertThat(world.has(session, MUTE)).isTrue();
    }

    @Test
    @DisplayName("Taking the role away ends the rights and the login, here and now")
    void revoke() {
        world.grants.addRole(target, "admin", null, null, "r", Actor.LOCAL_CONSOLE);
        Session session = world.join("uuid-1");
        world.staff.logIn(session);
        assertThat(session.player.admin).isTrue();

        world.grants.removeRole(target, "admin", "gone", Actor.LOCAL_CONSOLE);
        world.sessions.onChanged("uuid-1", 2);
        world.settle();

        assertThat(world.has(session, BAN)).isFalse();
        assertThat(session.player.admin).isFalse();
        assertThat(session.staffAuthenticated).isFalse();

        // Getting the role back does not hand the rights to whoever is connected.
        world.grants.addRole(target, "admin", null, null, "back", Actor.LOCAL_CONSOLE);
        world.sessions.onChanged("uuid-1", 3);
        world.settle();
        assertThat(world.has(session, BAN)).isFalse();
    }

    @Test
    @DisplayName("Store down for 5 minutes: rights work. For 20: staff nodes are off, denials hold. Back up: rights return")
    void outage() {
        world.grants.addRole(target, "admin", null, null, "r", Actor.LOCAL_CONSOLE);
        world.grants.deny(target, VOTE, null, "restricted", Actor.LOCAL_CONSOLE);
        Session session = world.join("uuid-1");
        world.staff.logIn(session);
        assertThat(world.has(session, BAN)).isTrue();
        assertThat(world.has(session, VOTE)).isFalse();

        world.store.failWith(new IllegalStateException("mongo is down"));

        world.clock.advance(Duration.ofMinutes(5));
        world.sessions.refreshAll();
        world.settle();
        assertThat(world.has(session, BAN)).isTrue();
        assertThat(session.player.admin).isTrue();
        assertThat(world.has(session, VOTE)).isFalse();

        world.clock.advance(Duration.ofMinutes(15));
        world.sessions.refreshAll();
        world.settle();
        assertThat(world.has(session, BAN)).isFalse();
        assertThat(world.permissions.explain(session, BAN).reason()).contains("could not be refreshed");
        assertThat(session.player.admin).as("the admin flag follows").isFalse();
        assertThat(world.has(session, VOTE)).as("a staff denial holds").isFalse();
        assertThat(world.permissions.explain(session, VOTE).reason()).as("and says so").contains("denied by");
        assertThat(session.staffAuthenticated).as("the login is kept for when the store is back").isTrue();

        world.store.failWith(null);
        world.sessions.refreshAll();
        world.settle();
        assertThat(world.has(session, BAN)).isTrue();
        assertThat(session.player.admin).isTrue();
        assertThat(world.has(session, VOTE)).isFalse();
    }

    @Test
    @DisplayName("One grant runs out while another stays, without anything being reread")
    void expiry() {
        world.grants.addRole(target, "moderator", null, null, "for good", Actor.LOCAL_CONSOLE);
        world.grants.addRole(target, "admin", null, Duration.ofHours(1), "one hour", Actor.LOCAL_CONSOLE);
        Session session = world.join("uuid-1");
        world.staff.logIn(session);
        assertThat(world.has(session, BAN)).isTrue();

        world.clock.advance(Duration.ofMinutes(61));
        // Keep the set fresh, so that only the expiry is at work.
        session.permissionSet = session.permissionSet.refreshedAt(world.clock.instant());

        assertThat(world.has(session, BAN)).isFalse();
        assertThat(world.has(session, MUTE)).isTrue();
        assertThat(world.permissions.weight(session.player)).isEqualTo(10);
    }

    @Test
    @DisplayName("The game's admin list gives nothing, unless this server is told to trust it")
    void nativeAdmins() {
        Session untrusted = world.join("uuid-1", true);
        assertThat(world.has(untrusted, BAN)).isFalse();
        assertThat(untrusted.player.admin).isFalse();

        RolesWorld dev = new RolesWorld("dev", world.clock, world.store, true);
        Session trusted = dev.join("uuid-1", true);
        assertThat(dev.has(trusted, BAN)).isTrue();
        assertThat(trusted.player.admin).isTrue();
        assertThat(dev.permissions.weight(trusted.player)).isEqualTo(50);

        // And it survives a reread, which knows nothing of the game's list.
        dev.sessions.refreshAll();
        dev.settle();
        assertThat(dev.has(trusted, BAN)).isTrue();
    }

    @Test
    @DisplayName("Hierarchy: a moderator cannot act on an admin, an admin can on a moderator, nobody on an equal")
    void hierarchy() {
        world.grants.addRole(RolesWorld.data("mod"), "moderator", null, null, "r", Actor.LOCAL_CONSOLE);
        world.grants.addRole(RolesWorld.data("admin-1"), "admin", null, null, "r", Actor.LOCAL_CONSOLE);
        world.grants.addRole(RolesWorld.data("admin-2"), "admin", null, null, "r", Actor.LOCAL_CONSOLE);
        Session mod = world.join("mod");
        Session admin = world.join("admin-1");
        Session other = world.join("admin-2");
        Session player = world.join("player");

        assertThat(world.permissions.canTarget(mod.player, admin.player)).isFalse();
        assertThat(world.permissions.canTarget(admin.player, mod.player)).isTrue();
        assertThat(world.permissions.canTarget(admin.player, other.player)).isFalse();
        assertThat(world.permissions.canTarget(mod.player, player.player)).isTrue();
        assertThat(world.permissions.canTarget(mod.player, mod.player)).isTrue();

        // Offline targets: the weight comes from the store.
        TargetHierarchy hierarchy = new TargetHierarchy(world.permissions, world.grants, () -> world.sessionService, null);
        world.leave("admin-1");
        world.leave("player");
        assertThat(hierarchy.mayTarget(mod, "admin-1")).isFalse();
        assertThat(hierarchy.mayTarget(mod, "player")).isTrue();
        assertThat(hierarchy.mayTarget(other, "admin-1")).isFalse();
        assertThat(hierarchy.mayTarget(other, "mod")).isTrue();
    }
}
