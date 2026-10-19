package org.xcore.plugin.service.map.inspect;

import java.util.*;

import static org.xcore.plugin.service.map.inspect.MapFinding.Severity.*;

/** Pure checks with documented defaults and the captured operator policy. */
public final class MapChecks {
    private final List<MapCheck> checks = List.of(MapChecks::metadata, MapChecks::gameplay, MapChecks::towerDefense);

    public List<MapFinding> check(MapSnapshot snapshot, String profile, InspectionSettings settings) {
        var findings = new ArrayList<MapFinding>();
        for (var check : checks) findings.addAll(check.check(snapshot, profile, settings));
        return findings.stream().map(finding -> new MapFinding(finding.code(),
                settings.severities().getOrDefault(finding.code(), finding.severity()),
                finding.detail(), finding.x(), finding.y(), finding.count())).toList();
    }

    public static String profile(MapSnapshot snapshot, String requested) {
        return requested == null ? snapshot.metadata().rules().gameMode() : requested;
    }

    private static List<MapFinding> metadata(MapSnapshot snapshot, String profile, InspectionSettings settings) {
        var findings = new ArrayList<MapFinding>();
        var meta = snapshot.metadata();
        if (plain(meta.name()).isBlank()) findings.add(finding("missing-name", WARNING, "Map name is missing"));
        if (plain(meta.author()).isBlank()) findings.add(finding("missing-author", WARNING, "Map author is missing"));
        var dimensions = new ArrayList<String>();
        if ((long) meta.width() * meta.height() > settings.maxTiles()) dimensions.add(
                "exceeds " + settings.maxTiles() + " tiles; tile checks skipped");
        var bounds = settings.profiles().get(profile);
        if (bounds != null) {
            if (bounds.minWidth() > 0 && meta.width() < bounds.minWidth()) dimensions.add("width < min_width=" + bounds.minWidth());
            if (bounds.maxWidth() > 0 && meta.width() > bounds.maxWidth()) dimensions.add("width > max_width=" + bounds.maxWidth());
            if (bounds.minHeight() > 0 && meta.height() < bounds.minHeight()) dimensions.add("height < min_height=" + bounds.minHeight());
            if (bounds.maxHeight() > 0 && meta.height() > bounds.maxHeight()) dimensions.add("height > max_height=" + bounds.maxHeight());
        }
        if (!dimensions.isEmpty()) findings.add(finding("size-out-of-range", ERROR,
                meta.width() + " x " + meta.height() + " " + String.join("; ", dimensions)));
        if (!meta.mods().isEmpty()) findings.add(new MapFinding("requires-mods", ERROR,
                "Required mods: " + String.join(", ", meta.mods()), null, null, meta.mods().size()));
        if (meta.dataPatches() > 0) findings.add(new MapFinding("data-patches", WARNING,
                "The map changes gameplay with data patches; these checks ignored them", null, null, meta.dataPatches()));
        if (!snapshot.unknownContent().isEmpty()) findings.add(new MapFinding("unknown-content", ERROR,
                "Unavailable or incompatible content: " + String.join(", ", snapshot.unknownContent()),
                null, null, snapshot.unknownContent().size()));
        return findings;
    }

    private static List<MapFinding> gameplay(MapSnapshot snapshot, String profile, InspectionSettings settings) {
        if (!snapshot.tilesAvailable()) return List.of();
        var findings = new ArrayList<MapFinding>();
        var rules = snapshot.metadata().rules();
        if (snapshot.cores().stream().noneMatch(core -> core.team() == rules.defaultTeam()))
            findings.add(finding("no-core", ERROR, "No core for default team " + snapshot.registry().teamName(rules.defaultTeam())));
        if ((profile.equals("survival") || profile.equals("tower-defense")) && snapshot.spawnCount() == 0)
            findings.add(finding("no-spawn", ERROR, "No spawn overlay"));
        if (profile.equals("pvp") && snapshot.cores().stream().map(MapSnapshot.Core::team).distinct().count() < 2)
            findings.add(finding("pvp-single-team", ERROR, "PvP requires cores for at least two teams"));
        if (profile.equals("attack") && snapshot.cores().stream().noneMatch(core -> core.team() == rules.waveTeam()))
            findings.add(finding("attack-no-enemy-core", ERROR, "No core for wave team " + snapshot.registry().teamName(rules.waveTeam())));
        return findings;
    }

