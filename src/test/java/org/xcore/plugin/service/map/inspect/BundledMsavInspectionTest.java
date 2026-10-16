package org.xcore.plugin.service.map.inspect;

import mindustry.Vars;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.testkit.fixtures.HeadlessWorld;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.*;

/** Every unmodified map in the v160 tag, including campaign and hidden sectors. */
class BundledMsavInspectionTest {
    @TempDir Path directory;
    private static final Map<String, String> UNSUPPORTED = Map.of(
            "default/canyon.msav", "Unknown legacy block has ambiguous building format",
            "serpulo/hidden/242.msav", "Gameplay patches/content are unsupported",
            "serpulo/littoralShipyard.msav", "Gameplay patches/content are unsupported");

    @TestFactory
    Stream<DynamicTest> everyBundledV160MapIsDecodedOrExplicitlyRejectedWithoutLoadingTheWorld() throws Exception {
        var tests = new ArrayList<DynamicTest>();
        try (InputStream resource = getClass().getResourceAsStream("/maps/inspection/bundled-v160.zip")) {
            assertThat(resource).isNotNull();
            try (var archive = new ZipInputStream(resource)) {
                for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                    String name = entry.getName();
                    byte[] bytes = archive.readAllBytes();
                    tests.add(DynamicTest.dynamicTest(name, () -> inspect(name, bytes)));
                }
            }
        }
        assertThat(tests).hasSize(114);
        return tests.stream();
    }

    private void inspect(String name, byte[] bytes) throws Exception {
        GameThread.setGameThreadOverride(Thread.currentThread());
        try (var engine = HeadlessWorld.create(8, 8)) {
            var world = Vars.world;
            var state = Vars.state;
            var content = Vars.content;
            var rules = state.rules;
            var registry = ContentRegistrySnapshot.capture();
            var file = directory.resolve(name.replace('/', '_'));
            Files.write(file, bytes);
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            var reader = new DetachedMsavReader();
            if (UNSUPPORTED.containsKey(name)) {
                assertThatThrownBy(() -> reader.read(file, hash, registry, MsavFixture.settings()))
                        .isInstanceOf(java.io.IOException.class).hasMessageContaining(UNSUPPORTED.get(name));
            } else {
                var snapshot = reader.read(file, hash, registry, MsavFixture.settings());
                assertThat(snapshot.tilesAvailable()).isTrue();
                assertThat(snapshot.tileCount()).isEqualTo(snapshot.metadata().width() * snapshot.metadata().height()).isPositive();
                assertThat(snapshot.metadata().sha256()).isEqualTo(hash);
                assertThat(snapshot.metadata().sizeBytes()).isEqualTo(bytes.length);
                var checks = new MapChecks();
                for (String profile : new String[]{MapChecks.profile(snapshot, null), "tower-defense"}) {
                    var findings = checks.check(snapshot, profile, MsavFixture.settings());
                    assertThat(checks.check(snapshot, profile, MsavFixture.settings())).isEqualTo(findings);
                    assertThat(findings).allSatisfy(finding -> assertThat(finding.detail()).hasSizeLessThanOrEqualTo(300));
                }
            }
            assertThat(Vars.world).isSameAs(world);
            assertThat(Vars.state).isSameAs(state);
            assertThat(Vars.content).isSameAs(content);
            assertThat(state.rules).isSameAs(rules);
            assertThat(world.width()).isEqualTo(8);
            assertThat(world.height()).isEqualTo(8);
        } finally {
            GameThread.setGameThreadOverride(null);
        }
    }
}
