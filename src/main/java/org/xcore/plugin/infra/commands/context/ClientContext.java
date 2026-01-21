package org.xcore.plugin.infra.commands.context;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import org.xcore.plugin.PluginVars;

import java.util.Locale;
import java.util.Map;

public final class ClientContext extends CommandContext {
    private final Player player;

    public ClientContext(Player player, String[] args) {
        super(args);
        this.player = player;
    }

    public Player player() {
        return player;
    }

    public Locale locale() {
        return PluginVars.bundle.locale(player);
    }

    public void send(String key, Map<String, Object> argsMap) {
        PluginVars.bundle.send(player, key, argsMap);
    }

    public void send(String key) {
        send(key, Map.of());
    }

    public void send(String key, Object... args) {
        PluginVars.bundle.send(player, key, Bundle.args(args));
    }


    public String format(String key, Map<String, Object> argsMap) {
        return PluginVars.bundle.format(locale(), key, argsMap);
    }

    public String format(String key, Object... args) {
        return PluginVars.bundle.format(locale(), key, Bundle.args(args));
    }
}