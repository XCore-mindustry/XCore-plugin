package org.xcore.plugin.metrics;

import com.google.gson.Gson;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import io.lettuce.core.SetArgs;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.protocol.generated.messages.telemetry.TelemetryMessages.MetricsSnapshotV1;
import org.xcore.protocol.generated.shared.MetricSampleV1;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

@Singleton
public final class MetricsSnapshotPublisher {
    private static final String SNAPSHOT_KEY_PREFIX = "xcore:metrics:snapshot:";
    private static final String PRODUCER_NAME = "xcore-plugin";

    private final LocalMetricRegistry registry;
    private final RedisNetworkBackend backend;
    private final TomlXcoreConfig config;
    private final Gson gson;
    private final BuildInfo buildInfo;
    private final AtomicLong sequence = new AtomicLong();
    private final long startTimeUnixMs = System.currentTimeMillis();

    private volatile boolean running;
    private Thread worker;

    public MetricsSnapshotPublisher(LocalMetricRegistry registry,
                                    RedisNetworkBackend backend,
                                    TomlXcoreConfig config,
                                    @Named("raw") Gson gson,
                                    BuildInfo buildInfo) {
        this.registry = registry;
        this.backend = backend;
        this.config = config;
        this.gson = gson;
        this.buildInfo = buildInfo;
    }

    @PostConstruct
    public void init() {
        if (!config.telemetry.enabled) {
            return;
        }

        running = true;
        worker = Thread.ofVirtual().name("telemetry-snapshot-publisher").start(this::runLoop);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    boolean publishOnce() {
        try {
            List<MetricSampleV1> samples = registry.snapshot();
            if (samples.isEmpty()) {
                return false;
            }

            MetricsSnapshotV1 snapshot = new MetricsSnapshotV1(
                    config.server.name,
                    resolveNodeId(),
                    resolveProducer(),
                    System.currentTimeMillis(),
                    startTimeUnixMs,
                    sequence.getAndIncrement(),
                    config.telemetry.publishIntervalMs,
                    samples
            );

            byte[] uncompressed = gson.toJson(snapshot.toPayload()).getBytes(StandardCharsets.UTF_8);
            if (uncompressed.length > config.telemetry.maxUncompressedSnapshotBytes) {
                return false;
            }

            byte[] compressed = gzip(uncompressed);
            if (compressed.length > config.telemetry.maxCompressedSnapshotBytes) {
                return false;
            }

            return backend.withBinaryCommands(commands -> {
                commands.set(snapshotKey(), compressed, SetArgs.Builder.ex(config.telemetry.ttlSeconds));
                return true;
            }, false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void runLoop() {
        publishOnce();
        while (running) {
            try {
                Thread.sleep(config.telemetry.publishIntervalMs);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
            publishOnce();
        }
    }

    private String snapshotKey() {
        return SNAPSHOT_KEY_PREFIX + config.server.name;
    }

    private String resolveNodeId() {
        if (config.telemetry.nodeId != null && !config.telemetry.nodeId.isBlank()) {
            return config.telemetry.nodeId;
        }
        if (config.transport.redis.consumerName != null && !config.transport.redis.consumerName.isBlank()) {
            return config.transport.redis.consumerName;
        }
        return config.server.name;
    }

    private String resolveProducer() {
        String version = buildInfo.getVersion();
        if (version == null || version.isBlank() || "Unknown".equals(version)) {
            return PRODUCER_NAME;
        }
        return PRODUCER_NAME + "/" + version;
    }

    private static byte[] gzip(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(output)) {
            gzipOutputStream.write(input);
        }
        return output.toByteArray();
    }
}
