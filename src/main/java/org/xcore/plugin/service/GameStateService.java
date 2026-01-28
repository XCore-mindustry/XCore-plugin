package org.xcore.plugin.service;

import arc.Core;
import arc.util.Log;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.net.WorldReloader;

@Singleton
public class GameStateService {
    public void reloadWorld(Runnable loadAction) {
        try {
            var reloader = new WorldReloader();
            reloader.begin();

            loadAction.run();

            Vars.state.rules = Vars.state.map.applyRules(
                    Gamemode.valueOf(Core.settings.getString("lastServerMode"))
            );
            Vars.logic.play();
            reloader.end();
        } catch (MapException e) {
            Log.err("@: @", e.map.name(), e.getMessage());
        }
    }

    public void loadMap(Map map) {
        reloadWorld(() -> {
            Gamemode mode = Gamemode.valueOf(Core.settings.getString("lastServerMode"));
            Vars.world.loadMap(map, map.applyRules(mode));
        });
    }
}
