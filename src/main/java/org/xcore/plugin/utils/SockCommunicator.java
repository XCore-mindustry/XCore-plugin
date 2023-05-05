package org.xcore.plugin.utils;

import arc.func.Cons;
import com.ospx.sock.ClientSock;
import com.ospx.sock.ServerSock;
import com.ospx.sock.Sock;
import org.xcore.plugin.modules.Config;

import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.globalConfig;

public class SockCommunicator {
    public static Sock sock;

    public static void init() {
        switch (config.sockType) {
            case CLIENT -> sock = new ClientSock(globalConfig.sockServerPort);
            case SERVER -> sock = new ServerSock(globalConfig.sockServerPort);
        }

        sock.connect();
    }

    public static void sendEvent(Object event) {
        sock.sendEvent(event);
    }

    public static <T> void onEvent(Class<T> type, Cons<T> consumer) {
        sock.onEvent(type, consumer);
    }

    public static boolean isSocketServer() {
        return config.sockType == Config.SockType.SERVER;
    }
}
