package org.xcore.plugin.utils;

import arc.func.Cons;
import arc.util.Log;
import arc.util.Timer;
import com.ospx.sock.ClientSock;
import com.ospx.sock.ServerSock;
import com.ospx.sock.Sock;
import com.ospx.sock.EventBus.Request;
import com.ospx.sock.EventBus.RequestSubscription;
import com.ospx.sock.EventBus.Response;
import com.ospx.sock.EventBus.Subscription;

import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.globalConfig;

public class NetSock {
    public static Sock sock;

    public static void init() {
        switch (config.sockType) {
            case CLIENT -> sock = new ClientSock(globalConfig.sockServerPort);
            case SERVER -> sock = new ServerSock(globalConfig.sockServerPort);
        }

        safeConnect();

        if (sock.isClient())
            Timer.schedule(() -> {
                if (!sock.isConnected()) {
                    Log.info("Trying reconnect to Sock server");
                    safeConnect();
                }
            }, 0, 120);
    }

    public static void safeConnect() {
        try {
            sock.connect();
        } catch (Exception e) {
            Log.err("Exception occurred while connecting to Sock server", e);
        }
    }

    public static void post(Object event) {
        if (!sock.isConnected()) return;
        sock.getBus().fire(event);
    }

    public static <T> Subscription<T> subscribe(Class<T> type, Cons<T> consumer) {
        return sock.bus.on(type, consumer);
    }

    public static <T extends Response> RequestSubscription<T> request(Request<T> request, Cons<T> listener) {
        if (!sock.isConnected()) return null;
        return sock.bus.request(request, listener);
    }

    public static boolean isSocketServer() {
        return sock.isServer();
    }
}
