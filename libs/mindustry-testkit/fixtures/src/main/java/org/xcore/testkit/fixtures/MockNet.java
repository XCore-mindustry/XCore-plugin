package org.xcore.testkit.fixtures;

import arc.func.Cons;
import mindustry.net.Host;
import mindustry.net.Net;
import mindustry.net.NetConnection;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Headless Net implementation reporting {@code server() == true} and {@code active() == true}
 * to enable {@code Call.*} RPC dispatch without binding host sockets.
 *
 * <p>{@link HeadlessWorld} registers every {@link MockPlayer} it creates, so broadcast
 * {@code Call.*} overloads ({@code Call.announce(String)}, {@code Call.infoPopup(String, ...)},
 * {@code Call.warningToast(...)}, ...) reach the simulated players exactly as they would on a
 * live server. Connection-targeted overloads bypass this path and go straight to the
 * {@link MockNetConnection}, matching Mindustry's own dispatch.
 */
public class MockNet extends Net {

    private final CopyOnWriteArrayList<NetConnection> connections;

    /**
     * {@code Net.send(Object, boolean)} reads the private {@code server} field directly
     * rather than calling {@link #server()}, so overriding the accessor is not enough for
     * broadcast {@code Call.*} overloads to be routed. The fields are resolved once here
     * instead of in every consuming test.
     */
    private static final Field SERVER_FIELD = netField("server");
    private static final Field ACTIVE_FIELD = netField("active");

    private static Field netField(String name) {
        try {
            Field field = Net.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(
                    new IllegalStateException("Mindustry Net has no field '" + name + "'", e));
        }
    }

    public MockNet() {
        this(new CopyOnWriteArrayList<>());
    }

    /**
     * The connection registry must exist before {@code super(...)} because the anonymous
     * {@link NetProvider} closes over it; passing it in as a constructor parameter keeps
     * that reference legal (a field reference in a {@code super()} argument is not).
     */
    private MockNet(CopyOnWriteArrayList<NetConnection> connections) {
        super(new NetProvider() {
            @Override public void connectClient(String ip, int port, Runnable success) {}
            @Override public void sendClient(Object object, boolean reliable) {}
            @Override public void disconnectClient() {}
            @Override public void discoverServers(Cons<Host> callback, Runnable done) {}
            @Override public void pingHost(String address, int port, Cons<Host> valid, Cons<Exception> failed) {}
            @Override public void hostServer(int port) {}
            @Override public Iterable<? extends NetConnection> getConnections() { return connections; }
            @Override public void sendAllServer(Object object, boolean reliable) {
                for (NetConnection connection : connections) {
                    connection.send(object, reliable);
                }
            }
            @Override public void closeServer() { connections.clear(); }
        });
        this.connections = connections;
        try {
            SERVER_FIELD.setBoolean(this, true);
            ACTIVE_FIELD.setBoolean(this, true);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not put Net into server mode", e);
        }
    }

    /**
     * Registers a connection to receive broadcast packets.
     *
     * <p>Idempotent: {@link #addPlayer} registers each connection it creates, and a test that
     * also calls this directly must not cause {@code sendAllServer} to deliver twice.
     */
    public void register(NetConnection connection) {
        if (connection != null) {
            connections.addIfAbsent(connection);
        }
    }
    /** Stops delivering broadcast packets to a connection. */
    public void unregister(NetConnection connection) {
        connections.remove(connection);
    }

    /** Connections currently receiving broadcast packets, in registration order. */
    public List<NetConnection> connections() {
        return List.copyOf(connections);
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
