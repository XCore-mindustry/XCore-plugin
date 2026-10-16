package org.xcore.plugin.service.map.inspect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.xcore.plugin.service.map.inspect.MapFinding.Severity.*;

class MapChecksTest {
    @TempDir Path directory;
    private final MapChecks checks = new MapChecks();

    record Scenario(String code, MapFinding.Severity severity, String profile, Consumer<MsavFixture> change) {}
    static Stream<Scenario> findings() {
        return Stream.of(
                new Scenario("missing-name", WARNING, "survival", f -> f.tags.remove("name")),
                new Scenario("missing-author", WARNING, "survival", f -> f.tags.put("author", "[scarlet][]")),
                new Scenario("size-out-of-range", ERROR, "survival", f -> { f.tags.put("width", "1000"); f.tags.put("height", "1000"); f.width = 1000; f.height = 1000; }),
                new Scenario("requires-mods", ERROR, "survival", f -> f.tags.put("mods", "[\"mod-alpha:1.2\"]")),
                new Scenario("unknown-content", ERROR, "survival", f -> f.names.add("missing-mod-block")),
                new Scenario("no-core", ERROR, "survival", f -> f.teams[14] = 2),
                new Scenario("no-spawn", ERROR, "survival", f -> f.overlays[10] = 0),
                new Scenario("pvp-single-team", ERROR, "pvp", f -> {}),
                new Scenario("attack-no-enemy-core", ERROR, "attack", f -> {}),
                new Scenario("td-road-floor-mixed", ERROR, "tower-defense", f -> { f.overlays[11] = 2; f.floors[11] = 6; }),
                new Scenario("td-road-floor-unexpected", WARNING, "tower-defense", f -> { for (int i = 10; i < 14; i++) f.floors[i] = 6; }),
                new Scenario("td-road-disconnected", ERROR, "tower-defense", f -> f.floors[12] = 1),
                new Scenario("td-spawn-not-at-road-start", WARNING, "tower-defense", f -> { f.overlays[10] = 0; f.overlays[11] = 2; }),
                new Scenario("td-border-wall-missing", WARNING, "tower-defense", f -> Arrays.fill(f.blocks, 5, 9, 0)),
                new Scenario("td-drop-zone-radius", WARNING, "tower-defense", f -> f.tags.put("rules", "{waves:false,waveTimer:false,dropZoneRadius:300}")),
                new Scenario("td-waves-enabled", INFO, "tower-defense", f -> f.tags.put("rules", "{waves:true,waveTimer:false,dropZoneRadius:0}")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("findings")
    void eachFindingHasAWireFixtureAndDocumentedSeverity(Scenario scenario) throws Exception {
        var fixture = new MsavFixture();
        scenario.change.accept(fixture);
        var snapshot = read(fixture, MsavFixture.settings());
        var findings = checks.check(snapshot, scenario.profile, MsavFixture.settings());
        assertThat(findings).filteredOn(f -> f.code().equals(scenario.code)).singleElement()
                .satisfies(f -> assertThat(f.severity()).isEqualTo(scenario.severity));
        assertThat(checks.check(snapshot, scenario.profile, MsavFixture.settings())).isEqualTo(findings);
        assertThat(findings).allSatisfy(f -> assertThat(f.detail()).hasSizeLessThanOrEqualTo(300));
    }

    @Test
    void cleanTowerDefenseFixtureHasNoFindings() throws Exception {
        assertThat(checks.check(read(new MsavFixture(), MsavFixture.settings()), "tower-defense", MsavFixture.settings())).isEmpty();
    }

    @Test
    void oversizedSnapshotMakesNoClaimsAboutCoresOrSpawns() throws Exception {
        var settings = new InspectionSettings(4194304, 24, Map.of());
        var snapshot = read(new MsavFixture(), settings);
        assertThat(snapshot.tilesAvailable()).isFalse();
        assertThat(snapshot.tileCount()).isZero();
        assertThat(checks.check(snapshot, "survival", settings)).extracting(MapFinding::code).containsExactly("size-out-of-range");
    }

    @Test
    void missingProfileDataAndEmptyConstantsRespectTheActualConfig() throws Exception {
        var fixture = new MsavFixture();
        Arrays.fill(fixture.blocks, 5, 9, 0);
        for (int i = 10; i < 14; i++) fixture.floors[i] = 6;
        fixture.tags.put("rules", "{waves:false,waveTimer:false,dropZoneRadius:300}");
        var empty = new InspectionSettings(4194304, 360000, Map.of());
        var disabled = new InspectionSettings(4194304, 360000,
                Map.of("tower-defense", new InspectionSettings.Profile("", "", 0.05, false)));
        var snapshot = read(fixture, empty);
        assertThat(checks.check(snapshot, "tower-defense", empty)).isEmpty();
        assertThat(checks.check(snapshot, "tower-defense", disabled)).isEmpty();
        assertThat(checks.check(snapshot, "custom-profile", MsavFixture.settings())).isEmpty();
        assertThat(MapChecks.profile(snapshot, null)).isEqualTo("survival");
        assertThat(MapChecks.profile(snapshot, "tower-defense")).isEqualTo("tower-defense");
    }

    @Test
    void configuredNumericIdsAndAliasesResolveLikeNames() throws Exception {
        var fixture = new MsavFixture();
        for (int i = 10; i < 14; i++) fixture.floors[i] = 6;
        var settings = new InspectionSettings(4194304, 360000,
                Map.of("tower-defense", new InspectionSettings.Profile("sand", "40", 0.05, true)));
        assertThat(checks.check(read(fixture, settings), "tower-defense", settings)).isEmpty();
    }

    @Test
    void borderToleranceUsesUniqueBorderTilesAndReportsFirstFiveInRowOrder() throws Exception {
        var fixture = new MsavFixture();
        Arrays.fill(fixture.blocks, 5, 9, 0); Arrays.fill(fixture.blocks, 15, 19, 0);
        var snapshot = read(fixture, MsavFixture.settings());
        var finding = checks.check(snapshot, "tower-defense", MsavFixture.settings()).getFirst();
        assertThat(finding.code()).isEqualTo("td-border-wall-missing");
        assertThat(finding.count()).isEqualTo(6);
        assertThat(finding.x()).isEqualTo(0); assertThat(finding.y()).isEqualTo(1);
        assertThat(finding.detail()).startsWith("(0,1), (1,1), (2,1), (0,3), (1,3):").doesNotContain("(2,3)");
        var tolerance = new InspectionSettings(4194304, 360000,
                Map.of("tower-defense", new InspectionSettings.Profile("metal-tiles-2", "metal-wall-2", 1, true)));
        assertThat(checks.check(snapshot, "tower-defense", tolerance)).isEmpty();
    }

    @Test
    void roadMustReachTheDefaultTeamsCoreFootprintAndCannotWrapAcrossRows() throws Exception {
        var fixture = new MsavFixture();
        fixture.teams[14] = 2;
        assertThat(checks.check(read(fixture, MsavFixture.settings()), "tower-defense", MsavFixture.settings()))
                .extracting(MapFinding::code).contains("no-core", "td-road-disconnected");
        fixture = new MsavFixture();
        Arrays.fill(fixture.floors, 1); fixture.floors[9] = 3; fixture.floors[10] = 3;
        fixture.overlays[10] = 0; fixture.overlays[9] = 2;
        fixture.blocks[14] = 0; fixture.blocks[11] = 5; fixture.teams[11] = 1;
        assertThat(checks.check(read(fixture, MsavFixture.settings()), "tower-defense", MsavFixture.settings()))
                .extracting(MapFinding::code).contains("td-road-disconnected");
    }

    @Test
    void noEmptyWavesFindingIsFabricatedAndWaveTimerAloneIsInformational() throws Exception {
        var fixture = new MsavFixture();
        fixture.tags.put("rules", "{waves:false,waveTimer:true,dropZoneRadius:0,spawns:[]}");
        var snapshot = read(fixture, MsavFixture.settings());
        assertThat(snapshot.metadata().rules().spawnGroupCount()).isZero();
        assertThat(checks.check(snapshot, "tower-defense", MsavFixture.settings())).singleElement()
                .satisfies(f -> { assertThat(f.code()).isEqualTo("td-waves-enabled"); assertThat(f.severity()).isEqualTo(INFO); });
    }

    @Test
    void diagnosticsBoundAndSanitizeUntrustedNamesWithoutDroppingCoordinateSamples() throws Exception {
        var fixture = new MsavFixture();
        fixture.tags.put("mods", "[\"[scarlet]evil\\n\\u202emod" + "x".repeat(1000) + "\"]");
        var findings = checks.check(read(fixture, MsavFixture.settings()), "survival", MsavFixture.settings());
        assertThat(findings.getFirst().detail()).hasSizeLessThanOrEqualTo(300).doesNotContain("[scarlet]", "\n", "\u202e");
    }

    @Test
    void heavilyBracketedModNamesDoNotCauseRepeatedSuffixScans() throws Exception {
        var fixture = new MsavFixture();
        fixture.tags.put("mods", "[\"" + "[".repeat(60000) + "]mod\"]");
        var snapshot = read(fixture, MsavFixture.settings());
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(2), () ->
                assertThat(checks.check(snapshot, "survival", MsavFixture.settings())).singleElement()
                        .satisfies(finding -> assertThat(finding.detail()).isEqualTo("Required mods: mod")));
    }

    private MapSnapshot read(MsavFixture fixture, InspectionSettings settings) throws Exception {
        return new DetachedMsavReader().read(fixture.write(directory), "0".repeat(64), MsavFixture.registry(), settings);
    }
}
