package org.xcore.plugin.utils;

import arc.func.Cons;
import arc.util.Log;
import arc.util.Timer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.ospx.sock.ClientSock;
import com.ospx.sock.ServerSock;
import com.ospx.sock.Sock;
import org.xcore.plugin.modules.Config;

import java.util.Date;

import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.globalConfig;

public class SockCommunicator {
    public static Sock sock;

    public static void init() {
        switch (config.sockType) {
            case CLIENT -> sock = new ClientSock(globalConfig.sockServerPort);
            case SERVER -> sock = new ServerSock(globalConfig.sockServerPort);
        }

        sock.getPacketSerializer().getKryo().register(Date.class, new Serializer<Date>() {
            @Override
            public void write(Kryo kryo, Output output, Date object) {
                output.writeLong(object.getTime());
            }

            @Override
            public Date read(Kryo kryo, Input input, Class type) {
                return new Date(input.readLong());
            }
        });

        safeConnect();

        if (sock.isClient())
            Timer.schedule(() -> {
                if (!sock.isConnected()) {
                    Log.info("Trying reconnect to Sock server");
                    safeConnect();
                }
            }, 0, 180);
    }

    public static void safeConnect() {
        try {
            sock.connect();
        } catch (Exception e) {
            Log.err("Exception occurred while connecting to Sock server", e);
        }
    }

    public static void sendEvent(Object event) {
        if (!sock.isConnected()) return;

        sock.sendEvent(event);
    }

    public static <T> void onEvent(Class<T> type, Cons<T> consumer) {
        sock.onEvent(type, consumer);
    }

    public static boolean isSocketServer() {
        return config.sockType == Config.SockType.SERVER;
    }
}
