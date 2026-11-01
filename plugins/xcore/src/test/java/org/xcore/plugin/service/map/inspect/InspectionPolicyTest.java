package org.xcore.plugin.service.map.inspect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class InspectionPolicyTest {
    @TempDir Path directory;
    private final MapChecks checks = new MapChecks();

    @ParameterizedTest
    @ValueSource(strings = {"error", "warning", "info"})
    void allDocumentedFindingsIncludingTdUseTheFinalConfiguredSeverity(String level) throws Exception {
        for (var scenario : MapChecksTest.findings().toList()) {
            var fixture = new MsavFixture(); scenario.change().accept(fixture);
            var config = new TomlSecretsConfig(); config.maps.checks.severities.put(scenario.code(), level);
            var settings = InspectionSettings.capture(config.maps.checks);
            var snapshot = read(fixture, settings);
            var defaults = checks.check(snapshot, scenario.profile(), MsavFixture.settings());
            var findings = checks.check(snapshot, scenario.profile(), settings);
            assertThat(findings).hasSameSizeAs(defaults);
            for (int i = 0; i < findings.size(); i++) {
                var before = defaults.get(i); var after = findings.get(i);
                assertThat(after.code()).isEqualTo(before.code()); assertThat(after.detail()).isEqualTo(before.detail());
                assertThat(after.x()).isEqualTo(before.x()); assertThat(after.y()).isEqualTo(before.y());
                assertThat(after.count()).isEqualTo(before.count());
                assertThat(after.severity()).isEqualTo(after.code().equals(scenario.code())
                        ? MapFinding.Severity.valueOf(level.toUpperCase(java.util.Locale.ROOT)) : before.severity());
            }
        }
    }

    record Bounds(int minWidth, int maxWidth, int minHeight, int maxHeight, String violation) {}
    static Stream<Bounds> bounds() {
        return Stream.of(new Bounds(6, 0, 0, 0, "width < min_width=6"), new Bounds(0, 4, 0, 0, "width > max_width=4"),
                new Bounds(0, 0, 6, 0, "height < min_height=6"), new Bounds(0, 0, 0, 4, "height > max_height=4"),
                new Bounds(6, 0, 0, 4, "width < min_width=6; height > max_height=4"));
    }

    @ParameterizedTest
    @MethodSource("bounds")
    void profileDimensionsApplyEvenBelowTheTileLimitWithoutMakingInspectionPartial(Bounds bounds) throws Exception {
        var settings = settings(bounds, 360000);
        var snapshot = read(new MsavFixture(), settings);
        assertThat(snapshot.tilesAvailable()).isTrue(); assertThat(snapshot.spawnCount()).isEqualTo(1);
        assertThat(snapshot.coresByTeam()).containsExactlyEntriesOf(Map.of("sharded", 1));
        assertThat(checks.check(snapshot, "survival", settings)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo("size-out-of-range");
            assertThat(finding.severity()).isEqualTo(MapFinding.Severity.ERROR);
            assertThat(finding.detail()).isEqualTo("5 x 5 " + bounds.violation());
        });
        assertThat(checks.check(snapshot, "sandbox", settings)).isEmpty();
    }

    @Test
    void inclusiveEndpointsAndDisabledBoundsPreserveTheExistingChecks() throws Exception {
        var exact = settings(new Bounds(5, 5, 5, 5, ""), 360000);
        var disabled = settings(new Bounds(0, 0, 0, 0, ""), 360000);
        var snapshot = read(new MsavFixture(), exact);
        assertThat(checks.check(snapshot, "survival", exact)).isEmpty();
        assertThat(checks.check(snapshot, "survival", disabled)).isEqualTo(checks.check(snapshot, "survival", MsavFixture.settings()));
    }

    @Test
    void tileLimitStillControlsPartialInspectionAtItsExactBoundaryAndSeverityCannotDisableIt() throws Exception {
        var config = new TomlSecretsConfig(); config.maps.checks.maxTiles = 24;
        config.maps.checks.severities.put("size-out-of-range", "info");
        config.maps.checks.profiles.get("tower-defense").maxWidth = 4;
        var settings = InspectionSettings.capture(config.maps.checks);
        var snapshot = read(new MsavFixture(), settings);
        assertThat(snapshot.tilesAvailable()).isFalse(); assertThat(snapshot.tileCount()).isZero();
        assertThat(snapshot.coresByTeam()).isEmpty();
        assertThat(checks.check(snapshot, "tower-defense", settings)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo("size-out-of-range"); assertThat(finding.severity()).isEqualTo(MapFinding.Severity.INFO);
            assertThat(finding.detail()).isEqualTo("5 x 5 exceeds 24 tiles; tile checks skipped; width > max_width=4");
        });
        for (int limit : new int[]{25, 26}) {
            config.maps.checks.maxTiles = limit; settings = InspectionSettings.capture(config.maps.checks);
            snapshot = read(new MsavFixture(), settings);
            assertThat(snapshot.tilesAvailable()).isTrue(); assertThat(snapshot.tileCount()).isEqualTo(25);
            assertThat(snapshot.coresByTeam()).containsExactlyEntriesOf(Map.of("sharded", 1));
        }
    }

    @Test
    void contradictoryBoundsNormalizeTheSameInConfigAndDetachedSettingsAndNeverRemoveParserCaps() throws Exception {
        var profile = new InspectionSettings.Profile("", "", 0.05, false, -1, -1, 6, 4);
        assertThat(profile.minWidth()).isZero(); assertThat(profile.maxWidth()).isZero();
        assertThat(profile.minHeight()).isEqualTo(6); assertThat(profile.maxHeight()).isEqualTo(6);
        var config = new TomlSecretsConfig();
        var mutable = config.maps.checks.profiles.get("tower-defense"); mutable.minHeight = 6; mutable.maxHeight = 4;
        config.normalize();
        assertThat(InspectionSettings.capture(config.maps.checks).profiles().get("tower-defense").maxHeight()).isEqualTo(6);
        var fixture = new MsavFixture(); fixture.tags.put("width", "65536");
        var settings = settings(new Bounds(0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, ""), 360000);
        assertThatThrownBy(() -> read(fixture, settings)).isInstanceOf(java.io.IOException.class).hasMessageContaining("width");
        assertThatThrownBy(() -> new InspectionSettings(0, 25, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private static InspectionSettings settings(Bounds bounds, int maxTiles) {
        return new InspectionSettings(4194304, maxTiles, Map.of("survival", new InspectionSettings.Profile("", "", 0.05, false,
                bounds.minWidth, bounds.maxWidth, bounds.minHeight, bounds.maxHeight)));
    }
    private MapSnapshot read(MsavFixture fixture, InspectionSettings settings) throws Exception {
        return new DetachedMsavReader().read(fixture.write(directory), "0".repeat(64), MsavFixture.registry(), settings);
    }
}
