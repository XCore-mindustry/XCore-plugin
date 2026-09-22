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
        if (loadAction == null) return;
        WorldReloader reloader = new WorldReloader();
        try {
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
        loadMap(map, resolveCurrentGamemode());
    }

    public void loadMap(Map map, Gamemode mode) {
        if (map == null) {
            Log.err("Cannot load null map");
            return;
        }
        Gamemode resolvedMode = mode != null ? mode : resolveCurrentGamemode();
        reloadWorld(() -> Vars.world.loadMap(map, map.applyRules(resolvedMode)));
    }

    private Gamemode resolveCurrentGamemode() {
        String modeName = Core.settings != null ? Core.settings.getString("lastServerMode", "survival") : "survival";
        if (modeName == null || modeName.isBlank()) {
            return Gamemode.survival;
        }
        try {
            return Gamemode.valueOf(modeName.trim().toLowerCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
            return Gamemode.survival;
        }
    }
}