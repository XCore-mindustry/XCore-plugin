package org.xcore.plugin.utils;

import arc.func.Cons;
import arc.util.Log;
import com.ospx.sock.ClientSock;
import com.ospx.sock.EventBus.Request;
import com.ospx.sock.EventBus.RequestSubscription;
import com.ospx.sock.EventBus.Response;
import com.ospx.sock.EventBus.Subscription;
import com.ospx.sock.ServerSock;
import com.ospx.sock.Sock;

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
    }

    public static void safeConnect() {
        try {
            sock.connect();
        } catch (Exception e) {
            Log.err("Exception occurred while connecting to Sock server", e);
        }
    }

    public static void post(Object event) {
        sock.send(event);
    }

    public static <T> Subscription<T> subscribe(Class<T> type, Cons<T> consumer) {
        return sock.on(type, consumer);
    }

    public static <T extends Response> RequestSubscription<T> request(Request<T> request, Cons<T> listener, Runnable timeout) {
        return sock.request(request, listener);
    }

    public static <T extends Response> void respond(Request<T> request, T response) {
        sock.respond(request, response);
    }

    public static boolean isSocketServer() {
        return sock.isServer();
    }
}