    private static List<MapFinding> towerDefense(MapSnapshot snapshot, String profile, InspectionSettings settings) {
        if (!profile.equals("tower-defense") || !snapshot.tilesAvailable()) return List.of();
        var findings = new ArrayList<MapFinding>();
        var config = settings.profiles().get(profile);
        var rules = snapshot.metadata().rules();
        if (config != null && config.requireDropZoneRadiusZero() && rules.dropZoneRadius() != 0)
            findings.add(finding("td-drop-zone-radius", WARNING, "Drop zone radius is " + rules.dropZoneRadius() + "; expected zero"));
        if (rules.waves() || rules.waveTimer()) findings.add(finding("td-waves-enabled", INFO,
                "Vanilla waves=" + rules.waves() + ", waveTimer=" + rules.waveTimer() + "; Tower Defence ignores vanilla waves"));
        var spawnBlock = snapshot.registry().resolve(ContentRegistrySnapshot.BLOCK_TYPE, "spawn");
        if (spawnBlock == null) return findings;
        var spawns = new ArrayList<Integer>();
        var floorIds = new TreeSet<Integer>();
        for (int i = 0; i < snapshot.tileCount(); i++) if (snapshot.overlay(i) == spawnBlock.id()) {
            spawns.add(i);
            if (snapshot.floor(i) >= 0) floorIds.add(snapshot.floor(i));
        }
        if (floorIds.size() > 1) findings.add(new MapFinding("td-road-floor-mixed", ERROR,
                "Spawn overlays use multiple road floors: " + floorNames(snapshot, floorIds), null, null, floorIds.size()));
        if (config != null && !config.roadFloor().isBlank() && !floorIds.isEmpty()) {
            var expected = configured(snapshot, config.roadFloor());
            if (expected == null || !expected.floor() || floorIds.stream().anyMatch(id -> id != expected.id()))
                findings.add(finding("td-road-floor-unexpected", WARNING,
                        "Road floors: " + floorNames(snapshot, floorIds) + "; configured floor: " + config.roadFloor()));
        }
        if (spawns.isEmpty() || floorIds.isEmpty()) return findings;
        int width = snapshot.metadata().width(), height = snapshot.metadata().height(), count = snapshot.tileCount();
        boolean[] road = new boolean[count], coreArea = new boolean[count], coreNeighbour = new boolean[count];
        for (int i = 0; i < count; i++) road[i] = floorIds.contains(snapshot.floor(i));
        for (var core : snapshot.cores()) {
            int offset = -(core.size() - 1) / 2;
            int x1 = Math.max(0, core.x() + offset), y1 = Math.max(0, core.y() + offset);
            int x2 = Math.min(width, core.x() + offset + core.size()), y2 = Math.min(height, core.y() + offset + core.size());
            for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++) {
                int index = x + y * width;
                coreArea[index] = true;
                if (core.team() == rules.defaultTeam()) {
                    if (x > 0) coreNeighbour[index - 1] = true;
                    if (x + 1 < width) coreNeighbour[index + 1] = true;
                    if (y > 0) coreNeighbour[index - width] = true;
                    if (y + 1 < height) coreNeighbour[index + width] = true;
                }
            }
        }
        // Label each connected component once, rather than flood-filling the entire map for every spawn.
        int[] components = new int[count], queue = new int[count];
        var reachesCore = new ArrayList<Boolean>();
        reachesCore.add(false);
        int component = 0;
        for (int start = 0; start < count; start++) if (road[start] && components[start] == 0) {
            int head = 0, tail = 0;
            queue[tail++] = start;
            components[start] = ++component;
            boolean reaches = false;
            while (head < tail) {
                int index = queue[head++];
                reaches |= coreNeighbour[index];
                int x = index % width, y = index / width;
                if (x > 0) tail = enqueue(index - 1, road, components, component, queue, tail);
                if (x + 1 < width) tail = enqueue(index + 1, road, components, component, queue, tail);
                if (y > 0) tail = enqueue(index - width, road, components, component, queue, tail);
                if (y + 1 < height) tail = enqueue(index + width, road, components, component, queue, tail);
            }
            reachesCore.add(reaches);
        }
        var disconnected = new ArrayList<Integer>();
        var middle = new ArrayList<Integer>();
        for (int spawn : spawns) {
            if (!reachesCore.get(components[spawn])) disconnected.add(spawn);
            if (neighbours(road, spawn, width, height) > 1) middle.add(spawn);
        }
        if (!disconnected.isEmpty()) findings.add(locations("td-road-disconnected", ERROR,
                "Spawns whose road does not reach a tile next to a default-team core", disconnected, width));
        if (!middle.isEmpty()) findings.add(locations("td-spawn-not-at-road-start", WARNING,
                "Spawns with more than one road neighbour", middle, width));
        if (config != null && !config.borderWall().isBlank()) {
            var expected = configured(snapshot, config.borderWall());
            var missing = new ArrayList<Integer>();
            int borderCount = 0;
            for (int i = 0; i < count; i++) if (!road[i] && !coreArea[i] && neighbours(road, i, width, height) > 0) {
                borderCount++;
                if (expected == null || snapshot.block(i) != expected.id()) missing.add(i);
            }
            if (borderCount > 0 && (double) missing.size() / borderCount > config.borderTolerance())
                findings.add(locations("td-border-wall-missing", WARNING,
                        missing.size() + "/" + borderCount + " road border tiles lack " + config.borderWall(), missing, width));
        }
        return findings;
    }

    private static int enqueue(int index, boolean[] road, int[] components, int component, int[] queue, int tail) {
        if (road[index] && components[index] == 0) { components[index] = component; queue[tail++] = index; }
        return tail;
    }

    private static int neighbours(boolean[] tiles, int index, int width, int height) {
        int x = index % width, y = index / width;
        return (x > 0 && tiles[index - 1] ? 1 : 0) + (x + 1 < width && tiles[index + 1] ? 1 : 0)
                + (y > 0 && tiles[index - width] ? 1 : 0) + (y + 1 < height && tiles[index + width] ? 1 : 0);
    }

    private static ContentRegistrySnapshot.Entry configured(MapSnapshot snapshot, String name) {
        var entry = snapshot.registry().resolve(ContentRegistrySnapshot.BLOCK_TYPE, name);
        if (entry != null) return entry;
        try { return snapshot.registry().blocks().get(Integer.parseInt(name)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static String floorNames(MapSnapshot snapshot, Set<Integer> ids) {
        return ids.stream().map(id -> snapshot.registry().blocks().get(id))
                .map(entry -> entry == null ? "unknown" : entry.name()).sorted().collect(java.util.stream.Collectors.joining(", "));
    }

    private static MapFinding locations(String code, MapFinding.Severity severity, String detail, List<Integer> indices, int width) {
        String coordinates = indices.stream().limit(5).map(i -> "(" + i % width + "," + i / width + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        int first = indices.getFirst();
        // Put samples first so even an adversarial configuration name cannot truncate them away.
        return new MapFinding(code, severity, coordinates + ": " + detail, first % width, first / width, indices.size());
    }

    private static String plain(String text) { return text == null ? "" : MapFinding.safe(text); }
    private static MapFinding finding(String code, MapFinding.Severity severity, String detail) {
        return new MapFinding(code, severity, detail, null, null, null);
    }
}
