package org.xcore.plugin.service.map.inspect;

import java.util.List;
import java.util.Map;

/** Detached values in row-major (x + y * width) order; arrays are never exposed. */
public final class MapSnapshot {
    private final Metadata metadata;
    private final ContentRegistrySnapshot registry;
    private final int[] floors, overlays, blocks, teams;
    private final List<Core> cores;
    private final List<String> unknownContent;
    private final boolean tilesAvailable;

    public MapSnapshot(Metadata metadata, ContentRegistrySnapshot registry, int[] floors, int[] overlays,
                       int[] blocks, int[] teams, List<Core> cores, List<String> unknownContent, boolean tilesAvailable) {
        this.metadata = metadata;
        this.registry = registry;
        int expected = tilesAvailable ? Math.multiplyExact(metadata.width(), metadata.height()) : 0;
        if (floors.length != expected || overlays.length != expected || blocks.length != expected || teams.length != expected)
            throw new IllegalArgumentException("Snapshot dimensions disagree");
        this.floors = floors.clone();
        this.overlays = overlays.clone();
        this.blocks = blocks.clone();
        this.teams = teams.clone();
        this.cores = List.copyOf(cores);
        this.unknownContent = List.copyOf(unknownContent);
        this.tilesAvailable = tilesAvailable;
    }

    public Metadata metadata() { return metadata; }
    public ContentRegistrySnapshot registry() { return registry; }
    public boolean tilesAvailable() { return tilesAvailable; }
    public int tileCount() { return floors.length; }
    public int floor(int index) { return floors[index]; }
    public int overlay(int index) { return overlays[index]; }
    public int block(int index) { return blocks[index]; }
    public int team(int index) { return teams[index]; }
    public List<Core> cores() { return cores; }
    public List<String> unknownContent() { return unknownContent; }

    public Map<String, Integer> coresByTeam() {
        var result = new java.util.TreeMap<String, Integer>();
        for (Core core : cores) result.merge(registry.teamName(core.team()), 1, Integer::sum);
        return Map.copyOf(result);
    }

    public int spawnCount() {
        var spawn = registry.resolve(ContentRegistrySnapshot.BLOCK_TYPE, "spawn");
        if (spawn == null) return 0;
        int count = 0;
        for (int overlay : overlays) if (overlay == spawn.id()) count++;
        return count;
    }

    public record Core(int x, int y, int size, int team) {}
    public record Metadata(String name, String author, String description, int width, int height, int saveVersion,
                           Integer build, MapRules rules, List<String> mods, String sha256, int sizeBytes,
                           int dataPatches) {
        public Metadata { mods = List.copyOf(mods); }
    }
}
