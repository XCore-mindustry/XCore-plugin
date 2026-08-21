package org.xcore.plugin.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-data DTO for the server-local {@code xcore.toml} configuration.
 * Mirrors the TOML section structure and preserves default values that match
 * the legacy {@link Config} runtime model for the first adapter slice.
 *
 * <p>Normalization repairs null nested objects and collections, converts blank
 * optional strings back to {@code null} where the legacy API expects it, and
 * enforces positive numeric defaults.</p>
 */
public class TomlXcoreConfig {

    public int version = 1;
    public ServerConfig server = new ServerConfig();
    public PathsConfig paths = new PathsConfig();
    public DiscordConfig discord = new DiscordConfig();
    public TransportConfig transport = new TransportConfig();
    public RuntimeConfig runtime = new RuntimeConfig();
    public EventHubConfig eventHub = new EventHubConfig();
    public TelemetryConfig telemetry = new TelemetryConfig();
    public TranslationConfig translation = new TranslationConfig();

    public void normalize() {
        if (server == null) {
            server = new ServerConfig();
        }
        if (paths == null) {
            paths = new PathsConfig();
        }
        if (discord == null) {
            discord = new DiscordConfig();
        }
        if (transport == null) {
            transport = new TransportConfig();
        }
        if (runtime == null) {
            runtime = new RuntimeConfig();
        }
        if (eventHub == null) {
            eventHub = new EventHubConfig();
        }
        if (telemetry == null) {
            telemetry = new TelemetryConfig();
        }
        if (translation == null) {
            translation = new TranslationConfig();
        }

        server.normalize();
        paths.normalize();
        discord.normalize();
        transport.normalize();
        runtime.normalize();
        telemetry.normalize();
        translation.normalize();
    }

    public static class ServerConfig {
        public String name = "server";
        public String publicHostOverride = "";
        public int playerLimit = 30;
        public boolean gameStartedTimer = true;

        public void normalize() {
            if (name == null || name.isBlank()) {
                name = "server";
            }
            if (publicHostOverride != null && publicHostOverride.isBlank()) {
                publicHostOverride = null;
            }
        }
    }

    public static class PathsConfig {
        public String globalConfigDirectory = "";

        public void normalize() {
            if (globalConfigDirectory != null && globalConfigDirectory.isBlank()) {
                globalConfigDirectory = null;
            }
        }
    }

    public static class DiscordConfig {
        public String channelId = "0";

        public void normalize() {
            if (channelId == null || channelId.isBlank()) {
                channelId = "0";
            } else {
                channelId = channelId.trim();
            }
        }

        public long channelIdAsLong() {
            normalize();
            for (int i = 0; i < channelId.length(); i++) {
                char ch = channelId.charAt(i);
                if (ch < '0' || ch > '9') {
                    throw new IllegalArgumentException("discord.channel_id must contain only decimal digits");
                }
            }
            try {
                return Long.parseLong(channelId);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("discord.channel_id must fit in signed 64-bit range", e);
            }
        }
    }

    public static class TransportConfig {
        public RedisConfig redis = new RedisConfig();

        public void normalize() {
            if (redis == null) {
                redis = new RedisConfig();
            }
            redis.normalize();
        }
    }

    public static class RedisConfig {
        public String url = "redis://127.0.0.1:6379";
        public String groupPrefix = "xcore:cg";
        public String consumerName = "xcore-node";
        public ReclaimConfig reclaim = new ReclaimConfig();
        public DlqConfig dlq = new DlqConfig();

        public void normalize() {
            if (url == null || url.isBlank()) {
                url = "redis://127.0.0.1:6379";
            }
            if (groupPrefix == null || groupPrefix.isBlank()) {
                groupPrefix = "xcore:cg";
            }
            if (consumerName == null || consumerName.isBlank()) {
                consumerName = "xcore-node";
            }
            if (reclaim == null) {
                reclaim = new ReclaimConfig();
            }
            if (dlq == null) {
                dlq = new DlqConfig();
            }
            reclaim.normalize();
            dlq.normalize();
        }
    }

