package org.xcore.protocol.generated.messages.telemetry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;
import org.xcore.protocol.generated.shared.MetricSampleV1;

public final class TelemetryMessages {
    private TelemetryMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.telemetry.TelemetryMessages instances");
    }

    public record MetricsSnapshotV1(
            String server,
            String nodeId,
            String producer,
            long createdAtUnixMs,
            long startTimeUnixMs,
            long sequence,
            int intervalMs,
            List<MetricSampleV1> samples
    ) implements ProtocolPayload {
    public static final String SCHEMA_VERSION = "metrics.snapshot.v1";

        public MetricsSnapshotV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(nodeId, "nodeId must not be null");
            if (nodeId.length() < 1) {
                throw new IllegalArgumentException("nodeId must be at least 1 characters");
            }
            Objects.requireNonNull(producer, "producer must not be null");
            if (producer.length() < 1) {
                throw new IllegalArgumentException("producer must be at least 1 characters");
            }
            if (createdAtUnixMs < 0) {
                throw new IllegalArgumentException("createdAtUnixMs must be >= 0");
            }
            if (startTimeUnixMs < 0) {
                throw new IllegalArgumentException("startTimeUnixMs must be >= 0");
            }
            if (sequence < 0) {
                throw new IllegalArgumentException("sequence must be >= 0");
            }
            if (intervalMs < 1) {
                throw new IllegalArgumentException("intervalMs must be >= 1");
            }
            samples = Objects.requireNonNull(samples, "samples must not be null");
            samples = List.copyOf(samples);
            if (samples.size() < 1) {
                throw new IllegalArgumentException("samples must contain at least 1 item(s)");
            }
            for (MetricSampleV1 item : samples) {
                Objects.requireNonNull(item, "samples[] must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", SCHEMA_VERSION);
            payload.put("server", server);
            payload.put("nodeId", nodeId);
            payload.put("producer", producer);
            payload.put("createdAtUnixMs", createdAtUnixMs);
            payload.put("startTimeUnixMs", startTimeUnixMs);
            payload.put("sequence", sequence);
            payload.put("intervalMs", intervalMs);
            if (samples != null) {
                payload.put(
        "samples",
        samples.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            return payload;
        }
    }
}
