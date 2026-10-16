package org.xcore.plugin.service.map.inspect;

import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.ctype.ContentType;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.InflaterInputStream;
import java.util.zip.Inflater;

/**
 * Mindustry v160 save formats 1..13, decoded without engine readers or callbacks.
 * Region and tile/building framing is checked; entity, marker and subclass building payloads are opaque.
 * Embedded gameplay patches/content are counted and skipped: applying them would require mutating engine content.
 * See v160 SaveVersion, LegacySaveVersion, ShortChunkSaveVersion and BuildingComp.readBase.
 */
public final class DetachedMsavReader {
    public MapSnapshot read(Path file, String sha256, ContentRegistrySnapshot registry, InspectionSettings settings) throws IOException {
        long size = Files.size(file);
        if (size > settings.maxFileBytes()) throw new IOException("Map exceeds maxFileBytes");
        var zlib = new Inflater();
        try (var compressed = new BufferedInputStream(Files.newInputStream(file));
             var inflated = new InflaterInputStream(compressed, zlib);
             var budget = new BudgetInput(inflated, InspectionSettings.MAX_INFLATED_BYTES);
             var input = new DataInputStream(budget)) {
            if (input.readInt() != 0x4d534156) throw new IOException("Incorrect MSAV magic");
            int version = input.readInt();
            if (version < 1 || version > 13) throw new IOException("Unsupported save version: " + version);
            var tags = readMetadata(region(input, 1024 * 1024));
            int width = dimension(tags, "width"), height = dimension(tags, "height");
            boolean oversized = (long) width * height > settings.maxTiles();
            int dataPatches = version >= 12 ? readAssets(region(input, InspectionSettings.MAX_REGION_BYTES), version) : 0;
            var unknown = new TreeSet<String>();
            var mapping = readContent(region(input, 4 * 1024 * 1024), registry, unknown, !oversized);
            if (version == 11) dataPatches = readAssets(region(input, InspectionSettings.MAX_REGION_BYTES), version);
            var metadata = new MapSnapshot.Metadata(tags.getOrDefault("name", ""), tags.get("author"),
                    tags.get("description"), width, height, version, optionalInt(tags.get("build")),
                    readRules(tags.getOrDefault("rules", "{}"), registry.defaults()),
                    readMods(tags.getOrDefault("mods", "[]")), sha256, (int) size, dataPatches);
            var map = region(input, InspectionSettings.MAX_REGION_BYTES);
            int mapWidth = map.data.readUnsignedShort(), mapHeight = map.data.readUnsignedShort();
            if (mapWidth != width || mapHeight != height) throw new IOException("Metadata and map dimensions disagree");
            if (oversized) {
                // Deliberately stop before tile arrays, buildings or entities. The partial result has no tile claims.
                return new MapSnapshot(metadata, registry, new int[0], new int[0], new int[0], List.of(),
                        List.copyOf(unknown), false);
            }
            int count = width * height;
            int[] floors = new int[count], overlays = new int[count], blocks = new int[count];
            var cores = new ArrayList<MapSnapshot.Core>();
            readFloors(map.data, mapping, registry, unknown, floors, overlays);
            readBlocks(map, version, mapping, registry, unknown, blocks, cores, width);
            map.end();
            // These engine regions may contain arbitrary mod/entity data. Verify their framing and consume
            // them through the inflation budget, without executing deserializers or allocating their lengths.
            readEntities(region(input, InspectionSettings.MAX_REGION_BYTES), version);
            if (version >= 8) region(input, InspectionSettings.MAX_REGION_BYTES).discard(); // markers
            if (version >= 7) readCustom(region(input, InspectionSettings.MAX_REGION_BYTES));
            if (input.read() != -1) throw new IOException("Unexpected data after save regions");
            if (!zlib.finished() || zlib.getRemaining() != 0 || compressed.read() != -1)
                throw new IOException("Unexpected data after zlib stream");
            return new MapSnapshot(metadata, registry, floors, overlays, blocks, cores, List.copyOf(unknown), true);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new IOException("Invalid map metadata or content", ex);
        } finally { zlib.end(); }
    }

