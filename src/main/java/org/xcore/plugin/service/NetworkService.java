package org.xcore.plugin.service;

import arc.func.Cons;
import arc.util.Log;
import com.ospx.sock.ClientSock;
import com.ospx.sock.EventBus.Request;
import com.ospx.sock.EventBus.RequestSubscription;
import com.ospx.sock.EventBus.Response;
import com.ospx.sock.EventBus.Subscription;
import com.ospx.sock.ServerSock;
import com.ospx.sock.Sock;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;

@Singleton
public class NetworkService {

    private final Config config;
    private final GlobalConfig globalConfig;
    @Getter
    private Sock sock;

    @Inject
    public NetworkService(Config config, GlobalConfig globalConfig) {
        this.config = config;
        this.globalConfig = globalConfig;
    }

    @PostConstruct
    public void init() {
        switch (config.sockType) {
            case CLIENT -> sock = new ClientSock(globalConfig.sockServerPort);
            case SERVER -> sock = new ServerSock(globalConfig.sockServerPort);
        }
        safeConnect();
    }

    public void safeConnect() {
        try {
            sock.connect();
        } catch (Exception e) {
            Log.err("Exception occurred while connecting to Sock server", e);
        }
    }

    public void disconnect() {
        sock.disconnect();
    }

    public void post(Object event) {
        sock.send(event);
    }

    public <T> Subscription<T> subscribe(Class<T> type, Cons<T> listener) {
        return sock.on(type, listener);
    }

    public <T extends Response> RequestSubscription<T> request(Request<T> request, Cons<T> listener, Runnable timeout) {
        return sock.request(request, listener, timeout);
    }

    public <T extends Response> void respond(Request<T> request, T response) {
        sock.respond(request, response);
    }

    public boolean isSocketServer() {
        return sock.isServer();
    }

    public String findServer(String query) {
        for (String server : globalConfig.servers.keySet()) {
            if (server.equals(query)) return query;
            if (server.startsWith(query) || server.contains(query)) return server;
        }
        return null;
    }
}
