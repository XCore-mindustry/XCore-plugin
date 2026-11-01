package org.xcore.plugin.service.network;

import java.lang.reflect.Method;

@FunctionalInterface
public interface RedisServerResolver {
    String resolveServer(Object payload, String defaultServer);

    static RedisServerResolver broadcast() {
        return (payload, defaultServer) -> defaultServer;
    }

    static RedisServerResolver defaultServer() {
        return (payload, defaultServer) -> defaultServer;
    }

    static RedisServerResolver payloadField(String fieldName) {
        return (payload, defaultServer) -> {
            if (payload == null || fieldName == null || fieldName.isBlank()) {
                return defaultServer;
            }
            try {
                Method accessor = payload.getClass().getMethod(fieldName);
                Object value = accessor.invoke(payload);
                if (value instanceof String stringValue && !stringValue.isBlank()) {
                    return stringValue;
                }
            } catch (ReflectiveOperationException ignored) {
                // Leave fallback handling to the default server when a payload does not expose the binding field.
            }
            return defaultServer;
        };
    }
}
