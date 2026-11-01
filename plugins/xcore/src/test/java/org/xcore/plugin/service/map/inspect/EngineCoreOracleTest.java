package org.xcore.plugin.service.map.inspect;

import arc.util.io.CounterInputStream;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.io.SaveFileReader;
import mindustry.io.SaveIO;
import mindustry.io.SaveReadState;
import mindustry.io.SaveVersion;
import mindustry.world.Tile;
import mindustry.world.WorldContext;
import mindustry.world.blocks.storage.CoreBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.testkit.fixtures.HeadlessWorld;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.xcore.plugin.service.map.inspect.BuildingValidationTest.*;

/**
 * Independent oracle for the core list: the map region of each generated fixture is read by the real
 * v160 {@code SaveVersion.readMap} into a disposable headless world, and the detached reader is held to
 * that result. It may reject a file the engine reads; it must never publish cores the engine does not have.
 */
class EngineCoreOracleTest {
    private static final MapSnapshot.Core SHARD = new MapSnapshot.Core(2, 2, 3, 1);
    @TempDir Path directory;

    @BeforeEach void gameThread() { GameThread.setGameThreadOverride(Thread.currentThread()); }
    @AfterEach void reset() { GameThread.setGameThreadOverride(null); }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13})
    void validThreeByThreeCoresMatchTheEngineInEverySaveVersion(int version) throws Exception {
        assertSame(core(version), List.of(SHARD));
        var edge = new MsavFixture(); edge.version = version; // core-shard at (4,2), clipped by the map edge
        assertSame(edge, List.of(new MapSnapshot.Core(4, 2, 3, 1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 3, 4})
    void everySharedBaseLayoutIsConsumedByteForByteByTheEngine(int baseVersion) throws Exception {
        // The engine checks the region length, so one missing or surplus base byte would fail this load.
        var fixture = empty(13);
        var records = coreRecords(13);
        records.put(12, center(13, 5, base(baseVersion, 1, baseVersion >= 2 ? 63 : 1)));
        fixture.mapOverride = map(13, records, Map.of());
        assertSame(fixture, List.of(SHARD));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void legacyAirRunsAroundACoreMatchTheEngine(int version) throws Exception {
        var fixture = empty(version);
        var records = new HashMap<Integer, byte[]>();
        byte[] airRun = MsavFixture.bytes(out -> { out.writeShort(0); out.writeByte(11); });
        records.put(0, airRun); records.put(13, airRun);
        records.put(12, center(version, 5, MsavFixture.legacyCoreBase(1)));
        fixture.mapOverride = map(version, records, Map.of(0, 11, 13, 11));
        assertSame(fixture, List.of(SHARD));
    }

    @Test
    void evenSizedSeparateAndEntityFreeCoresMatchTheEngine() throws Exception {
        var foundation = empty(13); foundation.names.add("core-foundation");
        Map<Integer, byte[]> records = new HashMap<>();
        for (int y = 0; y <= 3; y++) for (int x = 0; x <= 3; x++) records.put(x + y * 5, part(7));
        records.put(6, center(13, 7, base(3, 2, 1)));
        foundation.mapOverride = map(13, records, Map.of());
        assertSame(foundation, List.of(new MapSnapshot.Core(1, 1, 4, 2)));

        var separate = empty(13); records = new HashMap<>();
        for (int i : new int[]{1, 5, 6, 18, 19, 23}) records.put(i, part(5));
        records.put(0, center(13, 5, base(3, 1, 1)));
        records.put(24, center(13, 5, base(3, 2, 1)));
        separate.mapOverride = map(13, records, Map.of());
        assertSame(separate, List.of(new MapSnapshot.Core(0, 0, 3, 1), new MapSnapshot.Core(4, 4, 3, 2)));

        // A center without an entity chunk is still placed by Tile.setBlock, for the derelict team.
        var entityFree = empty(13); records = coreRecords(13);
        records.put(12, ordinary(5, 0));
        entityFree.mapOverride = map(13, records, Map.of());
        assertSame(entityFree, List.of(new MapSnapshot.Core(2, 2, 3, 0)));
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 9, 10, 13})
    void laterAirOverAFootprintErasesTheCoreInTheEngineAndIsRejected(int version) throws Exception {
        for (int run : new int[]{0, 5}) {
            var fixture = core(version);
            var records = coreRecords(version);
            records.put(13, ordinary(0, run));
            fixture.mapOverride = map(version, records, Map.of(13, run));
            var engine = engine(fixture);
            assertThat(engine.loaded()).isTrue();
            assertThat(engine.cores()).isEmpty();
            assertThat(detached(fixture)).isNull();
        }
    }

    @Test
    void overlappingCentresOfDifferentTeamsLeaveOnlyTheLaterCoreInTheEngineAndAreRejected() throws Exception {
        var fixture = core(13);
        var records = coreRecords(13);
        records.put(13, center(13, 5, base(3, 2, 1)));
        fixture.mapOverride = map(13, records, Map.of());
        // The air record at (4,2) then lands on the second footprint and erases that core as well.
        var engine = engine(fixture);
        assertThat(engine.loaded()).isTrue();
        assertThat(engine.cores()).isEmpty();
        assertThat(detached(fixture)).isNull();

        for (int i : new int[]{9, 14, 19}) records.put(i, part(5));
        fixture.mapOverride = map(13, records, Map.of());
        engine = engine(fixture);
        assertThat(engine.loaded()).isTrue();
        assertThat(engine.cores()).containsExactly(new MapSnapshot.Core(3, 2, 3, 2));
        assertThat(detached(fixture)).isNull();
    }

    @Test
    void orphanAndMismatchedPartsAreHarmlessToTheEngineButRejectedAsInconsistent() throws Exception {
        var mismatched = core(13);
        var records = coreRecords(13);
        records.put(6, part(4));
        mismatched.mapOverride = map(13, records, Map.of());
        assertThat(engine(mismatched).cores()).containsExactly(SHARD);
        assertThat(detached(mismatched)).isNull();

        var orphan = empty(13);
        orphan.mapOverride = map(13, Map.of(0, part(5)), Map.of());
        var engine = engine(orphan);
        assertThat(engine.loaded()).isTrue();
        assertThat(engine.cores()).isEmpty();
        assertThat(detached(orphan)).isNull();
    }

    @Test
    void truncatedSharedBaseIsUnreadableForTheEngineToo() throws Exception {
        for (int version : new int[]{9, 13}) {
            var fixture = new MsavFixture(); fixture.version = version;
            fixture.buildingPayloads.put(14, java.util.Arrays.copyOf(MsavFixture.modernCoreBase(1), 7));
            assertThat(engine(fixture).loaded()).isFalse();
            assertThat(detached(fixture)).isNull();
        }
    }

    /**
     * Known limit, see the inspection README: the engine reads a native building through its own subclass
     * reader and ignores the chunk length, so bytes the detached reader must treat as opaque subclass data
     * can be tile records for the engine. Here a wall chunk hides an air record whose skipped chunk swallows
     * the core center that only the detached reader sees. Closing this needs the subclass formats.
     */
    @Test
    void knownLimitSurplusBytesInANativeChunkCanHideTileRecordsFromDetachedReading() throws Exception {
        var fixture = empty(13); fixture.names.add("copper-wall");
        byte[] core = center(13, 5, base(3, 1, 1));
        byte[] wallBase = base(3, 1, 8); // a wall has no modules and no subclass payload
        byte[] hidden = MsavFixture.bytes(out -> {
            out.writeShort(0); out.writeByte(1); out.writeBoolean(true); out.writeInt(core.length);
        });
        byte[] payload = java.util.Arrays.copyOf(wallBase, wallBase.length + hidden.length);
        System.arraycopy(hidden, 0, payload, wallBase.length, hidden.length);
        var records = new HashMap<Integer, byte[]>();
        records.put(4, center(13, 7, payload));
        records.put(5, core); // core-shard at (0,1): footprint (0..1, 0..2)
        for (int i : new int[]{6, 10, 11}) records.put(i, part(5));
        fixture.mapOverride = map(13, records, Map.of());

        var engine = engine(fixture);
        assertThat(engine.loaded()).isTrue();
        assertThat(engine.cores()).isEmpty();
        assertThat(detached(fixture).cores()).containsExactly(new MapSnapshot.Core(0, 1, 3, 1));
    }

    /** The detached reader accepts the fixture and agrees with the engine on cores and on every block. */
    private void assertSame(MsavFixture fixture, List<MapSnapshot.Core> expected) throws Exception {
        var engine = engine(fixture);
        assertThat(engine.loaded()).isTrue();
        assertThat(engine.cores()).containsExactlyElementsOf(expected);
        var snapshot = detached(fixture);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.cores()).containsExactlyElementsOf(engine.cores());
        for (int i = 0; i < engine.blocks().length; i++)
            assertThat(snapshot.block(i)).as("block at tile %s", i).isEqualTo(engine.blocks()[i]);
    }

    /** @return null when the detached reader rejects the file */
    private MapSnapshot detached(MsavFixture fixture) throws Exception {
        try (var world = HeadlessWorld.create(5, 5)) {
            return new DetachedMsavReader().read(fixture.write(directory), "0".repeat(64),
                    ContentRegistrySnapshot.capture(), MsavFixture.settings());
        } catch (IOException rejected) {
            return null;
        }
    }

    private record Engine(boolean loaded, List<MapSnapshot.Core> cores, int[] blocks) {}

    private static Engine engine(MsavFixture fixture) throws Exception {
        var input = new DataInputStream(new ByteArrayInputStream(fixture.raw()));
        input.readInt();
        int version = input.readInt();
        input.skipNBytes(input.readInt()); // meta
        if (version >= 12) input.skipNBytes(input.readInt()); // assets
        input.skipNBytes(input.readInt()); // content header, mapped below as readContentHeader does
        if (version == 11) input.skipNBytes(input.readInt()); // patches
        try (var world = HeadlessWorld.create(fixture.width, fixture.height)) {
            var mapper = new MappableContent[ContentType.all.length][0];
            mapper[ContentType.block.ordinal()] = fixture.names.stream()
                    .map(name -> (MappableContent) Vars.content.getByName(ContentType.block, SaveFileReader.fallback.get(name, name)))
                    .toArray(MappableContent[]::new);
            SaveVersion reader = SaveIO.getSaveWriter(version);
            var counter = new CounterInputStream(input);
            var stream = new DataInputStream(counter);
            try {
                Vars.content.setTemporaryMapper(mapper);
                // readRegion applies the engine's own "read length mismatch" check to the map region.
                reader.readRegion("map", stream, counter, data -> reader.readMap(data, new SaveReadState(context())));
            } catch (IOException rejected) {
                return new Engine(false, List.of(), new int[0]);
            } finally {
                Vars.content.setTemporaryMapper(null);
                Vars.world.setGenerating(false);
            }
            var cores = new ArrayList<MapSnapshot.Core>();
            int[] blocks = new int[Vars.world.width() * Vars.world.height()];
            for (int i = 0; i < blocks.length; i++) {
                Tile tile = Vars.world.tiles.geti(i);
                blocks[i] = tile.blockID();
                if (tile.build instanceof CoreBlock.CoreBuild core && core.tile == tile)
                    cores.add(new MapSnapshot.Core(tile.x, tile.y, tile.block().size, core.team.id));
            }
            return new Engine(true, cores, blocks);
        }
    }

    /** World.Context without the load events and world finalisation, which no tile or building depends on. */
    private static WorldContext context() {
        return new WorldContext() {
            @Override public Tile tile(int index) { return Vars.world.tiles.geti(index); }
            @Override public void resize(int width, int height) { Vars.world.resize(width, height); }
            @Override public Tile create(int x, int y, int floorID, int overlayID, int wallID) {
                Tile tile = new Tile(x, y, floorID, overlayID, wallID);
                Vars.world.tiles.set(x, y, tile);
                return tile;
            }
            @Override public boolean isGenerating() { return Vars.world.isGenerating(); }
            @Override public void begin() { Vars.world.setGenerating(true); }
            @Override public void end() { Vars.world.setGenerating(false); }
        };
    }
}
