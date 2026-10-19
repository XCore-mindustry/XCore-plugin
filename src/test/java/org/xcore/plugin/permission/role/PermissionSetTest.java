package org.xcore.plugin.permission.role;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.permission.PermissionNode;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.permission.grant.Grant;
import org.xcore.plugin.permission.grant.GrantDocument;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The resolution rules of the plan, one scenario each. */
class PermissionSetTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final PermissionModel MODEL = PermissionModelLoader.parse(PermissionModelLoaderTest.SAMPLE);

    private static int nextId;

    static Grant role(String role) {
        return role(role, null, null);
    }

    static Grant role(String role, String server, Instant expiresAt) {
        return new Grant("g-" + nextId++, role, null, true, server, expiresAt, Grant.SOURCE_MANUAL, "console", NOW, "test");
    }

    static Grant node(String node, boolean allow) {
        return node(node, allow, null, null);
    }

    static Grant node(String node, boolean allow, String server, Instant expiresAt) {
        return new Grant("g-" + nextId++, null, node, allow, server, expiresAt, Grant.SOURCE_MANUAL, "console", NOW, "test");
    }

    private static PermissionSet set(Grant... grants) {
        return PermissionSet.compile(MODEL, new GrantDocument("uuid-1", 1, List.of(grants)), "main", false, NOW);
    }

    private static PermissionNode declared(String name) {
        return PermissionNodes.find(name).orElseThrow();
    }

    private static boolean has(PermissionSet set, String node) {
        return set.check(declared(node), true, NOW).granted();
    }

    @Test
    @DisplayName("An exact rule beats a wildcard, whichever way either points")
    void exactBeatsWildcard() {
        PermissionSet admin = set(role("admin"));

        assertThat(has(admin, PermissionNodes.MODERATION_BAN)).isTrue();
        assertThat(has(admin, PermissionNodes.MINDUSTRY_ADMIN)).isTrue();
        // "-xcore.permissions.manage" is exact, and the node is for the local console anyway.
        assertThat(has(admin, PermissionNodes.PERMISSIONS_MANAGE)).isFalse();
        assertThat(has(admin, PermissionNodes.PERMISSIONS_INSPECT)).isTrue();

        PermissionSet narrowed = set(node("xcore.moderation.*", false), node(PermissionNodes.MODERATION_MUTE, true));
        assertThat(has(narrowed, PermissionNodes.MODERATION_MUTE)).isTrue();
        assertThat(has(narrowed, PermissionNodes.MODERATION_KICK)).isFalse();
    }

    @Test
    @DisplayName("A longer wildcard beats a shorter one")
    void longerWildcardWins() {
        PermissionSet set = set(node("xcore.*", true), node("xcore.moderation.*", false));

        assertThat(has(set, PermissionNodes.MODERATION_KICK)).isFalse();
        assertThat(has(set, PermissionNodes.ADMIN_TP)).isTrue();
    }

    @Test
    @DisplayName("A direct denial beats a role, even a wildcard denial against the role's exact rule")
    void directBeatsRole() {
        PermissionSet moderator = set(role("moderator"), node(PermissionNodes.MODERATION_MUTE, false));

        assertThat(has(moderator, PermissionNodes.MODERATION_MUTE)).isFalse();
        assertThat(has(moderator, PermissionNodes.MODERATION_KICK)).isTrue();

        PermissionSet silenced = set(role("moderator"), node("xcore.moderation.*", false));
        assertThat(has(silenced, PermissionNodes.MODERATION_KICK)).isFalse();
    }

    @Test
    @DisplayName("At equal specificity a rule for this server beats a global one, and a denial beats a permission")
    void tieBreaks() {
        PermissionSet serverAllows = set(node(PermissionNodes.MODERATION_BAN, false), node(PermissionNodes.MODERATION_BAN, true, "main", null));
        assertThat(has(serverAllows, PermissionNodes.MODERATION_BAN)).isTrue();

        PermissionSet serverDenies = set(node(PermissionNodes.MODERATION_BAN, true), node(PermissionNodes.MODERATION_BAN, false, "main", null));
        assertThat(has(serverDenies, PermissionNodes.MODERATION_BAN)).isFalse();

        PermissionSet bothGlobal = set(node(PermissionNodes.MODERATION_BAN, true), node(PermissionNodes.MODERATION_BAN, false));
        assertThat(has(bothGlobal, PermissionNodes.MODERATION_BAN)).isFalse();
    }

    @Test
    @DisplayName("A grant for another server gives nothing here")
    void otherServer() {
        PermissionSet set = set(role("admin", "event", null));

        assertThat(has(set, PermissionNodes.MODERATION_BAN)).isFalse();
        assertThat(set.weight(NOW)).isZero();
        assertThat(set.canLogInAsStaff(NOW)).isFalse();
    }

    @Test
    @DisplayName("With nothing matching, a player node is allowed and a staff node is not")
    void defaults() {
        PermissionSet nobody = set();

        assertThat(nobody.check(new PermissionNode("xcore.test.open", org.xcore.plugin.permission.Access.PLAYER, "k"), false, NOW).granted()).isTrue();
        assertThat(has(nobody, PermissionNodes.MODERATION_MUTE)).isFalse();
    }

    @Test
    @DisplayName("A role can take a player node away")
    void playerNodeRevoked() {
        PermissionNode open = new PermissionNode("xcore.moderation.report", org.xcore.plugin.permission.Access.PLAYER, "k");
        PermissionSet set = set(node("xcore.moderation.*", false));

        assertThat(set.check(open, false, NOW).granted()).isFalse();
    }

    @Test
    @DisplayName("One grant running out leaves the other in force, without a reload")
    void expiry() {
        Instant inAnHour = NOW.plus(Duration.ofHours(1));
        PermissionSet set = set(role("admin", null, inAnHour), role("moderator"));

        assertThat(set.check(declared(PermissionNodes.MODERATION_BAN), true, NOW).granted()).isTrue();
        assertThat(set.weight(NOW)).isEqualTo(50);

        // Fresh enough to rule staleness out: only the expiry differs.
        PermissionSet later = set.refreshedAt(inAnHour);
        assertThat(later.check(declared(PermissionNodes.MODERATION_BAN), true, inAnHour).granted()).isFalse();
        assertThat(later.check(declared(PermissionNodes.MODERATION_MUTE), true, inAnHour).granted()).isTrue();
        assertThat(later.weight(inAnHour)).isEqualTo(10);
    }

    @Test
    @DisplayName("A temporary denial ends on time")
    void temporaryDenial() {
        Instant tomorrow = NOW.plus(Duration.ofDays(1));
        PermissionSet set = set(role("admin"), node(PermissionNodes.MODERATION_BAN, false, null, tomorrow)).refreshedAt(tomorrow);

        assertThat(set.check(declared(PermissionNodes.MODERATION_BAN), true, tomorrow.minusSeconds(1)).granted()).isFalse();
        assertThat(set.check(declared(PermissionNodes.MODERATION_BAN), true, tomorrow).granted()).isTrue();
    }

    @Test
    @DisplayName("A role without a login gives no staff node; the reason says so")
    void loginRequired() {
        PermissionSet moderator = set(role("moderator"));

        PermissionSet.Decision decision = moderator.check(declared(PermissionNodes.MODERATION_MUTE), false, NOW);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).contains("not logged in");
        assertThat(moderator.canLogInAsStaff(NOW)).isTrue();
    }

    @Test
    @DisplayName("Rights work for 5 minutes without a refresh, staff is off after 20, denials always hold")
    void staleness() {
        PermissionNode open = new PermissionNode("xcore.test.open", org.xcore.plugin.permission.Access.PLAYER, "k");
        PermissionNode revoked = new PermissionNode("xcore.moderation.report", org.xcore.plugin.permission.Access.PLAYER, "k");
        PermissionSet set = set(role("moderator"), node(PermissionNodes.MODERATION_KICK, false), node("xcore.moderation.report", false));

        Instant after5 = NOW.plus(Duration.ofMinutes(5));
        assertThat(set.check(declared(PermissionNodes.MODERATION_MUTE), true, after5).granted()).isTrue();
        assertThat(set.check(declared(PermissionNodes.MODERATION_KICK), true, after5).granted()).isFalse();

        Instant after20 = NOW.plus(Duration.ofMinutes(20));
        PermissionSet.Decision stale = set.check(declared(PermissionNodes.MODERATION_MUTE), true, after20);
        assertThat(stale.granted()).isFalse();
        assertThat(stale.reason()).contains("could not be refreshed");
        assertThat(set.check(declared(PermissionNodes.MODERATION_KICK), true, after20).granted()).isFalse();
        assertThat(set.check(revoked, true, after20).granted()).isFalse();
        assertThat(set.check(open, true, after20).granted()).isTrue();

        assertThat(set.refreshedAt(after20).check(declared(PermissionNodes.MODERATION_MUTE), true, after20).granted()).isTrue();
    }

    @Test
    @DisplayName("Nothing gives a player a console-only node, not even '*'")
    void consoleOnly() {
        PermissionSet everything = set(node("*", true), node(PermissionNodes.PERMISSIONS_MANAGE, true));

        assertThat(has(everything, PermissionNodes.PERMISSIONS_MANAGE)).isFalse();
        assertThat(has(everything, PermissionNodes.MODERATION_BAN)).isTrue();
    }

    @Test
    @DisplayName("The weight is the highest among the roles held; a role missing from the file counts for nothing")
    void weight() {
        assertThat(set().weight(NOW)).isZero();
        assertThat(set(role("moderator"), role("event-organizer")).weight(NOW)).isEqualTo(10);
        assertThat(set(role("moderator"), role("admin")).weight(NOW)).isEqualTo(50);

        PermissionSet removed = set(role("retired-role"));
        assertThat(removed.weight(NOW)).isZero();
        assertThat(has(removed, PermissionNodes.MODERATION_MUTE)).isFalse();
    }

    @Test
    @DisplayName("A trusted native admin holds the admin role on this server only")
    void nativeAdmin() {
        PermissionSet trusted = PermissionSet.compile(MODEL, GrantDocument.empty("uuid-1"), "main", true, NOW);

        assertThat(has(trusted, PermissionNodes.MINDUSTRY_ADMIN)).isTrue();
        assertThat(trusted.weight(NOW)).isEqualTo(50);

        PermissionSet untrusted = PermissionSet.compile(MODEL, GrantDocument.empty("uuid-1"), "main", false, NOW);
        assertThat(has(untrusted, PermissionNodes.MINDUSTRY_ADMIN)).isFalse();
    }

    @Test
    @DisplayName("Before the grants are read the placeholder gives staff nothing")
    void placeholder() {
        assertThat(PermissionSet.EMPTY.isPlaceholder()).isTrue();
        assertThat(has(PermissionSet.EMPTY, PermissionNodes.MODERATION_MUTE)).isFalse();
        assertThat(PermissionSet.EMPTY.canLogInAsStaff(NOW)).isFalse();
    }
}
