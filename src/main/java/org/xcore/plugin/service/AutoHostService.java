package org.xcore.plugin.service;

import arc.Core;
import arc.Events;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.game.Gamemode;
import mindustry.game.Rules;
import mindustry.maps.MapException;
import mindustry.net.Administration;
import mindustry.server.ServerControl;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.TomlXcoreConfig;

@Singleton
public class AutoHostService {

    private final TomlXcoreConfig config;
    private boolean initialized = false;

    @Inject
    public AutoHostService(TomlXcoreConfig config) {
        this.config = config;
    }

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        Events.on(EventType.ServerLoadEvent.class, event -> onServerLoad());
    }

    public boolean onServerLoad() {
        if (!config.server.autoStart) {
            return false;
        }

        if (Vars.state.isGame()) {
            PLog.warn("Auto-start skipped: server is already hosting a game.");
            return false;
        }

        Gamemode preset = parseGamemode(config.server.autoStartGamemode);
        var result = Vars.maps.getNextMap(preset, Vars.state.map);

        if (result == null) {
            PLog.warn("Auto-start skipped: no map available to host for mode '@'.", preset.name());
            return false;
        }

        PLog.info("Auto-start: hosting map '@' in @ mode...", result.plainName(), preset.name());

        if (ServerControl.instance != null) {
            ServerControl.instance.cancelPlayTask();
            ServerControl.instance.lastMode = preset;
        }

        Vars.logic.reset();
        Core.settings.put("lastServerMode", preset.name());

        try {
            Rules rules = result.applyRules(preset);
            Vars.world.loadMap(result, rules);
            Vars.state.rules = rules;
            Events.fire(new EventType.RulesLoadEvent(rules));
            Vars.logic.play();
            Vars.netServer.openServer();

            if (!Vars.state.isGame() || (Vars.net != null && !Vars.net.server())) {
                PLog.err("Auto-start failed: server socket could not be opened on port @.", Administration.Config.port.num());
                cleanupFailedHost();
                return false;
            }

            PLog.info("Auto-start: map '@' loaded and server opened on port @.",
                    result.plainName(),
                    Administration.Config.port.num());
            return true;
        } catch (MapException e) {
            cleanupFailedHost();
            PLog.err("Auto-start failed to load map '@': @",
                    e.map != null ? e.map.plainName() : result.plainName(),
                    e.getMessage());
            return false;
        } catch (Throwable t) {
            cleanupFailedHost();
            PLog.err("Auto-start failed unexpectedly", t);
            return false;
        }
    }

    private void cleanupFailedHost() {
        if (Vars.state != null && Vars.state.isGame()) {
            Vars.state.set(GameState.State.menu);
        }
    }

    public static Gamemode parseGamemode(String name) {
        if (name == null || name.isBlank()) {
            return Gamemode.survival;
        }
        try {
            Gamemode mode = Gamemode.valueOf(name.trim().toLowerCase());
            if (mode.hidden) {
                PLog.warn("Gamemode '@' is hidden/not playable on server, falling back to survival", name);
                return Gamemode.survival;
            }
            return mode;
        } catch (IllegalArgumentException e) {
            PLog.warn("Unknown gamemode '@', falling back to survival", name);
            return Gamemode.survival;
        }
    }
}
