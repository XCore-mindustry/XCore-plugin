package org.xcore.plugin.config;

import arc.files.Fi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xcore.plugin.service.map.inspect.InspectionSettings;
import org.xcore.plugin.service.map.inspect.MapFinding;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MapInspectionConfigTest {
    @TempDir Path directory;

    @Test
    void actualLoaderBindsNonDefaultSnakeCaseDimensionsAndAllThreeSeverities() throws Exception {
        var file = new Fi(directory.resolve("inspection-policy.toml").toFile());
        file.writeString("""
                [maps.checks]
                severities = { "no-core" = "warning", "td-waves-enabled" = "error", "td-road-floor-mixed" = "info" }
                [maps.checks.profiles.survival]
                min_width = 11
                max_width = 101
                min_height = 21
                max_height = 201
                # Old unknown-key convention remains lenient; this typo cannot alter max_width.
                maxWidth = 1
                """);
        var config = new PluginConfigLoader<>(file, TomlSecretsConfig.class, () -> "").load();
        var policy = InspectionSettings.capture(config.maps.checks);
        assertThat(policy.severities()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "no-core", MapFinding.Severity.WARNING, "td-waves-enabled", MapFinding.Severity.ERROR,
                "td-road-floor-mixed", MapFinding.Severity.INFO));
        var profile = policy.profiles().get("survival");
        assertThat(profile.minWidth()).isEqualTo(11); assertThat(profile.maxWidth()).isEqualTo(101);
        assertThat(profile.minHeight()).isEqualTo(21); assertThat(profile.maxHeight()).isEqualTo(201);
    }

    @Test
    void unknownCodesAndLevelsAreIgnoredWhileNegativeAndContradictoryBoundsNormalizeDeterministically() throws Exception {
        var config = PluginConfigLoader.lenientTomlMapper().readValue("""
                [maps.checks]
                severities = { "no-core" = " Warning ", "unknown-check" = "error", "missing-name" = "off", "td-waves-enabled" = "INFO" }
                [maps.checks.profiles.survival]
                min_width = -10
                max_width = -20
                min_height = 50
                max_height = 25
                [maps.checks.profiles.pvp]
                min_width = 80
                max_width = 40
                min_height = -1
                max_height = -1
                """, TomlSecretsConfig.class);
        // Reported once, at load, as one warning per invalid value; start-up continues with the repaired policy.
        assertThat(config.maps.checks.repair()).containsExactlyInAnyOrder(
                "severities: unknown check id \"unknown-check\" ignored",
                "severities.missing-name: unknown severity \"off\" ignored (expected error, warning or info); the default severity applies",
                "profiles.survival.min_width = -10 is negative; that bound is off",
                "profiles.survival.max_width = -20 is negative; that bound is off",
                "profiles.survival: max_height = 25 is below min_height = 50; using max_height = 50",
                "profiles.pvp: max_width = 40 is below min_width = 80; using max_width = 80",
                "profiles.pvp.min_height = -1 is negative; that bound is off",
                "profiles.pvp.max_height = -1 is negative; that bound is off");
        assertThat(config.maps.checks.repair()).isEmpty();
        config.normalize();
        assertThat(config.maps.checks.severities).containsExactlyInAnyOrderEntriesOf(Map.of("no-core", "warning", "td-waves-enabled", "info"));
        var survival = config.maps.checks.profiles.get("survival");
        assertThat(survival.minWidth).isZero(); assertThat(survival.maxWidth).isZero();
        assertThat(survival.minHeight).isEqualTo(50); assertThat(survival.maxHeight).isEqualTo(50);
        var pvp = config.maps.checks.profiles.get("pvp");
        assertThat(pvp.minWidth).isEqualTo(80); assertThat(pvp.maxWidth).isEqualTo(80);
        assertThat(pvp.minHeight).isZero(); assertThat(pvp.maxHeight).isZero();
        var once = InspectionSettings.capture(config.maps.checks);
        config.normalize();
        assertThat(InspectionSettings.capture(config.maps.checks)).isEqualTo(once);
    }

    @Test
    void invalidOverridesNeverPreventTheStartupSecretsLoaderFromLoading() {
        var file = new Fi(directory.resolve("secrets.toml").toFile());
        file.writeString("""
                [maps.checks]
                severities = { "no-such-check" = "error", "no-core" = "fatal", "no-spawn" = 3 }
                [maps.checks.profiles.attack]
                min_width = 300
                max_width = 100
                max_height = -4
                """);
        var checks = ConfigTomlLoader.loadTomlSecretsConfig(directory.toString()).config.maps.checks;
        assertThat(checks.severities).isEmpty();
        var attack = checks.profiles.get("attack");
        assertThat(attack.minWidth).isEqualTo(300); assertThat(attack.maxWidth).isEqualTo(300);
        assertThat(attack.minHeight).isZero(); assertThat(attack.maxHeight).isZero();
        assertThat(InspectionSettings.capture(checks).severities()).isEmpty();
    }

    @Test
    void addingBoundsForAnotherProfileKeepsTheTowerDefenseDefaults() throws Exception {
        var config = PluginConfigLoader.lenientTomlMapper().readValue("""
                [maps.checks.profiles.pvp]
                max_width = 300
                """, TomlSecretsConfig.class);
        config.normalize();
        assertThat(config.maps.checks.profiles.get("pvp").maxWidth).isEqualTo(300);
        var towerDefense = InspectionSettings.capture(config.maps.checks).profiles().get("tower-defense");
        assertThat(towerDefense).isEqualTo(InspectionSettings.capture(new TomlSecretsConfig().maps.checks).profiles().get("tower-defense"));
    }

    @Test
    void absentAndNullPolicyFieldsKeepDefaultsAndCannotRaiseHardSafetyCaps() throws Exception {
        var config = PluginConfigLoader.lenientTomlMapper().readValue("[maps.checks]\n", TomlSecretsConfig.class);
        assertThat(config.maps.checks.severities).isEmpty();
        var defaults = InspectionSettings.capture(config.maps.checks);
        assertThat(defaults.maxFileBytes()).isEqualTo(4194304); assertThat(defaults.maxTiles()).isEqualTo(360000);
        var profile = defaults.profiles().get("tower-defense");
        assertThat(profile.minWidth()).isZero(); assertThat(profile.maxWidth()).isZero();
        assertThat(profile.minHeight()).isZero(); assertThat(profile.maxHeight()).isZero();
        config.maps.checks.severities = null; config.maps.checks.profiles = null;
        config.normalize();
        assertThat(InspectionSettings.capture(config.maps.checks)).isEqualTo(defaults);
        config.maps.checks.maxFileBytes = Integer.MAX_VALUE; config.maps.checks.maxTiles = Integer.MAX_VALUE;
        var capped = InspectionSettings.capture(config.maps.checks);
        assertThat(capped.maxFileBytes()).isEqualTo(InspectionSettings.MAX_INFLATED_BYTES);
        assertThat(capped.maxTiles()).isEqualTo(InspectionSettings.MAX_ALLOCATED_TILES);
    }

    @Test
    void captureCopiesMutableMapsAndProfilesAndLegacyConstructorsPreserveDefaults() {
        var config = new TomlSecretsConfig();
        config.maps.checks.severities.put("no-core", "warning");
        var profile = config.maps.checks.profiles.get("tower-defense"); profile.maxWidth = 200;
        var captured = InspectionSettings.capture(config.maps.checks);
        config.maps.checks.severities.clear(); profile.maxWidth = 1; profile.roadFloor = "changed";
        config.maps.checks.profiles.clear();
        assertThat(captured.severities()).containsExactlyEntriesOf(Map.of("no-core", MapFinding.Severity.WARNING));
        assertThat(captured.profiles().get("tower-defense").maxWidth()).isEqualTo(200);
        assertThat(captured.profiles().get("tower-defense").roadFloor()).isEqualTo("metal-tiles-2");
        assertThatThrownBy(() -> captured.severities().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> captured.profiles().clear()).isInstanceOf(UnsupportedOperationException.class);
        var legacy = new InspectionSettings(4194304, 360000,
                Map.of("tower-defense", new InspectionSettings.Profile("metal-tiles-2", "metal-wall-2", 0.05, true)));
        assertThat(legacy).isEqualTo(InspectionSettings.capture(new TomlSecretsConfig().maps.checks));
    }
}
