package org.xcore.plugin.permission.grant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.Actor;
import org.xcore.plugin.permission.RolesWorld;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionMigrationTest {

    private RolesWorld world;
    private PermissionMigration migration;

    @BeforeEach
    void setUp() {
        world = new RolesWorld();
        PlayerDataRepository players = mock(PlayerDataRepository.class);
        when(players.findDiscordRoleAdmins()).thenReturn(List.of(admin("uuid-1"), admin("uuid-2")));
        migration = new PermissionMigration(players, world.grants, world.roles);
    }

    private static PlayerData admin(String uuid) {
        PlayerData data = RolesWorld.data(uuid);
        data.admin = true;
        data.adminSource = "DISCORD_ROLE";
        data.discordId = "9000000000000000" + uuid.charAt(uuid.length() - 1);
        return data;
    }

    @Test
    @DisplayName("A dry run reports and writes nothing")
    void dryRun() {
        PermissionMigration.Report report = migration.run(true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.candidates()).isEqualTo(2);
        assertThat(report.granted()).isEqualTo(2);
        assertThat(report.lines()).hasSize(2).allSatisfy(line -> assertThat(line).contains("would get admin"));
        assertThat(world.store.find("uuid-1").grants()).isEmpty();
        assertThat(world.store.find("uuid-1").revision()).isZero();
        assertThat(world.audited).isEmpty();
    }

    @Test
    @DisplayName("Applying gives admin, marked as coming from the bound Discord role, and leaves the flag alone")
    void apply() {
        PermissionMigration.Report report = migration.run(false);

        assertThat(report.granted()).isEqualTo(2);
        Grant grant = world.store.find("uuid-1").grants().get(0);
        assertThat(grant.role()).isEqualTo("admin");
        assertThat(grant.source()).isEqualTo(Grant.discordSource(RolesWorld.DISCORD_ADMIN));
        assertThat(grant.by()).isEqualTo("system:migration");
        assertThat(world.audited).hasSize(2);
    }

    @Test
    @DisplayName("What an account merge left behind gets nothing and is reported")
    void mergedAccount() {
        PlayerDataRepository players = mock(PlayerDataRepository.class);
        when(players.findDiscordRoleAdmins()).thenReturn(List.of(admin("uuid-1"), admin("merged:uuid-3")));
        migration = new PermissionMigration(players, world.grants, world.roles);

        PermissionMigration.Report report = migration.run(false);

        assertThat(report.candidates()).isEqualTo(1);
        assertThat(report.granted()).isEqualTo(1);
        assertThat(report.lines()).hasSize(2).anySatisfy(line -> assertThat(line).contains("merged:uuid-3").contains("skipped"));
        assertThat(world.store.find("uuid-1").grants()).hasSize(1);
        assertThat(world.store.find("merged:uuid-3").revision()).isZero();
    }

    @Test
    @DisplayName("A second run gives nothing twice")
    void repeat() {
        migration.run(false);
        PermissionMigration.Report again = migration.run(false);

        assertThat(again.granted()).isZero();
        assertThat(again.alreadyGranted()).isEqualTo(2);
        assertThat(world.store.find("uuid-1").grants()).hasSize(1);
        assertThat(world.store.find("uuid-1").revision()).isEqualTo(1);
        assertThat(world.audited).hasSize(2);
    }

    @Test
    @DisplayName("The bot's next sync finds the moved grant in place, or takes it from somebody who lost the Discord role")
    void thenSync() {
        migration.run(false);

        var kept = world.grants.syncDiscord(RolesWorld.data("uuid-1"), List.of(RolesWorld.DISCORD_ADMIN), true, Actor.LOCAL_CONSOLE);
        var lost = world.grants.syncDiscord(RolesWorld.data("uuid-2"), List.of(), true, Actor.LOCAL_CONSOLE);

        assertThat(kept.changed()).isFalse();
        assertThat(world.store.find("uuid-1").grants()).hasSize(1);
        assertThat(lost.changed()).isTrue();
        assertThat(world.store.find("uuid-2").grants()).isEmpty();
    }
}
