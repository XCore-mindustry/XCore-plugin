package org.xcore.plugin.service.network;

@FunctionalInterface
public interface RedisServerResolver {
    String resolveServer(Object payload, String defaultServer);

    static RedisServerResolver broadcast() {
        return (payload, defaultServer) -> defaultServer;
    }

    static RedisServerResolver defaultServer() {
        return (payload, defaultServer) -> defaultServer;
    }
}
