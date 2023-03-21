package org.xcore.plugin.utils;

import fr.xpdustry.javelin.JavelinConfig;
import fr.xpdustry.javelin.JavelinEvent;
import fr.xpdustry.javelin.JavelinPlugin;
import fr.xpdustry.javelin.JavelinSocket;

public class JavelinCommunicator {
    public static <E extends JavelinEvent> void sendEvent(E event) {
        if (JavelinPlugin.getJavelinSocket().getStatus() == JavelinSocket.Status.OPEN) {
            JavelinPlugin.getJavelinSocket().sendEvent(event);
        }
    }

    public static boolean isSocketServer() {
        return JavelinPlugin.getJavelinConfig().getMode() == JavelinConfig.Mode.SERVER;
    }
}