    private static Map<String, String> readMetadata(Region region) throws IOException {
        int count = region.data.readShort();
        if (count < 0 || count > 256) throw new IOException("Invalid metadata tag count");
        var tags = new LinkedHashMap<String, String>();
        for (int i = 0; i < count; i++) {
            String key = region.data.readUTF(), value = region.data.readUTF();
            if (key.length() > 256 || tags.putIfAbsent(key, value) != null) throw new IOException("Invalid metadata tag");
        }
        region.end();
        return tags;
    }

    private static int dimension(Map<String, String> tags, String key) throws IOException {
        Integer value = optionalInt(tags.get(key));
        if (value == null || value <= 0 || value > 65535) throw new IOException("Invalid map " + key);
        return value;
    }

    private static Integer optionalInt(String value) throws IOException {
        if (value == null) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException ex) { throw new IOException("Invalid metadata integer", ex); }
    }

    private static JsonValue json(String value) throws IOException {
        try {
            // Pure syntax parsing only: Arc minimal JSON also permits unquoted strings with spaces/quotes.
            // JsonIO/Json object deserialization would resolve live content and must never be used here.
            var result = new BoundedJsonReader().parse(value);
            if (result == null) throw new IOException("Empty metadata JSON");
            return result;
        } catch (RuntimeException ex) { throw new IOException("Invalid metadata JSON", ex); }
    }

    private static MapRules readRules(String value, MapRules defaults) throws IOException {
        var rules = json(value);
        if (!rules.isObject()) throw new IOException("Rules must be an object");
        boolean pvp = bool(rules, "pvp", false), editor = bool(rules, "editor", false);
        boolean attack = bool(rules, "attackMode", false), infiniteResources = bool(rules, "infiniteResources", false);
        String mode = pvp ? "pvp" : editor ? "editor" : attack ? "attack" : infiniteResources ? "sandbox" : defaults.gameMode();
        int spawns = defaults.spawnGroupCount();
        var groups = field(rules, "spawns");
        if (groups != null) {
            if (!groups.isArray()) throw new IOException("spawns must be an array");
            spawns = groups.size;
            for (var spawn : groups) if (!spawn.isObject()) throw new IOException("Invalid spawn group");
        }
        double radius = defaults.dropZoneRadius();
        var number = field(rules, "dropZoneRadius");
        if (number != null) {
            if (!number.isNumber()) throw new IOException("Invalid dropZoneRadius");
            radius = number.asFloat(); // Rules.dropZoneRadius is a float, including its underflow behavior.
            if (!Double.isFinite(radius)) throw new IOException("Non-finite dropZoneRadius");
        }
        return new MapRules(mode, team(rules, "defaultTeam", defaults.defaultTeam()), team(rules, "waveTeam", defaults.waveTeam()),
                bool(rules, "waves", defaults.waves()), bool(rules, "waveTimer", defaults.waveTimer()), spawns, radius);
    }

    private static boolean bool(JsonValue object, String key, boolean fallback) throws IOException {
        var value = field(object, key);
        if (value == null) return fallback;
        if (!value.isBoolean()) throw new IOException("Invalid rule: " + key);
        return value.asBoolean();
    }

    private static int team(JsonValue object, String key, int fallback) throws IOException {
        var value = field(object, key);
        if (value == null) return fallback;
        if (!value.isNumber()) throw new IOException("Invalid team rule");
        try {
            int id = new BigDecimal(value.asString()).intValueExact();
            if (id < 0 || id > 255) throw new IOException("Invalid team ID");
            return id;
        } catch (ArithmeticException ex) { throw new IOException("Invalid team ID", ex); }
    }

    private static List<String> readMods(String value) throws IOException {
        var parsed = json(value);
        if (!parsed.isArray()) throw new IOException("mods must be an array");
        var mods = new TreeSet<String>();
        for (var mod : parsed) {
            if (!mod.isString()) throw new IOException("Invalid mod name");
            if (!mod.asString().isBlank()) mods.add(mod.asString());
        }
        return List.copyOf(mods);
    }

    private static JsonValue field(JsonValue object, String name) {
        // Engine object deserialization uses exact field names, unlike JsonValue.get's case-insensitive lookup.
        for (var child = object.child; child != null; child = child.next) if (name.equals(child.name)) return child;
        return null;
    }

    /** Arc's parser is iterative; reject depth before it grows its parse stack, and reject additional roots. */
    private static final class BoundedJsonReader extends JsonReader {
        private int depth, roots;
        private final Deque<Set<String>> fields = new ArrayDeque<>();
        private void value(String name) {
            if (depth == 0 && ++roots > 1) throw new IllegalArgumentException("Trailing metadata JSON");
            if (name != null && !fields.isEmpty() && !fields.peek().add(name))
                throw new IllegalArgumentException("Duplicate metadata JSON field");
        }
        private void enter(String name) {
            value(name);
            if (++depth > 64) throw new IllegalArgumentException("Metadata JSON nesting limit exceeded");
        }
        @Override protected void startObject(String name) { enter(name); fields.push(new HashSet<>()); super.startObject(name); }
        @Override protected void startArray(String name) { enter(name); fields.push(Set.of()); super.startArray(name); }
        @Override protected void pop() { super.pop(); fields.pop(); depth--; }
        @Override protected void string(String name, String value) { value(name); super.string(name, value); }
        @Override protected void number(String name, double value, String text) { value(name); super.number(name, value, text); }
        @Override protected void number(String name, long value, String text) { value(name); super.number(name, value, text); }
        @Override protected void bool(String name, boolean value) { value(name); super.bool(name, value); }
    }

    private static ContentRegistrySnapshot.Entry[] readContent(Region region, ContentRegistrySnapshot registry,
                                                                Set<String> unknown, boolean collect) throws IOException {
        int count = region.data.readUnsignedByte(), totalNames = 0;
        if (count > ContentType.all.length) throw new IOException("Invalid content type count");
        var seen = new HashSet<Integer>();
        ContentRegistrySnapshot.Entry[] blocks = null;
        for (int i = 0; i < count; i++) {
            int type = region.data.readUnsignedByte(), total = region.data.readShort();
            if (type >= ContentType.all.length || !seen.add(type) || total < 0 || (totalNames += total) > 65536)
                throw new IOException("Invalid content header");
            var entries = collect && type == ContentRegistrySnapshot.BLOCK_TYPE ? new ContentRegistrySnapshot.Entry[total] : null;
            for (int j = 0; j < total; j++) {
                String name = region.data.readUTF();
                var entry = registry.resolve(type, name);
                if (entry == null) unknown.add(ContentType.all[type].name() + ":" + name);
                if (entries != null) entries[j] = entry;
            }
            if (entries != null) blocks = entries;
        }
        region.end();
        if (!collect) return null;
        // Engine falls back to the native registry for absent/empty block headers.
        if (blocks == null || blocks.length == 0) {
            int length = registry.blocks().keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            blocks = new ContentRegistrySnapshot.Entry[length];
            for (var entry : registry.blocks().values()) blocks[entry.id()] = entry;
        }
        return blocks;
    }

    private static ContentRegistrySnapshot.Entry block(DataInputStream input, ContentRegistrySnapshot.Entry[] mapping) throws IOException {
        int id = input.readShort();
        if (id < 0 || id >= mapping.length) throw new IOException("Invalid block content ID");
        return mapping[id]; // a missing named mod block stays unknown, rather than becoming native ID zero
    }

    private static void readFloors(DataInputStream input, ContentRegistrySnapshot.Entry[] mapping,
                                   ContentRegistrySnapshot registry, Set<String> unknown, int[] floors, int[] overlays) throws IOException {
        var stone = registry.resolve(ContentRegistrySnapshot.BLOCK_TYPE, "stone");
        for (int i = 0; i < floors.length;) {
            var floor = block(input, mapping);
            var overlay = block(input, mapping);
            int run = input.readUnsignedByte() + 1;
            if (run > floors.length - i) throw new IOException("Floor run exceeds tile count");
            if (floor != null && floor.name().equals("air")) floor = stone;
            if (floor != null && (!floor.floor() || floor.overlay())) unknown.add("incompatible floor:" + floor.name());
            if (overlay != null && !overlay.floor() && !overlay.name().equals("air")) unknown.add("incompatible overlay:" + overlay.name());
            Arrays.fill(floors, i, i + run, floor == null ? -1 : floor.id());
            Arrays.fill(overlays, i, i + run, overlay == null ? -1 : overlay.id());
            i += run;
        }
    }

    private static void readBlocks(Region map, int version, ContentRegistrySnapshot.Entry[] mapping,
                                   ContentRegistrySnapshot registry, Set<String> unknown, int[] blocks, List<MapSnapshot.Core> cores, int width) throws IOException {
        // Keep wire parts separate from effective blocks: parts before a center do not place anything,
        // while later ordinary records (including air RLE) remove a previously placed multiblock.
        boolean[] parts = new boolean[blocks.length];
        var centers = new ArrayList<Building>();
        for (int i = 0; i < blocks.length;) {
            var block = block(map.data, mapping);
            if (block != null && block.floor() && !block.name().equals("air")) unknown.add("incompatible block:" + block.name());
            int run = 1, team = 0;
            boolean center = true;
            if (version <= 3) {
                if (block == null) throw new IOException("Unknown legacy block has ambiguous building format");
                if (block.building()) {
                    var payload = shortRegion(map);
                    payload.data.readUnsignedByte(); // subclass revision
                    payload.data.readUnsignedShort(); // legacy health
                    int packed = payload.data.readUnsignedByte();
                    team = packed >>> 4;
                    if (team == 8) team = payload.data.readUnsignedByte();
                    readModules(payload, block.modules(), true);
                    readBoolean(payload.data, "consume");
                    payload.discard(); // subclass gameplay bytes only
                } else run += map.data.readUnsignedByte();
            } else {
                int flags = map.data.readUnsignedByte();
                if ((flags & ~(version <= 9 ? 7 : 5)) != 0) throw new IOException("Invalid tile flags");
                boolean entity = (flags & 1) != 0, oldData = (flags & 2) != 0, newData = (flags & 4) != 0;
                if (newData) map.skip(7);
                if (entity) {
                    center = readBoolean(map.data, "building center");
                    if (center) {
                        var payload = version <= 9 ? shortRegion(map) : nestedRegion(map);
                        // Removed/unknown blocks have opaque chunks, exactly as the native reader skips them.
                        if (block != null && block.building()) team = readBuildingBase(payload, block);
                        payload.discard();
                    } else parts[i] = true;
                } else if (oldData || newData) {
                    if (oldData) map.skip(1);
                } else run += map.data.readUnsignedByte();
            }
            if (run > blocks.length - i) throw new IOException("Block run exceeds tile count");
            Arrays.fill(blocks, i, i + run, block == null ? -1 : block.id());
            if (center && block != null && (block.building() || block.size() > 1)) {
                // Entity-free native buildings are placed with the engine's derelict default team.
                for (int j = i; j < i + run; j++) centers.add(new Building(j, block, team));
            }
            i += run;
        }
        var air = registry.resolve(ContentRegistrySnapshot.BLOCK_TYPE, "air");
        reconcileBuildings(version, blocks, parts, centers, cores, width, air == null ? -2 : air.id());
    }

    private static int readBuildingBase(Region payload, ContentRegistrySnapshot.Entry block) throws IOException {
        payload.data.readUnsignedByte(); // subclass revision
        if (!Float.isFinite(payload.data.readFloat())) throw new IOException("Invalid building health");
        int rotation = payload.data.readUnsignedByte(), team = payload.data.readUnsignedByte();
        boolean legacy = (rotation & 0x80) == 0;
        int version = 0, modules = block.modules();
        if (!legacy) {
            version = payload.data.readUnsignedByte();
            if (version > 4) throw new IOException("Unsupported building base version: " + version);
            if (version >= 1) readBoolean(payload.data, "building enabled");
            if (version >= 2) {
                modules = payload.data.readUnsignedByte();
                if ((modules & ~0x3f) != 0) throw new IOException("Invalid building module flags");
            }
        }
        readModules(payload, modules, legacy);
        if ((modules & 16) != 0) payload.skip(8); // timeScale, timeScaleDuration
        if ((modules & 32) != 0) payload.skip(4); // lastDisabler position, never resolve against world
        if (version <= 2) readBoolean(payload.data, "consume");
        if (version >= 3) payload.skip(2); // efficiency and optionalEfficiency
        if (version == 4) payload.skip(8); // visibleFlags
        return team;
    }

    private static void readModules(Region payload, int modules, boolean legacy) throws IOException {
        if ((modules & 1) != 0) readInventory(payload, legacy, "item");
        if ((modules & 2) != 0) {
            int count = payload.data.readShort();
            if (count < 0) throw new IOException("Invalid power link count");
            payload.skip((long) count * 4 + 4); // positions, status
        }
        if ((modules & 4) != 0) readInventory(payload, legacy, "liquid");
    }

    private static void readInventory(Region payload, boolean legacy, String module) throws IOException {
        int count = legacy ? payload.data.readUnsignedByte() : payload.data.readShort();
        if (count < 0) throw new IOException("Invalid " + module + " module count");
        payload.skip((long) count * (legacy ? 5 : 6)); // content ID + int amount/float amount
    }

    private static boolean readBoolean(DataInputStream input, String field) throws IOException {
        int value = input.readUnsignedByte();
        if (value > 1) throw new IOException("Invalid " + field + " flag");
        return value == 1;
    }

    private record Building(int index, ContentRegistrySnapshot.Entry block, int team) {}

    private static void reconcileBuildings(int version, int[] blocks, boolean[] parts, List<Building> centers,
                                           List<MapSnapshot.Core> cores, int width, int airId) throws IOException {
        int height = blocks.length / width;
        int[] owners = new int[blocks.length];
        Arrays.fill(owners, -1);
        for (int owner = 0; owner < centers.size(); owner++) {
            Building center = centers.get(owner);
            int size = center.block.size(), offset = -(size - 1) / 2;
            if (size < 1 || size > 65535) throw new IOException("Invalid native building size");
            int x = center.index % width, y = center.index / width;
            // Tile.setBlock clips footprints at the map edge; even sizes extend right/up from the anchor.
            for (int yy = Math.max(0, y + offset); yy < Math.min(height, y + offset + size); yy++) {
                for (int xx = Math.max(0, x + offset); xx < Math.min(width, x + offset + size); xx++) {
                    int index = xx + yy * width;
                    // Tile.setBlock removes whatever a new footprint touches; a later center would erase this one.
                    if (owners[index] >= 0) throw new IOException("Overlapping native building footprints at " + xx + "," + yy);
                    owners[index] = owner;
                }
            }
        }
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] < 0) {
                if (parts[i] && blocks[i] >= 0) throw new IOException("Orphan native building part at " + at(i, width));
                continue;
            }
            Building center = centers.get(owners[i]);
            if (i == center.index) continue;
            if (parts[i]) {
                // IDs are resolved registry identities, never hard-coded save/native part IDs.
                if (blocks[i] >= 0 && blocks[i] != center.block.id())
                    throw new IOException("Inconsistent native building part at " + at(i, width));
            } else if (i > center.index) {
                // Legacy readers explicitly preserve occupied parts encoded as air or the same block.
                boolean legacyPlaceholder = version <= 3 && (blocks[i] == airId || blocks[i] == center.block.id());
                if (!legacyPlaceholder) throw new IOException("Overwritten native building footprint at " + at(i, width));
            }
            blocks[i] = center.block.id();
        }
        for (Building center : centers) {
            if (center.block.core()) cores.add(new MapSnapshot.Core(center.index % width, center.index / width,
                    center.block.size(), center.team));
        }
    }

    private static String at(int index, int width) { return index % width + "," + index / width; }

    /** Skips the embedded assets and returns how many of them are gameplay patches or content. */
    private static int readAssets(Region region, int version) throws IOException {
        // Patches are not applied: the checks see the map as the vanilla content would load it.
        int gameplay = 0;
        if (version == 11) {
            gameplay = region.data.readUnsignedByte();
            for (int i = 0; i < gameplay; i++) nestedRegion(region).discard();
        } else if (version == 12) {
            region.data.readInt(); // asset format revision
            gameplay = count(region);
            for (int i = 0; i < gameplay; i++) nestedRegion(region).discard();
            int images = count(region);
            for (int i = 0; i < images; i++) {
                region.data.readUTF();
                region.data.readUnsignedShort(); region.data.readUnsignedShort();
                nestedRegion(region).discard();
            }
        } else {
            region.data.readInt(); // asset format revision
            int count = count(region);
            for (int i = 0; i < count; i++) {
                int type = region.data.readUnsignedByte();
                if (type >= 6) throw new IOException("Invalid asset type");
                region.data.readUTF();
                int embedded = region.data.readUnsignedByte();
                if (embedded > 1) throw new IOException("Invalid embedded asset flag");
                if (type <= 1) gameplay++; // patch or content
                if (embedded == 1) nestedRegion(region).discard();
                else region.skip(32);
            }
        }
        region.end();
        return gameplay;
    }

    private static void readCustom(Region region) throws IOException {
        int count = count(region);
        for (int i = 0; i < count; i++) {
            region.data.readUTF();
            nestedRegion(region).discard();
        }
        region.end();
    }

    /** Framing and build-plan configs only; no EntityMapping, TypeIO or entity constructors are used. */
    private static void readEntities(Region region, int version) throws IOException {
        if (version >= 5) {
            int mappings = region.data.readShort();
            if (mappings < 0 || mappings > 256) throw new IOException("Invalid entity mapping count");
            for (int i = 0; i < mappings; i++) {
                int id = region.data.readShort();
                if (id < 0 || id > 255) throw new IOException("Invalid entity mapping ID");
                region.data.readUTF();
            }
        }
        if (version >= 3) {
            int teams = count(region);
            if (teams > 256) throw new IOException("Invalid team count");
            for (int i = 0; i < teams; i++) {
                int team = region.data.readInt();
                if (team < 0 || team > 255) throw new IOException("Invalid plan team ID");
                int plans = count(region);
                for (int j = 0; j < plans; j++) {
                    region.skip(8); // x, y, rotation, block ID
                    if (version == 3) region.skip(4); // legacy integer config
                    else skipConfig(region, true);
                }
            }
        }
        if (version <= 3) {
            int groups = region.data.readByte();
            if (groups < 0) throw new IOException("Invalid legacy entity group count");
            for (int i = 0; i < groups; i++) {
                int entities = count(region);
                for (int j = 0; j < entities; j++) shortRegion(region).discard();
            }
        } else {
            int entities = count(region);
            for (int i = 0; i < entities; i++) {
                var payload = version <= 9 ? shortRegion(region) : nestedRegion(region);
                payload.data.readUnsignedByte(); // entity type; remainder can belong to a removed mod
                payload.discard();
            }
        }
        region.end();
    }

    /** v160 TypeIO object wire framing, with no content/world/group resolution or length-sized allocations. */
    private static void skipConfig(Region region, boolean arrays) throws IOException {
        int type = region.data.readUnsignedByte();
        if (!arrays && (type == 6 || type == 8 || type == 14 || type == 16 || type == 18 || type == 22))
            throw new IOException("Nested plan config arrays are unsupported");
        switch (type) {
            case 0 -> {}
            case 1, 3, 12, 17 -> region.skip(4);
            case 2, 7, 11, 19 -> region.skip(8);
            case 4 -> { if (region.data.readUnsignedByte() != 0) region.data.readUTF(); }
            case 5, 9 -> {
                if (region.data.readUnsignedByte() >= ContentType.all.length) throw new IOException("Invalid config content type");
                region.skip(2);
            }
            case 6, 18, 21 -> {
                int length = region.data.readShort();
                if (length < 0 || (type != 21 && length > 200)) throw new IOException("Invalid config array length");
                region.skip((long) length * (type == 18 ? 8 : 4));
            }
            case 8 -> region.skip((long) region.data.readUnsignedByte() * 4);
            case 10, 15, 20 -> region.skip(1);
            case 13, 23 -> region.skip(2);
            case 14, 16 -> {
                int length = region.data.readInt();
                if (length < 0 || length > (type == 14 ? 40000 : 200)) throw new IOException("Invalid config array length");
                region.skip(length);
            }
            case 22 -> {
                int length = region.data.readInt();
                if (length < 0 || length > 200) throw new IOException("Invalid config array length");
                for (int i = 0; i < length; i++) skipConfig(region, false);
            }
            default -> throw new IOException("Unknown plan config type");
        }
    }

    private static int count(Region region) throws IOException {
        int count = region.data.readInt();
        if (count < 0 || count > 65536 || count > region.remaining()) throw new IOException("Invalid region entry count");
        return count;
    }

    private static Region region(DataInputStream input, long max) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > max || length > InspectionSettings.MAX_REGION_BYTES) throw new IOException("Invalid region length");
        return new Region(input, length);
    }

    private static Region shortRegion(Region parent) throws IOException {
        int length = parent.data.readUnsignedShort();
        if (length > parent.remaining()) throw new IOException("Invalid short chunk length");
        return new Region(parent.data, length);
    }

    private static Region nestedRegion(Region parent) throws IOException {
        int length = parent.data.readInt();
        if (length < 0 || length > parent.remaining()) throw new IOException("Invalid nested region length");
        return new Region(parent.data, length);
    }

    private static final class Region {
        private final SegmentInput input;
        private final DataInputStream data;
        Region(InputStream input, long length) { this.input = new SegmentInput(input, length); data = new DataInputStream(this.input); }
        long remaining() { return input.remaining; }
        void end() throws IOException { if (remaining() != 0) throw new IOException("Region length mismatch"); }
        void skip(long length) throws IOException {
            if (length < 0 || length > remaining()) throw new IOException("Invalid skip length");
            data.skipNBytes(length);
        }
        void discard() throws IOException { skip(remaining()); }
    }

    private static final class SegmentInput extends FilterInputStream {
        private long remaining;
        SegmentInput(InputStream input, long length) { super(input); remaining = length; }
        @Override public int read() throws IOException {
            if (remaining == 0) return -1;
            int result = in.read();
            if (result < 0) throw new EOFException("Truncated region");
            remaining--;
            return result;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            int result = in.read(bytes, offset, (int) Math.min(length, remaining));
            if (result < 0) throw new EOFException("Truncated region");
            remaining -= result;
            return result;
        }
        @Override public long skip(long length) throws IOException { return readSkip(this, Math.min(length, remaining)); }
    }

    private static final class BudgetInput extends FilterInputStream {
        private long remaining;
        BudgetInput(InputStream input, long budget) { super(input); remaining = budget; }
        @Override public int read() throws IOException {
            int result = in.read();
            if (result >= 0 && --remaining < 0) throw new IOException("Inflated map exceeds byte limit");
            return result;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int result = in.read(bytes, offset, (int) Math.min(length, remaining + 1));
            if (result > 0 && (remaining -= result) < 0) throw new IOException("Inflated map exceeds byte limit");
            return result;
        }
        @Override public long skip(long length) throws IOException { return readSkip(this, length); }
    }

    private static long readSkip(InputStream input, long length) throws IOException {
        if (length == 0) return 0;
        byte[] scratch = new byte[(int) Math.min(8192, length)];
        long skipped = 0;
        while (skipped < length) {
            int read = input.read(scratch, 0, (int) Math.min(scratch.length, length - skipped));
            if (read < 0) break;
            skipped += read;
        }
        return skipped;
    }
}
