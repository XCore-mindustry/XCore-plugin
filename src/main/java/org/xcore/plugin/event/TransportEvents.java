package org.xcore.plugin.event;

public class TransportEvents {
    public interface Event {}

    public interface ServerScopedEvent {
        String server();
    }

}
