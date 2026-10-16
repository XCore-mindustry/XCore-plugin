package org.xcore.plugin.service.map.inspect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.service.map.MapInspectionService;
import org.xcore.protocol.generated.shared.MapFileSourceV1;
import org.xcore.testkit.fixtures.HeadlessWorld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Independent native wire constructions and literal effective-layout oracles, without engine loading. */
class BuildingValidationTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 3, 4})
    void modernAndFallbackBaseVersionsRequireEverySharedByteBeforeCountingCore(int baseVersion) throws Exception {
        byte[] base = base(baseVersion, 1, baseVersion >= 2 ? 63 : 1);
        var fixture = new MsavFixture();
        fixture.buildingPayloads.put(14, base);
        assertThat(read(fixture).coresByTeam()).containsExactlyEntriesOf(Map.of("sharded", 1));
        for (int length = 0; length < base.length; length++) {
            fixture.buildingPayloads.put(14, Arrays.copyOf(base, length));
            assertThatThrownBy(() -> read(fixture)).as("base version %s, prefix %s", baseVersion, length)
                    .isInstanceOf(IOException.class);
        }
        // Subclass payload is intentionally opaque, but only AFTER the complete shared base.
        fixture.buildingPayloads.put(14, Arrays.copyOf(base, base.length + 12));
        assertThat(read(fixture).coresByTeam()).containsExactlyEntriesOf(Map.of("sharded", 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void packedLegacyHealthTeamItemModuleAndConsumeAreRequired(int version) throws Exception {
        var fixture = new MsavFixture(); fixture.version = version;
        for (int team : new int[]{1, 200}) {
            byte[] base = MsavFixture.legacyCoreBase(team);
            fixture.buildingPayloads.put(14, base);
            assertThat(read(fixture).coresByTeam()).containsExactlyEntriesOf(Map.of(team == 1 ? "sharded" : "team#200", 1));
            for (int length = 0; length < base.length; length++) {
                fixture.buildingPayloads.put(14, Arrays.copyOf(base, length));
                assertThatThrownBy(() -> read(fixture)).as("save %s team %s prefix %s", version, team, length)
                        .isInstanceOf(IOException.class);
            }
        }
    }

    @Test
    void missingOrUnsupportedVersionInvalidFlagsAndModuleCountsFailClosed() throws Exception {
        var fixture = new MsavFixture();
        // The original seven-byte repro, with the marked rotation and no version byte.
        fixture.buildingPayloads.put(14, Arrays.copyOf(base(3, 1, 1), 7));
        assertThatThrownBy(() -> read(fixture)).isInstanceOf(IOException.class);
        for (int version : new int[]{5, 127, 255}) {
            byte[] bytes = base(3, 1, 1); bytes[7] = (byte) version;
            fixture.buildingPayloads.put(14, bytes);
            assertThatThrownBy(() -> read(fixture)).isInstanceOf(IOException.class).hasMessageContaining("base version");
        }
        for (int flags : new int[]{64, 128, 255}) {
            byte[] bytes = base(3, 1, 1); bytes[9] = (byte) flags;
            fixture.buildingPayloads.put(14, bytes);
            assertThatThrownBy(() -> read(fixture)).isInstanceOf(IOException.class).hasMessageContaining("module flags");
        }
        byte[] invalidEnabled = base(3, 1, 1); invalidEnabled[8] = 2;
        fixture.buildingPayloads.put(14, invalidEnabled);
        assertThatThrownBy(() -> read(fixture)).isInstanceOf(IOException.class).hasMessageContaining("enabled");
        // Explicit one-module bases isolate each signed count and its data; no other module can mask it.
        for (int module : new int[]{1, 2, 4}) {
            for (int count : new int[]{-1, Short.MAX_VALUE}) {
                byte[] bytes = base(3, 1, module);
                bytes[10] = (byte) (count >>> 8); bytes[11] = (byte) count;
                fixture.buildingPayloads.put(14, bytes);
                assertThatThrownBy(() -> read(fixture)).as("module %s count %s", module, count).isInstanceOf(IOException.class);
            }
        }
        byte[] consume = base(2, 1, 0); consume[10] = 2;
        fixture.buildingPayloads.put(14, consume);
        assertThatThrownBy(() -> read(fixture)).isInstanceOf(IOException.class).hasMessageContaining("consume");
    }

    @Test
    void nativeDescriptorsSupplyAllLegacyModulesWithoutBuildingConstruction() throws Exception {
        GameThread.setGameThreadOverride(Thread.currentThread());
        try (var world = HeadlessWorld.create(5, 5)) {
            var registry = ContentRegistrySnapshot.capture();
            var melter = registry.resolve(ContentRegistrySnapshot.BLOCK_TYPE, "melter");
            assertThat(melter.modules()).isEqualTo(7);
            assertThat(melter.size()).isEqualTo(1);
            assertThat(registry.resolve(ContentRegistrySnapshot.BLOCK_TYPE, "core-shard").modules()).isEqualTo(1);
            assertThat(registry.resolve(ContentRegistrySnapshot.BLOCK_TYPE, "core-foundation").size()).isEqualTo(4);
            for (int format : new int[]{1, 3, 4, 13}) {
                var fixture = empty(format); fixture.names.set(5, "melter");
                byte[] payload = format <= 3 ? MsavFixture.bytes(out -> {
                    out.writeByte(0); out.writeShort(1000); out.writeByte(0x10);
                    modules(out, 7, true); out.writeBoolean(true);
                }) : base(-1, 1, 7);
                fixture.mapOverride = map(format, Map.of(12, center(format, 5, payload)), Map.of());
                assertThat(read(fixture, registry).cores()).isEmpty();
                for (int length = 0; length < payload.length; length++) {
                    fixture.mapOverride = map(format, Map.of(12, center(format, 5, Arrays.copyOf(payload, length))), Map.of());
                    assertThatThrownBy(() -> read(fixture, registry)).as("melter format %s prefix %s", format, length)
                            .isInstanceOf(IOException.class);
                }
            }
        } finally { GameThread.setGameThreadOverride(null); }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13})
    void realThreeByThreeCoreHasEarlierPartsAndAnIndependentFinalLayout(int version) throws Exception {
        var fixture = core(version);
        var snapshot = read(fixture);
        assertThat(snapshot.cores()).containsExactly(new MapSnapshot.Core(2, 2, 3, 1));
        assertThat(snapshot.coresByTeam()).containsExactlyEntriesOf(Map.of("sharded", 1));
        Set<Integer> expected = Set.of(6, 7, 8, 11, 12, 13, 16, 17, 18);
        for (int i = 0; i < 25; i++) assertThat(snapshot.block(i)).as("tile %s", i).isEqualTo(expected.contains(i) ? 50 : 0);
        assertThat(new MapChecks().check(snapshot, "pvp", MsavFixture.settings())).extracting(MapFinding::code)
                .contains("pvp-single-team").doesNotContain("no-core");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void legacyAirRunsCoverEarlierAndLaterPartsWithoutErasingTheCore(int version) throws Exception {
        var fixture = empty(version);
        var records = new HashMap<Integer, byte[]>();
        byte[] airRun = MsavFixture.bytes(out -> { out.writeShort(0); out.writeByte(11); });
        records.put(0, airRun); records.put(13, airRun);
        records.put(12, center(version, 5, MsavFixture.legacyCoreBase(1)));
        fixture.mapOverride = map(version, records, Map.of(0, 11, 13, 11));
        var snapshot = read(fixture);
        assertThat(snapshot.cores()).containsExactly(new MapSnapshot.Core(2, 2, 3, 1));
        assertThat(snapshot.coresByTeam()).containsExactlyEntriesOf(Map.of("sharded", 1));
        for (int i : new int[]{6, 7, 8, 11, 12, 13, 16, 17, 18}) assertThat(snapshot.block(i)).isEqualTo(50);
    }

    @Test
    void evenCoreUsesTheEngineAnchorOffsetAndCountsOnlyItsActualFootprint() throws Exception {
        var registry = MsavFixture.registry();
        var names = new HashMap<>(registry.types().get(ContentRegistrySnapshot.BLOCK_TYPE));
        var ids = new HashMap<>(registry.blocks());
        var core = new ContentRegistrySnapshot.Entry(70, "core-foundation", false, false, true, true, 4, 1);
        names.put(core.name(), core); ids.put(core.id(), core);
        registry = new ContentRegistrySnapshot(Map.of(ContentRegistrySnapshot.BLOCK_TYPE, names), ids, registry.aliases(), registry.teams(), registry.defaults());
        var fixture = empty(13); fixture.names.add("core-foundation");
        var records = new HashMap<Integer, byte[]>();
        // Size 4 at (1,1) occupies (0,0)..(3,3); the center is NOT the upper-right middle tile.
        for (int y = 0; y <= 3; y++) for (int x = 0; x <= 3; x++) records.put(x + y * 5, part(7));
        records.put(6, center(13, 7, base(3, 2, 1)));
        fixture.mapOverride = map(13, records, Map.of());
        var snapshot = read(fixture, registry);
        assertThat(snapshot.cores()).containsExactly(new MapSnapshot.Core(1, 1, 4, 2));
        assertThat(snapshot.coresByTeam()).containsExactlyEntriesOf(Map.of("crux", 1));
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++)
            assertThat(snapshot.block(x + y * 5)).isEqualTo(x <= 3 && y <= 3 ? 70 : 0);
        assertThat(new MapChecks().check(snapshot, "attack", MsavFixture.settings())).extracting(MapFinding::code)
                .contains("no-core").doesNotContain("attack-no-enemy-core");
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 9, 10, 13})
    void laterAirOrRleOverPartCannotLeaveAStaleCore(int version) throws Exception {
        for (int run : new int[]{0, 5}) {
            var fixture = core(version);
            var records = coreRecords(version);
            records.put(13, ordinary(0, run)); // (3,2) erases the already placed core at (2,2)
            fixture.mapOverride = map(version, records, Map.of(13, run));
            assertThatThrownBy(() -> read(fixture)).isInstanceOf(IOException.class).hasMessageContaining("Overwritten");
        }
    }

    @Test
    void overlappingDifferentTeamsOrEarlierMismatchedOrOrphanPartsAreInvalid() throws Exception {
        var fixture = core(13); var records = coreRecords(13);
        records.put(13, center(13, 5, base(3, 2, 1)));
        fixture.mapOverride = map(13, records, Map.of());
        assertThatThrownBy(() -> read(fixture)).isInstanceOf(IOException.class).hasMessageContaining("Overlapping");
        records = coreRecords(13); records.put(6, part(4));
        fixture.mapOverride = map(13, records, Map.of());
        assertThatThrownBy(() -> read(fixture)).isInstanceOf(IOException.class).hasMessageContaining("Inconsistent");
        for (int id : new int[]{0, 4, 5}) {
            var orphan = empty(13); orphan.mapOverride = map(13, Map.of(0, part(id)), Map.of());
            assertThatThrownBy(() -> read(orphan)).isInstanceOf(IOException.class).hasMessageContaining("Orphan");
        }
    }

    @Test
    void inconsistentFootprintsReachTheCallerAsInvalidMapWithoutAnyCoreList() throws Exception {
        var overwritten = core(13); var records = coreRecords(13);
        records.put(13, ordinary(0, 0));
        overwritten.mapOverride = map(13, records, Map.of());
        var overlapping = core(13); records = coreRecords(13);
        records.put(13, center(13, 5, base(3, 2, 1)));
        for (int i : new int[]{9, 14, 19}) records.put(i, part(5));
        overlapping.mapOverride = map(13, records, Map.of());
        for (var fixture : List.of(overwritten, overlapping)) {
            var file = fixture.write(directory);
            var downloader = mock(MapDownloader.class);
            when(downloader.download(anyString(), anyInt()))
                    .thenReturn(new MapDownloader.Download(file, "c".repeat(64), (int) Files.size(file)));
            var service = new MapInspectionService(new TomlSecretsConfig(), downloader, new DetachedMapReadLock());
            var result = service.inspect(new MapFileSourceV1("https://example.invalid/map", "fixture.msav", null), "pvp",
                    new MapInspectionService.Context(MsavFixture.registry(), MsavFixture.settings()));
            assertThat(result.status()).isEqualTo(MapInspectionResult.Status.INVALID_MAP);
            assertThat(result.snapshot()).isNull(); assertThat(result.findings()).isEmpty();
            assertThat(file).doesNotExist();
        }
    }

    @Test
    void validSeparateTeamsAndEmptyLayoutsHaveLiteralCoreAndGameplayOracles() throws Exception {
        var fixture = empty(13); var records = new HashMap<Integer, byte[]>();
        for (int i : new int[]{1, 5, 6}) records.put(i, part(5));
        records.put(0, center(13, 5, base(3, 1, 1)));
        for (int i : new int[]{18, 19, 23}) records.put(i, part(5));
        records.put(24, center(13, 5, base(3, 2, 1)));
        fixture.mapOverride = map(13, records, Map.of());
        var snapshot = read(fixture);
        assertThat(snapshot.coresByTeam()).isEqualTo(Map.of("sharded", 1, "crux", 1));
        assertThat(new MapChecks().check(snapshot, "pvp", MsavFixture.settings())).isEmpty();
        fixture.mapOverride = map(13, Map.of(), Map.of());
        snapshot = read(fixture);
        assertThat(snapshot.coresByTeam()).isEmpty();
        assertThat(new MapChecks().check(snapshot, "pvp", MsavFixture.settings())).extracting(MapFinding::code)
                .containsExactly("no-core", "pvp-single-team");
    }

    static MsavFixture empty(int version) {
        var fixture = new MsavFixture(); fixture.version = version;
        Arrays.fill(fixture.blocks, 0); Arrays.fill(fixture.overlays, 0);
        return fixture;
    }
    static MsavFixture core(int version) throws IOException {
        var fixture = empty(version); fixture.mapOverride = map(version, coreRecords(version), Map.of()); return fixture;
    }
    static Map<Integer, byte[]> coreRecords(int version) throws IOException {
        var records = new HashMap<Integer, byte[]>();
        if (version >= 4) for (int i : new int[]{6, 7, 8, 11, 13, 16, 17, 18}) records.put(i, part(5));
        records.put(12, center(version, 5, version <= 3 ? MsavFixture.legacyCoreBase(1) : base(3, 1, 1)));
        return records;
    }
    static byte[] part(int id) throws IOException {
        return MsavFixture.bytes(out -> { out.writeShort(id); out.writeByte(1); out.writeBoolean(false); });
    }
    static byte[] ordinary(int id, int run) throws IOException {
        return MsavFixture.bytes(out -> { out.writeShort(id); out.writeByte(0); out.writeByte(run); });
    }
    static byte[] center(int version, int id, byte[] payload) throws IOException {
        return MsavFixture.bytes(out -> {
            out.writeShort(id);
            if (version >= 4) { out.writeByte(1); out.writeBoolean(true); }
            if (version <= 9) out.writeShort(payload.length); else out.writeInt(payload.length);
            out.write(payload);
        });
    }
    static byte[] map(int version, Map<Integer, byte[]> records, Map<Integer, Integer> runs) throws IOException {
        return MsavFixture.bytes(out -> {
            out.writeShort(5); out.writeShort(5);
            for (int i = 0; i < 25; i++) { out.writeShort(1); out.writeShort(0); out.writeByte(0); }
            for (int i = 0; i < 25; i += 1 + runs.getOrDefault(i, 0)) {
                byte[] fallback = version <= 3 ? new byte[]{0, 0, 0} : ordinary(0, 0);
                out.write(records.getOrDefault(i, fallback));
            }
        });
    }
    static byte[] base(int version, int team, int modules) throws IOException {
        return MsavFixture.bytes(out -> {
            out.writeByte(0); out.writeFloat(1000); out.writeByte(version < 0 ? 0 : 0x80); out.writeByte(team);
            if (version >= 0) out.writeByte(version);
            if (version >= 1) out.writeBoolean(true);
            if (version >= 2) out.writeByte(modules);
            modules(out, modules, version < 0);
            if ((modules & 16) != 0) { out.writeFloat(2); out.writeFloat(120); }
            if ((modules & 32) != 0) out.writeInt(0x12345678);
            if (version <= 2) out.writeBoolean(true);
            else { out.writeByte(255); out.writeByte(128); }
            if (version == 4) out.writeLong(0x123456789abcdefL);
        });
    }
    private static void modules(java.io.DataOutputStream out, int modules, boolean legacy) throws IOException {
        if ((modules & 1) != 0) {
            if (legacy) { out.writeByte(1); out.writeByte(0); } else { out.writeShort(1); out.writeShort(0); }
            out.writeInt(42);
        }
        if ((modules & 2) != 0) { out.writeShort(1); out.writeInt(0x12345678); out.writeFloat(0.5f); }
        if ((modules & 4) != 0) {
            if (legacy) { out.writeByte(1); out.writeByte(0); } else { out.writeShort(1); out.writeShort(0); }
            out.writeFloat(10);
        }
    }
    private MapSnapshot read(MsavFixture fixture) throws IOException { return read(fixture, MsavFixture.registry()); }
    private MapSnapshot read(MsavFixture fixture, ContentRegistrySnapshot registry) throws IOException {
        return new DetachedMsavReader().read(fixture.write(directory), "0".repeat(64), registry, MsavFixture.settings());
    }
}
