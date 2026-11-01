package org.xcore.plugin.service.map.inspect;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Small wire fixtures written directly, never through SaveIO or the live world. */
public final class MsavFixture {
    public int version = 13, width = 5, height = 5;
    public final Map<String, String> tags = new LinkedHashMap<>();
    public final List<String> names = new ArrayList<>(List.of("air", "stone", "spawn", "metal-tiles-2", "metal-wall-2", "core-shard", "sand-floor"));
    public final int[] floors = new int[25], overlays = new int[25], blocks = new int[25], teams = new int[25];
    public byte[] assets, entities, custom, mapOverride;
    public final Map<Integer, byte[]> buildingPayloads = new HashMap<>();

    public MsavFixture() {
        tags.put("name", "Fixture"); tags.put("author", "Mapper"); tags.put("description", "Detached fixture");
        tags.put("width", "5"); tags.put("height", "5"); tags.put("build", "160"); tags.put("mods", "[]");
        tags.put("rules", "{waves:false,waveTimer:false,dropZoneRadius:0,spawns:[]}");
        Arrays.fill(floors, 1);
        for (int x = 0; x < 4; x++) { floors[x + 10] = 3; blocks[x + 5] = 4; blocks[x + 15] = 4; }
        overlays[10] = 2; blocks[14] = 5; teams[14] = 1;
    }

    public Path write(Path directory) throws IOException {
        Path file = Files.createTempFile(directory, "fixture-", ".msav");
        Files.write(file, compressed());
        return file;
    }

    public byte[] compressed() throws IOException { return compress(raw()); }

    public byte[] raw() throws IOException {
        return bytes(output -> {
            output.writeInt(0x4d534156); output.writeInt(version);
            region(output, bytes(meta -> {
                meta.writeShort(tags.size());
                for (var tag : tags.entrySet()) { meta.writeUTF(tag.getKey()); meta.writeUTF(tag.getValue()); }
            }));
            if (version >= 12) region(output, assets == null ? bytes(a -> { a.writeInt(0); a.writeInt(0); if (version == 12) a.writeInt(0); }) : assets);
            region(output, bytes(content -> {
                content.writeByte(1); content.writeByte(ContentRegistrySnapshot.BLOCK_TYPE); content.writeShort(names.size());
                for (String name : names) content.writeUTF(name);
            }));
            if (version == 11) region(output, assets == null ? new byte[]{0} : assets);
            region(output, mapOverride == null ? map() : mapOverride);
            region(output, entities == null ? bytes(e -> {
                if (version >= 5) e.writeShort(0); // custom entity mapping
                if (version >= 3) e.writeInt(0); // teams with plans
                if (version <= 3) e.writeByte(0); else e.writeInt(0); // entity groups/count
            }) : entities);
            if (version >= 8) region(output, new byte[]{'{', '}'}); // empty UBJSON marker map
            if (version >= 7) region(output, custom == null ? new byte[4] : custom);
        });
    }

    public byte[] map() throws IOException {
        // A core-shard really is 3x3. Generate native part records, including those before the center.
        int[] owners = new int[blocks.length];
        Arrays.fill(owners, -1);
        for (int i = 0; i < blocks.length; i++) if ((long) width * height == blocks.length && blocks[i] == 5) {
            int x = i % width, y = i / width;
            for (int yy = Math.max(0, y - 1); yy < Math.min(height, y + 2); yy++)
                for (int xx = Math.max(0, x - 1); xx < Math.min(width, x + 2); xx++)
                    owners[xx + yy * width] = i;
        }
        return bytes(map -> {
            map.writeShort(width); map.writeShort(height);
            for (int i = 0; i < floors.length; i++) { map.writeShort(floors[i]); map.writeShort(overlays[i]); map.writeByte(0); }
            for (int i = 0; i < blocks.length; i++) {
                boolean core = blocks[i] == 5, part = owners[i] >= 0 && !core;
                map.writeShort(part ? (version <= 3 ? 0 : 5) : blocks[i]);
                if (version <= 3) {
                    if (core) {
                        byte[] payload = buildingPayloads.getOrDefault(i, legacyCoreBase(teams[i]));
                        map.writeShort(payload.length); map.write(payload);
                    } else map.writeByte(0);
                } else {
                    map.writeByte(core || part ? 1 : 0);
                    if (core || part) {
                        map.writeBoolean(core);
                        if (core) {
                            byte[] payload = buildingPayloads.getOrDefault(i, modernCoreBase(teams[i]));
                            if (version <= 9) map.writeShort(payload.length); else map.writeInt(payload.length);
                            map.write(payload);
                        }
                    } else map.writeByte(0);
                }
            }
        });
    }

