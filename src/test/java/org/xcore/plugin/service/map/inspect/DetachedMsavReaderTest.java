package org.xcore.plugin.service.map.inspect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

import static org.assertj.core.api.Assertions.*;

class DetachedMsavReaderTest {
    @TempDir Path directory;
    private final DetachedMsavReader reader = new DetachedMsavReader();

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13})
    void supportsEachV160SaveFormatAndUnsignedTeamIds(int version) throws Exception {
        var fixture = new MsavFixture(); fixture.version = version; fixture.teams[14] = 200;
        var snapshot = read(fixture.compressed());
        assertThat(snapshot.metadata().saveVersion()).isEqualTo(version);
        assertThat(snapshot.metadata().build()).isEqualTo(160);
        assertThat(snapshot.metadata().name()).isEqualTo("Fixture");
        assertThat(snapshot.metadata().author()).isEqualTo("Mapper");
        assertThat(snapshot.metadata().description()).isEqualTo("Detached fixture");
        assertThat(snapshot.coresByTeam()).containsEntry("team#200", 1);
        assertThat(snapshot.spawnCount()).isEqualTo(1);
        assertThat(snapshot.floor(10)).isEqualTo(30); // native ID, not file ID 3
        assertThat(snapshot.overlay(10)).isEqualTo(20);
        assertThat(snapshot.block(14)).isEqualTo(50);
    }

    @Test
    void aliasesAirFloorFallbackAndRuleDefaultsAreDetached() throws Exception {
        var fixture = new MsavFixture(); fixture.names.set(6, "sand"); fixture.floors[0] = 6; fixture.floors[1] = 0;
        fixture.tags.put("rules", "{}"); fixture.tags.remove("build");
        var snapshot = read(fixture.compressed());
        assertThat(snapshot.floor(0)).isEqualTo(60); assertThat(snapshot.floor(1)).isEqualTo(10);
        assertThat(snapshot.unknownContent()).isEmpty();
        assertThat(snapshot.metadata().build()).isNull();
        assertThat(snapshot.metadata().rules()).isEqualTo(MsavFixture.registry().defaults());
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 9, 10, 13})
    void tileDataAndNoncenterBuildingPartsPreserveWireAlignment(int version) throws Exception {
        var fixture = new MsavFixture(); fixture.version = version;
        byte[] original = fixture.map(); int firstBlock = 4 + 25 * 5;
        byte[] map;
        if (version == 8) {
            map = original.clone(); map[firstBlock + 2] = 2; // old one-byte tile data, replacing the RLE byte
        } else {
            map = new byte[original.length + 6];
            System.arraycopy(original, 0, map, 0, firstBlock + 3);
            map[firstBlock + 2] = 4; // new seven-byte data, replacing the one-byte RLE field
            for (int i = 0; i < 7; i++) map[firstBlock + 3 + i] = (byte) (i + 1);
            System.arraycopy(original, firstBlock + 4, map, firstBlock + 10, original.length - firstBlock - 4);
        }
        int secondBlock = firstBlock + (version == 8 ? 4 : 10) + 7 * 4;
        ByteBuffer.wrap(map).putShort(secondBlock, (short) 5);
        map[secondBlock + 2] = 1; map[secondBlock + 3] = 0; // a noncenter part has no entity chunk or RLE
        fixture.mapOverride = map;
        var snapshot = read(fixture.compressed());
        assertThat(snapshot.block(8)).isEqualTo(50);
        assertThat(snapshot.cores()).hasSize(1); assertThat(snapshot.cores().getFirst().x()).isEqualTo(4);
    }

    @Test
    void unknownLegacyBlocksFailClosedWhileModernBlocksProduceUnknownContent() throws Exception {
        var fixture = new MsavFixture(); fixture.names.set(4, "missing-wall"); fixture.version = 3;
        assertThatThrownBy(() -> read(fixture.compressed())).isInstanceOf(IOException.class).hasMessageContaining("ambiguous");
        fixture.version = 10;
        var snapshot = read(fixture.compressed());
        assertThat(snapshot.unknownContent()).containsExactly("block:missing-wall");
        assertThat(snapshot.block(5)).isEqualTo(-1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{pvp:true,editor:true,attackMode:true,infiniteResources:true}", "{editor:true,attackMode:true}", "{attackMode:true}", "{infiniteResources:true}"})
    void modeInferenceUsesRulesModePriority(String rules) throws Exception {
        var fixture = new MsavFixture(); fixture.tags.put("rules", rules);
        String expected = rules.contains("pvp") ? "pvp" : rules.contains("editor") ? "editor" : rules.contains("attackMode") ? "attack" : "sandbox";
        assertThat(read(fixture.compressed()).metadata().rules().gameMode()).isEqualTo(expected);
    }

    @Test
    void unknownNamedContentAndIncompatibleFloorsRemainVisible() throws Exception {
        var fixture = new MsavFixture(); fixture.names.set(6, "missing-mod-floor"); fixture.floors[0] = 6; fixture.floors[1] = 4;
        fixture.names.add("another-missing");
        fixture.blocks[0] = 1;
        var snapshot = read(fixture.compressed());
        assertThat(snapshot.floor(0)).isEqualTo(-1);
        assertThat(snapshot.unknownContent()).containsExactly("block:another-missing", "block:missing-mod-floor", "incompatible block:stone", "incompatible floor:metal-wall-2");
    }

    @Test
    void allCompressedTruncationsAndRawTruncationsAreInvalid() throws Exception {
        var fixture = new MsavFixture(); byte[] compressed = fixture.compressed(), raw = fixture.raw();
        for (int i = 0; i < compressed.length; i++) {
            byte[] truncated = Arrays.copyOf(compressed, i);
            assertThatThrownBy(() -> read(truncated)).as("compressed prefix %s", i).isInstanceOf(IOException.class);
        }
        for (int i = 0; i < raw.length; i++) {
            byte[] truncated = MsavFixture.compress(Arrays.copyOf(raw, i));
            assertThatThrownBy(() -> read(truncated)).as("raw prefix %s", i).isInstanceOf(IOException.class);
        }
    }

    @Test
    void rejectsNonZlibMagicVersionChecksumAndTrailingInflatedData() throws Exception {
        assertInvalid(new byte[]{'M', 'S', 'A', 'V'});
        var fixture = new MsavFixture(); byte[] raw = fixture.raw(); raw[0] = 'X'; assertInvalid(MsavFixture.compress(raw));
        for (int version : new int[]{-1, 0, 14, Integer.MAX_VALUE}) { fixture.version = version; assertInvalid(fixture.compressed()); }
        fixture.version = 13;
        var compressed = fixture.compressed(); compressed[compressed.length - 1] ^= 1; assertInvalid(compressed);
        raw = Arrays.copyOf(fixture.raw(), fixture.raw().length + 1); assertInvalid(MsavFixture.compress(raw));
        compressed = fixture.compressed();
        assertInvalid(Arrays.copyOf(compressed, compressed.length + 1));
        byte[] concatenated = new byte[compressed.length * 2];
        System.arraycopy(compressed, 0, concatenated, 0, compressed.length);
        System.arraycopy(compressed, 0, concatenated, compressed.length, compressed.length);
        assertInvalid(concatenated);
    }

    @Test
    void rejectsNegativeHugeAndMismatchedRegionLengthsBeforeAllocation() throws Exception {
        var fixture = new MsavFixture();
        for (int length : new int[]{-1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 2}) {
            var raw = fixture.raw(); ByteBuffer.wrap(raw).putInt(8, length); assertInvalid(MsavFixture.compress(raw));
        }
        fixture.custom = MsavFixture.bytes(c -> { c.writeInt(1); c.writeUTF("malformed"); c.writeInt(-1); });
        assertInvalid(fixture.compressed());
        fixture.custom = MsavFixture.bytes(c -> c.writeInt(Integer.MAX_VALUE)); assertInvalid(fixture.compressed());
        var map = fixture.map();
        // Locate the sole core's center payload length in the row-major block stream.
        int coreLength = 4 + 25 * 5 + 14 * 4 + 4;
        ByteBuffer.wrap(map).putInt(coreLength, Integer.MAX_VALUE);
        fixture = new MsavFixture(); fixture.mapOverride = map; assertInvalid(fixture.compressed());
    }

    @Test
    void dimensionsAndRleCannotOverflowOrDisagreeWithMetadata() throws Exception {
        for (String value : List.of("-1", "0", "65536", "2147483647", "NaN")) {
            var fixture = new MsavFixture(); fixture.tags.put("width", value); assertInvalid(fixture.compressed());
        }
        var fixture = new MsavFixture(); fixture.width = 6; assertInvalid(fixture.compressed());
        var map = fixture.map(); map[8] = (byte) 255; fixture.width = 5; fixture.mapOverride = map; assertInvalid(fixture.compressed());
        map = new MsavFixture().map(); map[4 + 25 * 5 + 3] = (byte) 255;
        fixture.mapOverride = map; assertInvalid(fixture.compressed());
        fixture = new MsavFixture(); fixture.tags.put("width", "65535"); fixture.tags.put("height", "65535");
        fixture.mapOverride = new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255};
        var snapshot = read(fixture.compressed());
        assertThat(snapshot.tilesAvailable()).isFalse(); assertThat(snapshot.tileCount()).isZero();
    }

    @Test
    void invalidContentIdsTypeCountsAndTileFlagsAreInvalid() throws Exception {
        var fixture = new MsavFixture(); fixture.floors[0] = 32767; assertInvalid(fixture.compressed());
        fixture = new MsavFixture(); fixture.overlays[0] = -1; assertInvalid(fixture.compressed());
        fixture = new MsavFixture(); byte[] raw = fixture.raw();
        int content = 12 + ByteBuffer.wrap(raw).getInt(8);
        content += 4 + ByteBuffer.wrap(raw).getInt(content); // patches
        raw[content + 5] = 127; assertInvalid(MsavFixture.compress(raw));
        raw = fixture.raw(); content = 12 + ByteBuffer.wrap(raw).getInt(8); content += 4 + ByteBuffer.wrap(raw).getInt(content);
        ByteBuffer.wrap(raw).putShort(content + 6, (short) -1); assertInvalid(MsavFixture.compress(raw));
        byte[] map = fixture.map(); map[4 + 25 * 5 + 2] = 2; fixture.mapOverride = map; assertInvalid(fixture.compressed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{waves:1}", "{spawns:null}", "{spawns:[1]}", "{dropZoneRadius:1e999}", "{dropZoneRadius:1e39}", "{pvp:true,editor:1}", "{defaultTeam:256}", "{waveTeam:1.1}", "{waves:true} garbage", "{waves:false,waves:true}", "{extra:{a:[],a:{}}}"})
    void malformedRulesAreInvalid(String rules) throws Exception {
        var fixture = new MsavFixture(); fixture.tags.put("rules", rules); assertInvalid(fixture.compressed());
    }

    @Test
    void dropZoneRadiusUsesTheEnginesFloatPrecision() throws Exception {
        var fixture = new MsavFixture(); fixture.tags.put("rules", "{dropZoneRadius:1e-50}");
        assertThat(read(fixture.compressed()).metadata().rules().dropZoneRadius()).isZero();
    }

    @Test
    void arcMinimalJsonStringsCommentsAndEscapesDoNotChangeSavedRuleFields() throws Exception {
        var fixture = new MsavFixture();
        fixture.tags.put("rules", "{ /* saved editor metadata */ objectives:[{details:Starts the first set of factories.,"
                + "completionLogicCode:print \"@announce.nuclearstrike\"\\\\nstop\\\\n}],"
                + "waves:false,waveTimer:false,dropZoneRadius:0,defaultTeam:200,spawns:[],PVP:true}");
        fixture.tags.put("mods", "[mod alpha:1.2,\"quoted mod\"]");
        var snapshot = read(fixture.compressed());
        assertThat(snapshot.metadata().rules()).isEqualTo(new MapRules("survival", 200, 2, false, false, 0, 0));
        assertThat(snapshot.metadata().mods()).containsExactly("mod alpha:1.2", "quoted mod");
        for (String json : List.of("{}{}", "{}[]", "{} true", "[] []")) {
            fixture.tags.put("rules", json);
            assertInvalid(fixture.compressed());
        }
    }

    @Test
    void malformedModsAndDeepJsonAreInvalidWithoutStackOverflow() throws Exception {
        for (String mods : List.of("null", "{}", "[1]", "[null]")) {
            var fixture = new MsavFixture(); fixture.tags.put("mods", mods); assertInvalid(fixture.compressed());
        }
        var fixture = new MsavFixture(); fixture.tags.put("rules", "{extra:" + "[".repeat(10000) + "0" + "]".repeat(10000) + "}");
        assertInvalid(fixture.compressed());
    }

    @Test
    void fileByteLimitAndInflationBombAreEnforced() throws Exception {
        var fixture = new MsavFixture(); var file = fixture.write(directory);
        assertThatThrownBy(() -> reader.read(file, "0".repeat(64), MsavFixture.registry(), new InspectionSettings(10, 360000, Map.of())))
                .isInstanceOf(IOException.class).hasMessageContaining("maxFileBytes");
        byte[] prefix = fixture.raw(); int offset = 8;
        for (int i = 0; i < 5; i++) offset += 4 + ByteBuffer.wrap(prefix).getInt(offset); // through entities
        Path bomb = directory.resolve("bomb.msav");
        try (var out = new DataOutputStream(new DeflaterOutputStream(Files.newOutputStream(bomb)))) {
            out.write(prefix, 0, offset);
            byte[] zero = new byte[8192];
            out.writeInt(InspectionSettings.MAX_REGION_BYTES); // opaque markers
            for (int written = 0; written < InspectionSettings.MAX_REGION_BYTES; written += zero.length) out.write(zero);
            out.writeInt(InspectionSettings.MAX_REGION_BYTES); // custom chunk
            out.writeInt(1); out.writeUTF("bomb"); out.writeInt(InspectionSettings.MAX_REGION_BYTES - 14);
            for (int written = 0; written < InspectionSettings.MAX_REGION_BYTES - 14;) {
                int length = Math.min(zero.length, InspectionSettings.MAX_REGION_BYTES - 14 - written);
                out.write(zero, 0, length); written += length;
            }
        }
        assertThat(Files.size(bomb)).isLessThan(4194304);
        assertThatThrownBy(() -> reader.read(bomb, "0".repeat(64), MsavFixture.registry(), MsavFixture.settings()))
                .isInstanceOf(IOException.class).hasMessageContaining("Inflated");
    }

    @Test
    void entityCountsMappingAndNestedPlanConfigLengthsAreValidatedWithoutReadingEngineEntities() throws Exception {
        var fixture = new MsavFixture();
        fixture.entities = MsavFixture.bytes(e -> { e.writeShort(0); e.writeInt(0); e.writeInt(-1); });
        assertInvalid(fixture.compressed());
        fixture.entities = MsavFixture.bytes(e -> { e.writeShort(-1); e.writeInt(0); e.writeInt(0); });
        assertInvalid(fixture.compressed());
        fixture.entities = MsavFixture.bytes(e -> { e.writeShort(0); e.writeInt(1); e.writeInt(1); e.writeInt(1); e.writeLong(0); e.writeByte(14); e.writeInt(-1); e.writeInt(0); });
        assertInvalid(fixture.compressed());
        fixture.entities = MsavFixture.bytes(e -> { e.writeShort(0); e.writeInt(1); e.writeInt(1); e.writeInt(1); e.writeLong(0); e.writeByte(22); e.writeInt(1); e.writeByte(22); e.writeInt(0); e.writeInt(0); });
        assertInvalid(fixture.compressed());
        fixture.entities = MsavFixture.bytes(e -> { e.writeShort(0); e.writeInt(0); e.writeInt(1); e.writeInt(Integer.MAX_VALUE); });
        assertInvalid(fixture.compressed());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
    void everyV160PlanConfigTypePreservesFollowingPlanAndEntityAlignment(int type) throws Exception {
        byte[] config = MsavFixture.bytes(c -> {
            c.writeByte(type);
            switch (type) {
                case 0 -> {}
                case 1, 3, 12, 17 -> c.writeInt(123);
                case 2, 7, 11, 19 -> c.writeLong(123);
                case 4 -> { c.writeBoolean(true); c.writeUTF("Config text"); }
                case 5, 9 -> { c.writeByte(ContentRegistrySnapshot.BLOCK_TYPE); c.writeShort(5); }
                case 6, 18, 21 -> {
                    int length = type == 21 ? 32767 : 200;
                    c.writeShort(length); c.write(new byte[length * (type == 18 ? 8 : 4)]);
                }
                case 8 -> { c.writeByte(255); c.write(new byte[255 * 4]); }
                case 10, 15, 20 -> c.writeByte(1);
                case 13, 23 -> c.writeShort(0);
                case 14, 16 -> {
                    int length = type == 14 ? 40000 : 200;
                    c.writeInt(length); c.write(new byte[length]);
                }
                case 22 -> {
                    c.writeInt(2); c.writeByte(10); c.writeBoolean(true);
                    // TypeIO permits int[] in nested object arrays, despite disallowing other nested arrays.
                    c.writeByte(21); c.writeShort(1); c.writeInt(123);
                }
                default -> throw new AssertionError(type);
            }
        });
        var fixture = new MsavFixture();
        fixture.entities = entityRegionWithPlanConfig(config);
        var snapshot = read(fixture.compressed());
        assertThat(snapshot.coresByTeam()).containsEntry("sharded", 1);
        assertThat(snapshot.spawnCount()).isEqualTo(1);
    }

    @Test
    void invalidPlanConfigCountsTypesAndEntityHeadersFailClosed() throws Exception {
        var fixture = new MsavFixture();
        for (int type : new int[]{6, 14, 16, 18, 21, 22}) {
            fixture.entities = entityRegionWithPlanConfig(MsavFixture.bytes(c -> {
                c.writeByte(type);
                if (type == 6 || type == 18 || type == 21) c.writeShort(-1); else c.writeInt(-1);
            }));
            assertInvalid(fixture.compressed());
        }
        for (int type : new int[]{5, 9, 24, 255}) {
            fixture.entities = entityRegionWithPlanConfig(new byte[]{(byte) type, 127, 0, 0});
            assertInvalid(fixture.compressed());
        }
        for (byte[] entities : List.of(
                MsavFixture.bytes(e -> { e.writeShort(1); e.writeShort(256); e.writeUTF("mod"); e.writeInt(0); e.writeInt(0); }),
                MsavFixture.bytes(e -> { e.writeShort(0); e.writeInt(1); e.writeInt(256); e.writeInt(0); e.writeInt(0); }),
                MsavFixture.bytes(e -> { e.writeShort(0); e.writeInt(0); e.writeInt(1); e.writeInt(0); }),
                MsavFixture.bytes(e -> { e.writeShort(0); e.writeInt(0); e.writeInt(1); e.writeInt(2); e.writeByte(1); }))) {
            fixture.entities = entities;
            assertInvalid(fixture.compressed());
        }
    }

    private static byte[] entityRegionWithPlanConfig(byte[] config) throws IOException {
        return MsavFixture.bytes(e -> {
            e.writeShort(1); e.writeShort(200); e.writeUTF("removed-mod-entity");
            e.writeInt(1); e.writeInt(200); e.writeInt(2);
            e.writeLong(0); e.write(config); // first plan
            e.writeLong(0); e.writeByte(10); e.writeBoolean(true); // subsequent plan must stay aligned
            e.writeInt(1); e.writeInt(1); e.writeByte(200); // opaque removed-mod entity
        });
    }

    @Test
    void assetsAreBoundedAndGameplayPatchesAreCountedAndSkipped() throws Exception {
        var fixture = new MsavFixture();
        fixture.assets = MsavFixture.bytes(a -> { a.writeInt(0); a.writeInt(1); a.writeByte(3); a.writeUTF("image.png"); a.writeBoolean(true); a.writeInt(3); a.write(new byte[3]); });
        assertThat(read(fixture.compressed()).tilesAvailable()).isTrue();
        assertThat(read(fixture.compressed()).metadata().dataPatches()).isZero();
        fixture.assets = MsavFixture.bytes(a -> { a.writeInt(0); a.writeInt(1); a.writeByte(3); a.writeUTF("image.png"); a.writeBoolean(true); a.writeInt(-1); });
        assertInvalid(fixture.compressed());
        fixture.assets = MsavFixture.bytes(a -> { a.writeInt(0); a.writeInt(3);
            a.writeByte(0); a.writeUTF("patch.json"); a.writeBoolean(true); a.writeInt(2); a.write(new byte[2]);
            a.writeByte(1); a.writeUTF("content.json"); a.writeBoolean(false); a.write(new byte[32]);
            a.writeByte(2); a.writeUTF("bundle.properties"); a.writeBoolean(true); a.writeInt(0); });
        assertThat(read(fixture.compressed()).metadata().dataPatches()).isEqualTo(2);
        fixture.version = 11; fixture.assets = new byte[]{1}; assertInvalid(fixture.compressed());
        fixture.assets = MsavFixture.bytes(a -> { a.writeByte(1); a.writeInt(2); a.write(new byte[2]); });
        assertThat(read(fixture.compressed()).metadata().dataPatches()).isEqualTo(1);
        fixture.version = 12; fixture.assets = MsavFixture.bytes(a -> { a.writeInt(0); a.writeInt(1); }); assertInvalid(fixture.compressed());
        fixture.assets = MsavFixture.bytes(a -> { a.writeInt(0); a.writeInt(1); a.writeInt(2); a.write(new byte[2]); a.writeInt(0); });
        assertThat(read(fixture.compressed()).metadata().dataPatches()).isEqualTo(1);
        fixture.assets = MsavFixture.bytes(a -> { a.writeInt(0); a.writeInt(0); a.writeInt(1); a.writeUTF("image"); a.writeShort(8); a.writeShort(8); a.writeInt(3); a.write(new byte[3]); });
        assertThat(read(fixture.compressed()).tilesAvailable()).isTrue();
    }

    @Test
    void snapshotAndRegistryAreImmutableCopies() throws Exception {
        var snapshot = read(new MsavFixture().compressed());
        int[] floors = new int[25], overlays = new int[25], blocks = new int[25];
        var copy = new MapSnapshot(snapshot.metadata(), snapshot.registry(), floors, overlays, blocks, new int[blocks.length], snapshot.cores(), List.of(), true);
        floors[0] = 999; overlays[0] = 999; blocks[0] = 999;
        assertThat(copy.floor(0)).isZero(); assertThat(copy.overlay(0)).isZero(); assertThat(copy.block(0)).isZero();
        assertThatThrownBy(() -> copy.cores().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> copy.registry().blocks().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private MapSnapshot read(byte[] bytes) throws Exception {
        Path file = Files.createTempFile(directory, "adversarial-", ".msav"); Files.write(file, bytes);
        return reader.read(file, "0".repeat(64), MsavFixture.registry(), MsavFixture.settings());
    }
    private void assertInvalid(byte[] bytes) { assertThatThrownBy(() -> read(bytes)).isInstanceOf(IOException.class); }
}
