package org.xcore.plugin.service.map.inspect;

import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.io.SaveFileReader;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OverlayFloor;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.concurrent.GameThread;

import java.util.HashMap;
import java.util.Map;

/** Value copies only: no worker holds a ContentLoader, Block, Rules, Team, or Tile. */
public record ContentRegistrySnapshot(Map<Integer, Map<String, Entry>> types, Map<Integer, Entry> blocks,
                                      Map<String, String> aliases, Map<Integer, String> teams, MapRules defaults) {
    public static final int BLOCK_TYPE = ContentType.block.ordinal();

    public ContentRegistrySnapshot {
        var copy = new HashMap<Integer, Map<String, Entry>>();
        types.forEach((id, entries) -> copy.put(id, Map.copyOf(entries)));
        types = Map.copyOf(copy);
        blocks = Map.copyOf(blocks);
        aliases = Map.copyOf(aliases);
        teams = Map.copyOf(teams);
    }

    public static ContentRegistrySnapshot capture() {
        GameThread.require("map inspection registry capture");
        if (Vars.content == null) throw new IllegalStateException("Content registry unavailable");
        var types = new HashMap<Integer, Map<String, Entry>>();
        var blocks = new HashMap<Integer, Entry>();
        // Raw registered content bypasses temporaryMapper, including while an existing preview runs.
        var registered = Vars.content.getContentMap();
        for (int type = 0; type < registered.length; type++) {
            var names = new HashMap<String, Entry>();
            for (int i = 0; i < registered[type].size; i++) {
                if (!(registered[type].get(i) instanceof MappableContent value)) continue;
                Block block = value instanceof Block b ? b : null;
                var entry = new Entry(value.id, value.name, block instanceof Floor,
                        block instanceof OverlayFloor, block != null && block.hasBuilding(),
                        block instanceof CoreBlock, block == null ? 1 : block.size,
                        block == null ? 0 : (block.hasItems ? 1 : 0) | (block.hasPower ? 2 : 0) | (block.hasLiquids ? 4 : 0));
                names.put(entry.name(), entry);
                if (block != null) blocks.put(entry.id(), entry);
            }
            types.put(type, names);
        }
        var aliases = new HashMap<String, String>();
        for (var alias : SaveFileReader.fallback) aliases.put(alias.key, alias.value);
        for (var alias : SaveFileReader.modContentNameMap) aliases.putIfAbsent(alias.key, alias.value);
        var teams = new HashMap<Integer, String>();
        for (Team team : Team.all) teams.put(team.id, team.name);
        Rules rules = new Rules();
        return new ContentRegistrySnapshot(types, blocks, aliases, teams,
                new MapRules(rules.mode().name(), rules.defaultTeam.id, rules.waveTeam.id, rules.waves,
                        rules.waveTimer, rules.spawns.size, rules.dropZoneRadius));
    }

    public Entry resolve(int type, String name) {
        if (type == BLOCK_TYPE) name = aliases.getOrDefault(name, name);
        return types.getOrDefault(type, Map.of()).get(name);
    }

    public String teamName(int id) { return teams.getOrDefault(id, "team#" + id); }

    /** Module presence for pre-bitmask base formats, copied from Block fields without constructing a Building. */
    public record Entry(int id, String name, boolean floor, boolean overlay, boolean building, boolean core, int size,
                        int modules) {
        public Entry(int id, String name, boolean floor, boolean overlay, boolean building, boolean core, int size) {
            this(id, name, floor, overlay, building, core, size, 0);
        }
    }
}
