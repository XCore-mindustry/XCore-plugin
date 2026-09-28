package org.xcore.testkit.fixtures;

import arc.func.Cons;
import mindustry.net.Host;
import mindustry.net.Net;
import mindustry.net.NetConnection;

import java.util.Collections;

/**
 * Headless Net implementation reporting {@code server() == true} and {@code active() == true}
 * to enable {@code Call.*} RPC dispatch without binding host sockets.
 */
public class MockNet extends Net {

    public MockNet() {
        super(new NetProvider() {
            @Override public void connectClient(String ip, int port, Runnable success) {}
            @Override public void sendClient(Object object, boolean reliable) {}
            @Override public void disconnectClient() {}
            @Override public void discoverServers(Cons<Host> callback, Runnable done) {}
            @Override public void pingHost(String address, int port, Cons<Host> valid, Cons<Exception> failed) {}
            @Override public void hostServer(int port) {}
            @Override public Iterable<? extends NetConnection> getConnections() { return Collections.emptyList(); }
            @Override public void closeServer() {}
        });
    }

    @Override
    public boolean server() {
        return true;
    }

    @Override
    public boolean active() {
        return true;
    }

    @Override
    public boolean client() {
        return false;
    }
}
