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
        reloadWorld(loadAction, null);
    }

    public void reloadWorld(Runnable loadAction, Runnable afterLoadAction) {
        try {
            WorldReloader reloader = new WorldReloader();
            reloader.begin();

            loadAction.run();

            Vars.logic.play();

            if (afterLoadAction != null) {
                afterLoadAction.run();
            }

            reloader.end();
        } catch (MapException e) {
            Log.err("Error loading map @: @", e.map == null ? "unknown" : e.map.name(), e.getMessage());
        } catch (Throwable t) {
            Log.err("Unexpected error during world reload", t);
        }
    }

    public void loadMap(Map map) {
        loadMap(map, Gamemode.valueOf(Core.settings.getString("lastServerMode", "survival")));
    }

    public void loadMap(Map map, Gamemode mode) {
        reloadWorld(() -> {
            Vars.world.loadMap(map, map.applyRules(mode));
        });
    }
}