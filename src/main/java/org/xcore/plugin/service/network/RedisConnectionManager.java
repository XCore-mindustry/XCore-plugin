package org.xcore.plugin.service.network;

import org.xcore.plugin.common.PLog;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.net.URI;

@Singleton
public final class RedisConnectionManager {
    private final TomlXcoreConfig config;
    private final RedisTransportHealth transportHealth;

    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> commands;
    private StatefulRedisConnection<String, byte[]> binaryConnection;
    private RedisCommands<String, byte[]> binaryCommands;
    private boolean connectionWarningLogged;

    public RedisConnectionManager(TomlXcoreConfig config, RedisTransportHealth transportHealth) {
        this.config = config;
        this.transportHealth = transportHealth;
    }

    public synchronized void connect() {
        if (commands != null && binaryCommands != null) {
            return;
        }

        transportHealth.markConnecting();
        try {
            client = RedisClient.create(config.transport.redis.url);
            connection = client.connect();
            commands = connection.sync();
            binaryConnection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
            binaryCommands = binaryConnection.sync();
            connectionWarningLogged = false;
            transportHealth.markConnected();
            PLog.info("Redis connected: url=@", sanitizeRedisUrl(config.transport.redis.url));
        } catch (RuntimeException e) {
            closeResources();
            transportHealth.markUnavailable();
            throw e;
        }
    }

    public synchronized void disconnect() {
        closeResources();
        transportHealth.markDisconnected();
    }

    public synchronized boolean ensureConnected() {
        if (commands != null && binaryCommands != null) {
            return true;
        }

        try {
            connect();
            return commands != null;
        } catch (Exception e) {
            transportHealth.markUnavailable();
            if (!connectionWarningLogged) {
                connectionWarningLogged = true;
                PLog.warn("Redis backend unavailable, continuing without publish: @", e.getMessage());
            }
            return false;
        }
    }

    public synchronized RedisClient client() {
        return client;
    }

    public synchronized RedisCommands<String, String> commands() {
        return commands;
    }

    public synchronized RedisCommands<String, byte[]> binaryCommands() {
        return binaryCommands;
    }

    static String sanitizeRedisUrl(String redisUrl) {
        if (redisUrl == null || redisUrl.isBlank()) {
            return "(empty)";
        }

        try {
            URI uri = URI.create(redisUrl);
            if (uri.getHost() != null) {
                StringBuilder sanitized = new StringBuilder();
                if (uri.getScheme() != null && !uri.getScheme().isBlank()) {
                    sanitized.append(uri.getScheme()).append("://");
                }
                sanitized.append(uri.getHost());
                if (uri.getPort() >= 0) {
                    sanitized.append(':').append(uri.getPort());
                }
                if (uri.getPath() != null && !uri.getPath().isBlank()) {
                    sanitized.append(uri.getPath());
                }
                if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                    sanitized.append('?').append(uri.getQuery());
                }
                if (uri.getFragment() != null && !uri.getFragment().isBlank()) {
                    sanitized.append('#').append(uri.getFragment());
                }
                return sanitized.toString();
            }
        } catch (Exception ignored) {
        }

        int schemeIndex = redisUrl.indexOf("://");
        int atIndex = redisUrl.lastIndexOf('@');
        if (schemeIndex >= 0 && atIndex > schemeIndex + 3) {
            return redisUrl.substring(0, schemeIndex + 3) + redisUrl.substring(atIndex + 1);
        }

        return redisUrl;
    }

    private void closeResources() {
        commands = null;
        binaryCommands = null;

        if (binaryConnection != null) {
            binaryConnection.close();
            binaryConnection = null;
        }

        if (connection != null) {
            connection.close();
            connection = null;
        }
        if (client != null) {
            client.shutdown();
            client = null;
        }
    }
}
