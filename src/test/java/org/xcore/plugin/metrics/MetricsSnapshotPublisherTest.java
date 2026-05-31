package org.xcore.plugin.metrics;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsSnapshotPublisherTest {

    @Test
    @DisplayName("publishOnce writes gzip telemetry snapshot to redis TTL key")
    void publishOnce_writesGzipTelemetrySnapshotToRedisTtlKey() throws Exception {
        LocalMetricRegistry registry = new LocalMetricRegistry();
        registry.counter(XcoreMetrics.PLAYER_JOINS_TOTAL, Tags.empty()).add(2);
        registry.gauge(XcoreMetrics.PLAYERS_ONLINE, Tags.empty()).set(9);
        registry.histogram(new HistogramDef(
                "xcore_command_duration_seconds",
                "Command duration",
                "seconds",
                LabelSchema.empty(),
                new double[]{0.1d, 0.5d, 1.0d}
        ), Tags.empty()).observe(0.05d);
        registry.histogram(new HistogramDef(
                "xcore_command_duration_seconds",
                "Command duration",
                "seconds",
                LabelSchema.empty(),
                new double[]{0.1d, 0.5d, 1.0d}
        ), Tags.empty()).observe(0.4d);
        registry.histogram(new HistogramDef(
                "xcore_command_duration_seconds",
                "Command duration",
                "seconds",
                LabelSchema.empty(),
                new double[]{0.1d, 0.5d, 1.0d}
        ), Tags.empty()).observe(0.8d);

        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        RedisCommands<String, byte[]> commands = mock(RedisCommands.class);
        AtomicReference<byte[]> payloadRef = new AtomicReference<>();

        when(commands.set(anyString(), any(byte[].class), any(SetArgs.class))).thenAnswer(invocation -> {
            payloadRef.set(invocation.getArgument(1));
            return "OK";
        });
        when(backend.withBinaryCommands(any(Function.class), any())).thenAnswer(invocation -> {
            Function<RedisCommands<String, byte[]>, Boolean> function = invocation.getArgument(0);
            return function.apply(commands);
        });

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        config.transport.redis.consumerName = "mini-pvp-01";
        config.telemetry.enabled = true;

        BuildInfo buildInfo = new BuildInfo();
        buildInfo.setVersion("4.1.0-SNAPSHOT");

        MetricsSnapshotPublisher publisher = new MetricsSnapshotPublisher(registry, backend, config, new Gson(), buildInfo);

        boolean published = publisher.publishOnce();

        assertThat(published).isTrue();
        verify(commands).set(eq("xcore:metrics:snapshot:mini-pvp"), any(byte[].class), any(SetArgs.class));
        assertThat(payloadRef.get()).isNotNull();

        String json = ungzip(payloadRef.get());
        Map<String, Object> payload = new Gson().fromJson(json, Map.class);
        assertThat(payload.get("createdAtUnixMs")).isInstanceOf(Number.class);
        assertThat(payload.get("startTimeUnixMs")).isInstanceOf(Number.class);

        Map<String, Object> expectedPayload = loadExpectedPayloadFixture();
        expectedPayload.put("createdAtUnixMs", payload.get("createdAtUnixMs"));
        expectedPayload.put("startTimeUnixMs", payload.get("startTimeUnixMs"));

        assertThat(payload).isEqualTo(expectedPayload);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadExpectedPayloadFixture() {
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(
                        MetricsSnapshotPublisherTest.class.getResourceAsStream("/telemetry/plugin-snapshot-contract.json")
                ),
                StandardCharsets.UTF_8
        )) {
            return new Gson().fromJson(reader, Map.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load telemetry fixture", exception);
        }
    }

    private static String ungzip(byte[] compressed) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}
