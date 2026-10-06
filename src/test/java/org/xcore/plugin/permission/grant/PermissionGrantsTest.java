package org.xcore.plugin.permission.grant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditAppendCommand;
import org.xcore.plugin.model.AuditAppendResult;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.Actor;
import org.xcore.plugin.permission.RolesWorld;
import org.xcore.plugin.permission.grant.PermissionGrants.Change;
import org.xcore.plugin.permission.grant.PermissionGrants.GrantException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PermissionGrantsTest {

    private static final Actor SYNC = new Actor.SystemActor("discord-sync");

    private RolesWorld world;
    private PermissionGrants grants;
    private PlayerData target;
    private final List<String> announced = new ArrayList<>();

    @BeforeEach
    void setUp() {
        world = new RolesWorld();
        grants = world.grants;
        target = RolesWorld.data("uuid-1");
        grants.onChanged((uuid, revision) -> announced.add(uuid + "@" + revision));
    }

    private List<String> held() {
        return world.store.find("uuid-1").grants().stream()
                .map(grant -> (grant.isRole() ? grant.role() : "-" + grant.node()) + " [" + grant.source() + "]")
                .toList();
    }

    @Test
    @DisplayName("A role given by hand is stored, audited and announced")
    void addRole() {
        Change change = grants.addRole(target, "Moderator", null, null, "staff recruitment", Actor.LOCAL_CONSOLE);

        assertThat(change.changed()).isTrue();
        assertThat(change.document().revision()).isEqualTo(1);
        Grant grant = change.added().get(0);
        assertThat(grant.role()).isEqualTo("moderator");
        assertThat(grant.source()).isEqualTo(Grant.SOURCE_MANUAL);
        assertThat(grant.by()).isEqualTo("console@main");
        assertThat(grant.id()).startsWith("g-");
        assertThat(announced).containsExactly("uuid-1@1");

        AuditAppendCommand record = world.audited.get(0);
        assertThat(record.action()).isEqualTo(AuditAction.NOTE);
        assertThat(record.reason()).isEqualTo("staff recruitment");
        assertThat(record.target().uuid).isEqualTo("uuid-1");
        assertThat(record.details().extra)
                .containsEntry("kind", "permission")
                .containsEntry("operation", "role-add")
                .containsEntry("server", "main");
        assertThat(record.details().extra.get("added")).contains("role moderator").contains("[manual]");
    }

    @Test
    @DisplayName("Giving the same role again changes nothing and writes no second audit record")
    void addRole_twice() {
        grants.addRole(target, "moderator", null, null, "first", Actor.LOCAL_CONSOLE);
        Change again = grants.addRole(target, "moderator", null, null, "second", Actor.LOCAL_CONSOLE);

        assertThat(again.changed()).isFalse();
        assertThat(held()).containsExactly("moderator [manual]");
        assertThat(world.audited).hasSize(1);
        assertThat(announced).containsExactly("uuid-1@1");
    }

    @Test
    @DisplayName("An unknown role, an undeclared node and a missing duration are refused")
    void refusals() {
        assertThatThrownBy(() -> grants.addRole(target, "moderatr", null, null, "typo", Actor.LOCAL_CONSOLE))
                .isInstanceOf(GrantException.class).hasMessageContaining("Unknown role");
        assertThatThrownBy(() -> grants.deny(target, "xcore.moderation.bann", null, "typo", Actor.LOCAL_CONSOLE))
                .isInstanceOf(GrantException.class);
        assertThatThrownBy(() -> grants.addRole(target, "moderator", null, Duration.ZERO, "zero", Actor.LOCAL_CONSOLE))
                .isInstanceOf(GrantException.class);
        assertThat(held()).isEmpty();
        assertThat(world.audited).isEmpty();
    }

    @Test
    @DisplayName("A grant that could not be audited is refused")
    void auditFailure() {
        when(world.audit.append(any(), any(AuditAppendCommand.class))).thenReturn(AuditAppendResult.failure("audit is down"));

        assertThatThrownBy(() -> grants.addRole(target, "moderator", null, null, "reason", Actor.LOCAL_CONSOLE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("audit is down");
        assertThat(announced).isEmpty();
    }

    @Test
    @DisplayName("Discord: an incomplete snapshot gives roles but takes nothing away")
    void discord_incomplete() {
        grants.syncDiscord(target, List.of(RolesWorld.DISCORD_MODERATOR, RolesWorld.DISCORD_ADMIN), true, SYNC);

        Change change = grants.syncDiscord(target, List.of(), false, SYNC);

        assertThat(change.changed()).isFalse();
        assertThat(held()).containsExactlyInAnyOrder("moderator [discord:111111111111111111]", "admin [discord:222222222222222222]");
    }

    @Test
    @DisplayName("Discord: a complete and empty snapshot takes the Discord roles away")
    void discord_completeEmpty() {
        grants.syncDiscord(target, List.of(RolesWorld.DISCORD_MODERATOR), true, SYNC);

        Change change = grants.syncDiscord(target, List.of(), true, SYNC);

        assertThat(change.changed()).isTrue();
        assertThat(change.removed()).extracting(Grant::role).containsExactly("moderator");
        assertThat(held()).isEmpty();
    }

    @Test
    @DisplayName("Discord: a role given by hand survives every sync")
    void discord_manualSurvives() {
        grants.addRole(target, "admin", null, null, "trusted", Actor.LOCAL_CONSOLE);
        grants.deny(target, "xcore.moderation.ban", null, "cooldown", Actor.LOCAL_CONSOLE);
        grants.syncDiscord(target, List.of(RolesWorld.DISCORD_MODERATOR), true, SYNC);

        grants.syncDiscord(target, List.of(), true, SYNC);

        assertThat(held()).containsExactly("admin [manual]", "-xcore.moderation.ban [manual]");
    }

    @Test
    @DisplayName("Discord: the same sync sent again changes nothing and keeps the revision")
    void discord_retry() {
        Change first = grants.syncDiscord(target, List.of(RolesWorld.DISCORD_MODERATOR), true, SYNC);
        Change retry = grants.syncDiscord(target, List.of(RolesWorld.DISCORD_MODERATOR), true, SYNC);

        assertThat(first.changed()).isTrue();
        assertThat(retry.changed()).isFalse();
        assertThat(retry.document().revision()).isEqualTo(first.document().revision());
        assertThat(held()).containsExactly("moderator [discord:111111111111111111]");
        assertThat(world.audited).hasSize(1);
    }

    @Test
    @DisplayName("Discord: a role that permissions.toml does not bind is ignored")
    void discord_unbound() {
        Change change = grants.syncDiscord(target, List.of("999"), true, SYNC);

        assertThat(change.changed()).isFalse();
        assertThat(held()).isEmpty();
    }

    @Test
    @DisplayName("A role that comes from Discord cannot be removed by hand")
    void removeDiscordRole() {
        grants.syncDiscord(target, List.of(RolesWorld.DISCORD_MODERATOR), true, SYNC);

        assertThatThrownBy(() -> grants.removeRole(target, "moderator", "no longer", Actor.LOCAL_CONSOLE))
                .isInstanceOf(GrantException.class).hasMessageContaining("Discord");
        assertThat(held()).containsExactly("moderator [discord:111111111111111111]");
    }

    @Test
    @DisplayName("A role is removed by name or by grant id, a denial by grant id")
    void remove() {
        grants.addRole(target, "moderator", null, null, "a", Actor.LOCAL_CONSOLE);
        String adminGrant = grants.addRole(target, "admin", "event", null, "b", Actor.LOCAL_CONSOLE).added().get(0).id();
        String denial = grants.deny(target, "xcore.moderation.*", Duration.ofDays(1), "c", Actor.LOCAL_CONSOLE).added().get(0).id();

        grants.removeRole(target, "moderator", "d", Actor.LOCAL_CONSOLE);
        grants.removeRole(target, adminGrant, "e", Actor.LOCAL_CONSOLE);
        assertThat(held()).containsExactly("-xcore.moderation.* [manual]");

        grants.undeny(target, denial, "f", Actor.LOCAL_CONSOLE);
        assertThat(held()).isEmpty();
        assertThatThrownBy(() -> grants.undeny(target, denial, "again", Actor.LOCAL_CONSOLE)).isInstanceOf(GrantException.class);
    }

    @Test
    @DisplayName("Expired grants stay until pruned or until the next change, and give no weight")
    void expiry() {
        grants.addRole(target, "admin", null, Duration.ofHours(1), "one hour", Actor.LOCAL_CONSOLE);
        grants.addRole(RolesWorld.data("uuid-2"), "admin", null, null, "for good", Actor.LOCAL_CONSOLE);
        assertThat(grants.weightOf("uuid-1")).isEqualTo(50);

        world.clock.advance(Duration.ofHours(2));

        assertThat(grants.weightOf("uuid-1")).isZero();
        assertThat(held()).hasSize(1);
        assertThat(grants.prune()).isEqualTo(1);
        assertThat(held()).isEmpty();
        assertThat(world.store.find("uuid-2").grants()).hasSize(1);
        assertThat(grants.prune()).isZero();
    }

    @Test
    @DisplayName("No more than 64 grants per player")
    void limit() {
        List<Grant> full = new ArrayList<>();
        for (int i = 0; i < GrantDocument.MAX_GRANTS; i++) {
            full.add(new Grant("g-" + i, "moderator", null, true, "server-" + i, null, Grant.SOURCE_MANUAL,
                    "console@main", world.clock.instant(), "filler"));
        }
        world.store.replace(null, "uuid-1", 0, full);

        assertThatThrownBy(() -> grants.addRole(target, "admin", null, null, "one too many", Actor.LOCAL_CONSOLE))
                .isInstanceOf(GrantException.class).hasMessageContaining("64");
    }

    @Test
    @DisplayName("A relayed console is recorded as such, with the server it came from")
    void remoteActor() {
        grants.addRole(target, "moderator", null, null, "relayed", new Actor.RemoteConsole("hub"));

        assertThat(world.store.find("uuid-1").grants().get(0).by()).isEqualTo("remote-console@hub");
        assertThat(world.audited.get(0).actor().nameSnapshot).isEqualTo("remote-console@hub");
        assertThat(world.audited.get(0).actor().serverId).isEqualTo("hub");
    }
}
