package org.xcore.plugin.modules;

import arc.Events;
import arc.struct.ObjectMap;
import arc.util.Structs;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.XcorePlugin;

import static mindustry.Vars.world;
import static org.xcore.plugin.PluginVars.config;

public class MiniHexed {
    private static final ObjectMap<String, Team> teams = new ObjectMap<>();
    private static Schematic startBase;
    public static void init() {
        if (!config.isMiniHexed()) return;

        startBase = Schematics.readBase64("bXNjaAF4nDWQ3W6DMAxGv/wQUpDWV+gLcLPXmXaRQap2YhgFurYvv82ONSLlJLGPbYEWvYNf0lfGy0glny75cdr2VHb0U97Gcl33Ky0Awpw+8rzBvr336Eda11yGe5pndCvd+bzQlBFHWr7zkwqOZypjHtZCn3nc+cFNN0K/0ZzKsKYlsygdh+2SyoR4W2ZKUy7o07UM5yTOE8d72rl2fuylvsBPxDvwivpZ2QyvejZCFy387w+/NUbCXrMaRVCvVSUqDopOICfrOJcXV1TdqG5E94wWrmGwLjio1/0PZAMcC6blG2d6RhTBaqbVTCeZkctFA23rNOAlcKh9uIQXs8a9huVmPcPBWYaXORteFUEmaDQzaJfAcoVVVC+oF9QL6gX5Lx0jdppa5w1S7Q8n5z8n");

        Events.on(EventType.PlayerJoin.class, event -> initPlayer(event.player));
        Events.on(EventType.GameOverEvent.class, event -> teams.clear());
        Events.on(EventType.WorldLoadEvent.class, event -> Groups.player.each(MiniHexed::initPlayer));

        XcorePlugin.info("MiniHexed loaded.");
    }

    private static void notAvailableTeamMessage(Player player) {
        player.sendMessage("All cores are busy. You are an observer of the game.");
    }

    private static void initPlayer(Player player) {
        var playerTeam = teams.get(player.uuid());

        if (playerTeam != null && playerTeam.active()) {
            player.team(playerTeam);
            return;
        }

        var core = Team.green.core();
        var team = Structs.find(Team.all, t -> t.id > 5 && !t.active());

        if (team == null || core == null) {
            notAvailableTeamMessage(player);
            return;
        }

        teams.put(player.uuid(), team);

        var tile = core.tile;

        tile.setNet(Blocks.coreShard, team, 0);

        int x = tile.x - startBase.width / 2, y = tile.y - startBase.height / 2;

        startBase.tiles.each(st -> {
            var _tile = world.tile(st.x + x, st.y + y);
            if (_tile == null) return;

            _tile.setNet(st.block, team, st.rotation);
            _tile.build.configureAny(st.config);
        });

        player.team(team);
    }
}
