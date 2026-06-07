package org.xcore.plugin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TomlXcoreConfigTest {

    @Test
    @DisplayName("fresh instance has defaults matching legacy Config")
    void freshInstance_hasDefaultsMatchingLegacyConfig() {
        TomlXcoreConfig toml = new TomlXcoreConfig();

        assertThat(toml.version).isEqualTo(1);
        assertThat(toml.server.name).isEqualTo("server");
        assertThat(toml.server.publicHostOverride).isEqualTo("");
        assertThat(toml.server.playerLimit).isEqualTo(30);
        assertThat(toml.server.consoleEnabled).isTrue();
        assertThat(toml.server.gameStartedTimer).isTrue();

        assertThat(toml.paths.globalConfigDirectory).isEqualTo("");

        assertThat(toml.discord.channelId).isEqualTo("0");

        assertThat(toml.transport.redis.url).isEqualTo("redis://127.0.0.1:6379");
        assertThat(toml.transport.redis.groupPrefix).isEqualTo("xcore:cg");
        assertThat(toml.transport.redis.consumerName).isEqualTo("xcore-node");
        assertThat(toml.transport.redis.reclaim.enabled).isTrue();
        assertThat(toml.transport.redis.reclaim.minIdleMs).isEqualTo(15000L);
        assertThat(toml.transport.redis.reclaim.batch).isEqualTo(50);
        assertThat(toml.transport.redis.dlq.enabled).isTrue();
        assertThat(toml.transport.redis.dlq.maxDeliveryAttempts).isEqualTo(3);
        assertThat(toml.transport.redis.dlq.prefix).isEqualTo("xcore:dlq");

        assertThat(toml.runtime.disabledCommands).isNotNull().isEmpty();
        assertThat(toml.runtime.disabledFeatures).isNotNull().isEmpty();

        assertThat(toml.eventHub.enabled).isFalse();
        assertThat(toml.eventHub.mapId).isEqualTo("");

        assertThat(toml.telemetry.enabled).isFalse();
        assertThat(toml.telemetry.nodeId).isEqualTo("");
        assertThat(toml.telemetry.publishIntervalMs).isEqualTo(15000);
        assertThat(toml.telemetry.sampleIntervalMs).isEqualTo(5000);
        assertThat(toml.telemetry.ttlSeconds).isEqualTo(60);
        assertThat(toml.telemetry.maxCompressedSnapshotBytes).isEqualTo(131072);
        assertThat(toml.telemetry.maxUncompressedSnapshotBytes).isEqualTo(524288);

        assertThat(toml.translation.enabled).isTrue();
        assertThat(toml.translation.pipeline).containsExactly("google");
        assertThat(toml.translation.preserveOriginalMessageOnFailure).isTrue();
        assertThat(toml.translation.cache.enabled).isTrue();
        assertThat(toml.translation.cache.ttlSeconds).isEqualTo(1800);
        assertThat(toml.translation.cache.maxTextLength).isEqualTo(500);
        assertThat(toml.translation.metrics.enabled).isTrue();
        assertThat(toml.translation.metrics.minuteBucketsEnabled).isTrue();
        assertThat(toml.translation.metrics.minuteBucketTtlSeconds).isEqualTo(21600);
        assertThat(toml.translation.llm.preserveFormattingTokens).isTrue();
        assertThat(toml.translation.llm.structuredOutputRequired).isTrue();
        assertThat(toml.translation.llm.maxInputChars).isEqualTo(500);
        assertThat(toml.translation.llm.maxOutputChars).isEqualTo(1200);
        assertThat(toml.translation.llm.stripControlCharacters).isTrue();

        assertThat(toml.ipReputation.enabled).isFalse();
        assertThat(toml.ipReputation.blockProxy).isTrue();
        assertThat(toml.ipReputation.blockVpn).isTrue();
        assertThat(toml.ipReputation.blockTor).isTrue();
        assertThat(toml.ipReputation.blockHosting).isFalse();
        assertThat(toml.ipReputation.cacheTtlSeconds).isEqualTo(3600);
    }

    @Test
    @DisplayName("normalize repairs null nested sections")
    void normalize_repairsNullNestedSections() {
        TomlXcoreConfig toml = new TomlXcoreConfig();
        toml.server = null;
        toml.paths = null;
        toml.discord = null;
        toml.transport = null;
        toml.runtime = null;
        toml.eventHub = null;
        toml.telemetry = null;
        toml.translation = null;
        toml.ipReputation = null;

        toml.normalize();

        assertThat(toml.server).isNotNull();
        assertThat(toml.paths).isNotNull();
        assertThat(toml.discord).isNotNull();
        assertThat(toml.transport).isNotNull();
        assertThat(toml.runtime).isNotNull();
        assertThat(toml.eventHub).isNotNull();
        assertThat(toml.telemetry).isNotNull();
        assertThat(toml.translation).isNotNull();
        assertThat(toml.ipReputation).isNotNull();

        assertThat(toml.server.name).isEqualTo("server");
        assertThat(toml.transport.redis.url).isEqualTo("redis://127.0.0.1:6379");
        assertThat(toml.translation.pipeline).containsExactly("google");
    }

    @Test
    @DisplayName("normalize converts blank optional strings to null")
    void normalize_convertsBlankOptionalStringsToNull() {
        TomlXcoreConfig toml = new TomlXcoreConfig();
        toml.server.publicHostOverride = "   ";
        toml.paths.globalConfigDirectory = "\t";

        toml.normalize();

        assertThat(toml.server.publicHostOverride).isNull();
        assertThat(toml.paths.globalConfigDirectory).isNull();
    }

    @Test
    @DisplayName("normalize repairs invalid numeric values")
    void normalize_repairsInvalidNumericValues() {
        TomlXcoreConfig toml = new TomlXcoreConfig();
        toml.transport.redis.reclaim.minIdleMs = -1;
        toml.transport.redis.reclaim.batch = 0;
        toml.transport.redis.dlq.maxDeliveryAttempts = -5;
        toml.telemetry.publishIntervalMs = 0;
        toml.telemetry.sampleIntervalMs = -10;
        toml.telemetry.ttlSeconds = 0;
        toml.telemetry.maxCompressedSnapshotBytes = -1;
        toml.telemetry.maxUncompressedSnapshotBytes = 0;
        toml.translation.cache.ttlSeconds = 0;
        toml.translation.cache.maxTextLength = -1;
        toml.translation.metrics.minuteBucketTtlSeconds = 0;
        toml.translation.llm.maxInputChars = -10;
        toml.translation.llm.maxOutputChars = 0;
        toml.ipReputation.cacheTtlSeconds = -1;

        toml.normalize();

        assertThat(toml.transport.redis.reclaim.minIdleMs).isEqualTo(15000L);
        assertThat(toml.transport.redis.reclaim.batch).isEqualTo(50);
        assertThat(toml.transport.redis.dlq.maxDeliveryAttempts).isEqualTo(3);
        assertThat(toml.telemetry.publishIntervalMs).isEqualTo(15000);
        assertThat(toml.telemetry.sampleIntervalMs).isEqualTo(5000);
        assertThat(toml.telemetry.ttlSeconds).isEqualTo(60);
        assertThat(toml.telemetry.maxCompressedSnapshotBytes).isEqualTo(131072);
        assertThat(toml.telemetry.maxUncompressedSnapshotBytes).isEqualTo(524288);
        assertThat(toml.translation.cache.ttlSeconds).isEqualTo(1800);
        assertThat(toml.translation.cache.maxTextLength).isEqualTo(500);
        assertThat(toml.translation.metrics.minuteBucketTtlSeconds).isEqualTo(21600);
        assertThat(toml.translation.llm.maxInputChars).isEqualTo(500);
        assertThat(toml.translation.llm.maxOutputChars).isEqualTo(1200);
        assertThat(toml.ipReputation.cacheTtlSeconds).isEqualTo(3600);
    }

    @Test
    @DisplayName("normalize restores default pipeline when empty")
    void normalize_restoresDefaultPipeline_whenEmpty() {
        TomlXcoreConfig toml = new TomlXcoreConfig();
        toml.translation.pipeline.clear();

        toml.normalize();

        assertThat(toml.translation.pipeline).containsExactly("google");
    }

    @Test
    @DisplayName("normalize repairs null redis nested sections")
    void normalize_repairsNullRedisNestedSections() {
        TomlXcoreConfig toml = new TomlXcoreConfig();
        toml.transport.redis.reclaim = null;
        toml.transport.redis.dlq = null;

        toml.normalize();

        assertThat(toml.transport.redis.reclaim).isNotNull();
        assertThat(toml.transport.redis.reclaim.enabled).isTrue();
        assertThat(toml.transport.redis.dlq).isNotNull();
        assertThat(toml.transport.redis.dlq.prefix).isEqualTo("xcore:dlq");
    }

    @Test
    @DisplayName("normalize repairs null translation nested sections")
    void normalize_repairsNullTranslationNestedSections() {
        TomlXcoreConfig toml = new TomlXcoreConfig();
        toml.translation.cache = null;
        toml.translation.metrics = null;
        toml.translation.llm = null;

        toml.normalize();

        assertThat(toml.translation.cache).isNotNull();
        assertThat(toml.translation.metrics).isNotNull();
        assertThat(toml.translation.llm).isNotNull();
    }
}