    /** LegacySaveVersion: revision, unsigned health, packed team/rotation, item module, consume. */
    public static byte[] legacyCoreBase(int team) throws IOException {
        return bytes(entity -> {
            entity.writeByte(0); entity.writeShort(1000);
            entity.writeByte(team < 8 ? team << 4 : 0x80);
            if (team >= 8) entity.writeByte(team);
            entity.writeByte(0); // empty legacy ItemModule (core-shard.hasItems)
            entity.writeBoolean(true); // consume
        });
    }

    /** BuildingComp v3 base: revision, float health, marked rotation, team, version, enabled, modules, efficiencies. */
    public static byte[] modernCoreBase(int team) throws IOException {
        return bytes(entity -> {
            entity.writeByte(0); entity.writeFloat(1000); entity.writeByte(0x80); entity.writeByte(team);
            entity.writeByte(3); entity.writeBoolean(true); entity.writeByte(9); // items + historical consume bit
            entity.writeShort(0); // empty ItemModule
            entity.writeByte(255); entity.writeByte(255); // efficiency and optionalEfficiency
        });
    }

    public static ContentRegistrySnapshot registry() {
        var entries = List.of(new ContentRegistrySnapshot.Entry(0, "air", true, false, false, false, 1),
                new ContentRegistrySnapshot.Entry(10, "stone", true, false, false, false, 1),
                new ContentRegistrySnapshot.Entry(20, "spawn", true, true, false, false, 1),
                new ContentRegistrySnapshot.Entry(30, "metal-tiles-2", true, false, false, false, 1),
                new ContentRegistrySnapshot.Entry(40, "metal-wall-2", false, false, false, false, 1),
                new ContentRegistrySnapshot.Entry(50, "core-shard", false, false, true, true, 3, 1),
                new ContentRegistrySnapshot.Entry(60, "sand-floor", true, false, false, false, 1));
        var names = new HashMap<String, ContentRegistrySnapshot.Entry>();
        var ids = new HashMap<Integer, ContentRegistrySnapshot.Entry>();
        for (var entry : entries) { names.put(entry.name(), entry); ids.put(entry.id(), entry); }
        return new ContentRegistrySnapshot(Map.of(ContentRegistrySnapshot.BLOCK_TYPE, names), ids, Map.of("sand", "sand-floor"),
                Map.of(0, "derelict", 1, "sharded", 2, "crux", 200, "team#200"), new MapRules("survival", 1, 2, false, true, 0, 300));
    }

    public static InspectionSettings settings() {
        return new InspectionSettings(4194304, 360000,
                Map.of("tower-defense", new InspectionSettings.Profile("metal-tiles-2", "metal-wall-2", 0.05, true)));
    }

    public static byte[] compress(byte[] bytes) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var output = new DeflaterOutputStream(buffer)) { output.write(bytes); }
        return buffer.toByteArray();
    }

    public static byte[] inflate(byte[] bytes) throws IOException {
        try (var input = new InflaterInputStream(new ByteArrayInputStream(bytes))) { return input.readAllBytes(); }
    }

    public static byte[] bytes(Writer writer) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) { writer.write(output); }
        return buffer.toByteArray();
    }

    public static void region(DataOutputStream output, byte[] payload) throws IOException { output.writeInt(payload.length); output.write(payload); }
    @FunctionalInterface public interface Writer { void write(DataOutputStream output) throws IOException; }
}