    public static class ReclaimConfig {
        public boolean enabled = true;
        public long minIdleMs = 15000;
        public int batch = 50;

        public void normalize() {
            if (minIdleMs < 0) {
                minIdleMs = 15000;
            }
            if (batch <= 0) {
                batch = 50;
            }
        }
    }

    public static class DlqConfig {
        public boolean enabled = true;
        public int maxDeliveryAttempts = 3;
        public String prefix = "xcore:dlq";

        public void normalize() {
            if (maxDeliveryAttempts <= 0) {
                maxDeliveryAttempts = 3;
            }
            if (prefix == null || prefix.isBlank()) {
                prefix = "xcore:dlq";
            }
        }
    }

    public static class RuntimeConfig {
        public Set<String> disabledCommands = new HashSet<>();
        public Set<String> disabledFeatures = new HashSet<>();

        public void normalize() {
            if (disabledCommands == null) {
                disabledCommands = new HashSet<>();
            }
            if (disabledFeatures == null) {
                disabledFeatures = new HashSet<>();
            }
        }
    }

    public static class EventHubConfig {
        public boolean enabled = false;
        public String mapId = "";
    }

    public static class TelemetryConfig {
        public boolean enabled = false;
        public String nodeId = "";
        public int publishIntervalMs = 15000;
        public int sampleIntervalMs = 5000;
        public int ttlSeconds = 60;
        public int maxCompressedSnapshotBytes = 131072;
        public int maxUncompressedSnapshotBytes = 524288;

        public void normalize() {
            if (nodeId != null && nodeId.isBlank()) {
                nodeId = null;
            }
            if (publishIntervalMs <= 0) {
                publishIntervalMs = 15000;
            }
            if (sampleIntervalMs <= 0) {
                sampleIntervalMs = 5000;
            }
            if (ttlSeconds <= 0) {
                ttlSeconds = 60;
            }
            if (maxCompressedSnapshotBytes <= 0) {
                maxCompressedSnapshotBytes = 131072;
            }
            if (maxUncompressedSnapshotBytes <= 0) {
                maxUncompressedSnapshotBytes = 524288;
            }
        }
    }

    public static class TranslationConfig {
        public boolean enabled = true;
        public List<String> pipeline = new ArrayList<>(List.of("google"));
        public boolean preserveOriginalMessageOnFailure = true;
        public CacheConfig cache = new CacheConfig();
        public MetricsConfig metrics = new MetricsConfig();
        public LlmConfig llm = new LlmConfig();

        public void normalize() {
            if (pipeline == null || pipeline.isEmpty()) {
                pipeline = new ArrayList<>(List.of("google"));
            }
            if (cache == null) {
                cache = new CacheConfig();
            }
            if (metrics == null) {
                metrics = new MetricsConfig();
            }
            if (llm == null) {
                llm = new LlmConfig();
            }
            cache.normalize();
            metrics.normalize();
            llm.normalize();
        }
    }

    public static class CacheConfig {
        public boolean enabled = true;
        public int ttlSeconds = 1800;
        public int maxTextLength = 500;

        public void normalize() {
            if (ttlSeconds <= 0) {
                ttlSeconds = 1800;
            }
            if (maxTextLength <= 0) {
                maxTextLength = 500;
            }
        }
    }

    public static class MetricsConfig {
        public boolean enabled = true;
        public boolean minuteBucketsEnabled = true;
        public int minuteBucketTtlSeconds = 21600;

        public void normalize() {
            if (minuteBucketTtlSeconds <= 0) {
                minuteBucketTtlSeconds = 21600;
            }
        }
    }

    public static class LlmConfig {
        public boolean preserveFormattingTokens = true;
        public boolean structuredOutputRequired = true;
        public int maxInputChars = 500;
        public int maxOutputChars = 1200;
        public boolean stripControlCharacters = true;

        public void normalize() {
            if (maxInputChars <= 0) {
                maxInputChars = 500;
            }
            if (maxOutputChars <= 0) {
                maxOutputChars = 1200;
            }
        }
    }
}
