package org.xcore.plugin.modules;

import arc.Events;
import arc.struct.ObjectMap;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.*;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.MapException;
import mindustry.net.WorldReloader;
import org.xcore.plugin.XcorePlugin;

import static mindustry.Vars.world;
import static org.xcore.plugin.PluginVars.config;

public class MiniHexed {
    private static final ObjectMap<String, Team> teams = new ObjectMap<>();
    private static int winScore = 3600;
    private static Schematic startBase;
    public static void init() {
        if (!config.isMiniHexed()) return;

        Vars.state.rules.canGameOver = false;

        startBase = Schematics.readBase64("bXNjaAF4nDWQ3W6DMAxGv/wQUpDWV+gLcLPXmXaRQap2YhgFurYvv82ONSLlJLGPbYEWvYNf0lfGy0glny75cdr2VHb0U97Gcl33Ky0Awpw+8rzBvr336Eda11yGe5pndCvd+bzQlBFHWr7zkwqOZypjHtZCn3nc+cFNN0K/0ZzKsKYlsygdh+2SyoR4W2ZKUy7o07UM5yTOE8d72rl2fuylvsBPxDvwivpZ2QyvejZCFy387w+/NUbCXrMaRVCvVSUqDopOICfrOJcXV1TdqG5E94wWrmGwLjio1/0PZAMcC6blG2d6RhTBaqbVTCeZkctFA23rNOAlcKh9uIQXs8a9huVmPcPBWYaXORteFUEmaDQzaJfAcoVVVC+oF9QL6gX5Lx0jdppa5w1S7Q8n5z8n");

        Events.on(EventType.PlayerJoin.class, event -> initPlayer(event.player));
        Events.on(EventType.GameOverEvent.class, event -> teams.clear());

        Timer.schedule(() -> {
            if (!Vars.state.isPaused()) {
                winScore -= 1;
            }
            int sec = winScore % 60;
            int min = (winScore / 60) % 60;

            Groups.player.each(p -> Call.infoPopup(p.con(), Strings.format("[blue]@:@[] until endgame", min, sec),
                    1, Align.bottom, 0, 0, 0, 0));

            if (winScore < 1) {
                winScore = 36000;

                var winnerTeam = Vars.state.teams.getActive().filter(t -> !t.players.isEmpty()).max(t -> t.cores.size);
                var player = winnerTeam.players.first();

                Groups.player.each(p -> Call.infoMessage(Strings.format("@[] won! He had @ hexes.", player.coloredName(), winnerTeam.cores.size)));
                reloadMap();
            }
        }, 0f, 1);

        XcorePlugin.info("MiniHexed loaded.");
    }
    private static void reloadMap() {
        try {
            var map = Vars.maps.getNextMap(Gamemode.pvp, Vars.state.map);
            var reloader = new WorldReloader();
            reloader.begin();

            world.loadMap(map, map.applyRules(Vars.state.rules.mode()));
            Vars.state.rules = Vars.state.map.applyRules(Vars.state.rules.mode());
            Vars.logic.play();
            Groups.player.each(MiniHexed::initPlayer);
            reloader.end();

        } catch (MapException e) {
            Log.err("@: @", e.map.name(), e.getMessage());
        }
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

        var core = Team.green.cores().random();
        var team = Structs.find(Team.all, t -> t.id > 5 && !t.active());

        if (team == null || core == null) {
            notAvailableTeamMessage(player);
            player.team(Team.sharded);
            return;
        }

        teams.put(player.uuid(), team);

        core.tile.setNet(Blocks.coreShard, team, 0);

        int x = core.tileX() - startBase.width / 2, y = core.tileY() - startBase.height / 2;

        startBase.tiles.each(st -> {
            var tile = world.tile(st.x + x, st.y + y);
            if (tile == null) return;

            tile.setNet(st.block, team, st.rotation);
            tile.build.configureAny(st.config);
        });

        player.team(team);
    }
}
