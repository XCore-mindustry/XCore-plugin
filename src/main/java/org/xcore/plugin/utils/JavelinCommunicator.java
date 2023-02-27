package org.xcore.plugin.utils;

import arc.func.Cons;
import fr.xpdustry.javelin.JavelinConfig;
import fr.xpdustry.javelin.JavelinEvent;
import fr.xpdustry.javelin.JavelinPlugin;

import static org.xcore.plugin.PluginVars.isSocketServer;

public class JavelinCommunicator {
    public static void init() {
        isSocketServer = JavelinPlugin.getJavelinConfig().getMode() == JavelinConfig.Mode.SERVER;
    }

    public static <E extends JavelinEvent> void sendEvent(E event, Cons<E> callback) {
        if (isSocketServer) {
            callback.get(event);
        } else {
            JavelinPlugin.getJavelinSocket().sendEvent(event);
        }
    }
}
