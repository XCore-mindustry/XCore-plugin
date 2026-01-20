package org.xcore.plugin.infra.commands.context;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import org.xcore.plugin.PluginVars;
import java.util.Map;
import java.util.Locale;

public record CommandContext<T>(T source, String[] args) {

    public Player player() {
        return (Player) source;
    }

    public Locale locale() {
        return PluginVars.bundle.locale(player());
    }

    public String arg(int index) {
        return args.length > index ? args[index] : null;
    }

    public int argInt(int index, int defaultValue) {
        String val = arg(index);
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val); } catch (Exception e) { return defaultValue; }
    }

    public void send(String key, Map<String, Object> argsMap) {
        if (source instanceof Player p) {
            PluginVars.bundle.send(p, key, argsMap);
        }
    }

    public void send(String key) {
        send(key, Map.of());
    }

    public void send(String key, Object... args) {
        if (source instanceof Player p) {
            PluginVars.bundle.send(p, key, Bundle.args(args));
        }
    }

    public String format(String key, Map<String, Object> argsMap) {
        return PluginVars.bundle.format(locale(), key, argsMap);
    }
}