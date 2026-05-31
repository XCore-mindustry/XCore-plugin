package org.xcore.plugin.security.ingress;

import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.shared.MetricSampleV1;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.metrics.DefaultMetricsService;
import org.xcore.plugin.metrics.LocalMetricRegistry;
import org.xcore.plugin.metrics.XcoreMetrics;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IngressServiceAvajeTest {

    private IngressService service;
    private LocalMetricRegistry registry;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        // Avoid leaking interrupted state between tests.
        Thread.interrupted();
    }

    @Test
    @DisplayName("fast check deny returns Denied immediately")
    void fastCheckDenyReturnsDeniedImmediately() {
        var fastDeny = StubIngressCheck.deny("fast-deny", -10, "blocked");
        var fastAllow = StubIngressCheck.allow("fast-allow", -5);
        var slowAllow = StubIngressCheck.allow("slow-allow", 10);
        var service = buildService(fastDeny, fastAllow, slowAllow);

        var result = service.validate(null, null);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied ->
                assertThat(denied.reason()).isEqualTo("blocked"));
        assertThat(fastDeny.calls).isEqualTo(1);
        assertThat(fastAllow.calls).isZero();
        assertThat(slowAllow.calls).isZero();
        assertThat(sample(XcoreMetrics.INGRESS_DENIALS_TOTAL.name(), "check", "fast-deny", "silent", "false"))
                .get()
                .extracting(MetricSampleV1::value)
                .isEqualTo(1d);
    }

    @Test
    @DisplayName("all fast checks allow and no slow checks returns Allowed")
    void allFastAllowAndNoSlowReturnsAllowed() {
        var fastAllowA = StubIngressCheck.allow("fast-a", -20);
        var fastAllowB = StubIngressCheck.allow("fast-b", -10);
        var service = buildService(fastAllowA, fastAllowB);

        var result = service.validate(null, null);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
        assertThat(fastAllowA.calls).isEqualTo(1);
        assertThat(fastAllowB.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("slow check deny returns Denied")
    void slowCheckDenyReturnsDenied() {
        var fastAllow = StubIngressCheck.allow("fast-a", -10);
        var slowAllow = StubIngressCheck.allow("slow-a", 0);
        var slowDeny = StubIngressCheck.deny("slow-deny", 5, "slow blocked");
        var service = buildService(fastAllow, slowAllow, slowDeny);

        var result = service.validate(null, null);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied ->
                assertThat(denied.reason()).isEqualTo("slow blocked"));
        assertThat(fastAllow.calls).isEqualTo(1);
        assertThat(slowAllow.calls).isEqualTo(1);
        assertThat(slowDeny.calls).isEqualTo(1);
        assertThat(sample(XcoreMetrics.INGRESS_DENIALS_TOTAL.name(), "check", "slow-deny", "silent", "false"))
                .get()
                .extracting(MetricSampleV1::value)
                .isEqualTo(1d);
    }

    @Test
    @DisplayName("exception in check does not fail validation and processing continues")
    void exceptionInCheckDoesNotFailValidation() {
        var fastThrows = StubIngressCheck.throwing("fast-throws", -20);
        var fastAllow = StubIngressCheck.allow("fast-allow", -10);
        var service = buildService(fastThrows, fastAllow);

        var result = service.validate(null, null);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
        assertThat(fastThrows.calls).isEqualTo(1);
        assertThat(fastAllow.calls).isEqualTo(1);
        assertThat(sample(XcoreMetrics.INGRESS_CHECK_ERRORS_TOTAL.name(), "check", "fast-throws", "phase", "fast"))
                .get()
                .extracting(MetricSampleV1::value)
                .isEqualTo(1d);
    }

    @Test
    @DisplayName("interrupted parallel checks returns silent Interrupted deny")
    void interruptedParallelChecksReturnsInterruptedDenied() {
        var slowAllow = StubIngressCheck.allow("slow-a", 0);
        var service = buildService(slowAllow);
        Thread.currentThread().interrupt();

        var result = service.validate(null, null);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied -> {
            assertThat(denied.reason()).isEqualTo("Interrupted");
            assertThat(denied.silent()).isTrue();
        });
    }

    private IngressService buildService(IngressCheck... checks) {
        registry = new LocalMetricRegistry();
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.telemetry.enabled = true;
        service = new IngressService(List.of(checks), new DefaultMetricsService(registry, config));
        return service;
    }

    private Optional<MetricSampleV1> sample(String metricName,
                                            String labelName,
                                            String labelValue,
                                            String secondLabelName,
                                            String secondLabelValue) {
        return registry.snapshot().stream()
                .filter(sample -> sample.name().equals(metricName))
                .filter(sample -> labelValue.equals(sample.labels().get(labelName)))
                .filter(sample -> secondLabelValue.equals(sample.labels().get(secondLabelName)))
                .findFirst();
    }

    private static final class StubIngressCheck implements IngressCheck {
        private final String name;
        private final int priority;
        private final CheckBehavior behavior;
        private int calls;

        private StubIngressCheck(String name, int priority, CheckBehavior behavior) {
            this.name = name;
            this.priority = priority;
            this.behavior = behavior;
        }

        static StubIngressCheck allow(String name, int priority) {
            return new StubIngressCheck(name, priority, (con, packet) -> AccessResult.Allowed.INSTANCE);
        }

        static StubIngressCheck deny(String name, int priority, String reason) {
            return new StubIngressCheck(name, priority, (con, packet) -> new AccessResult.Denied(reason));
        }

        static StubIngressCheck throwing(String name, int priority) {
            return new StubIngressCheck(name, priority, (con, packet) -> {
                throw new IllegalStateException("boom");
            });
        }

        @Override
        public AccessResult check(NetConnection con, Packets.ConnectPacket packet) {
            calls++;
            return behavior.apply(con, packet);
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public String name() {
            return name;
        }
    }

    @FunctionalInterface
    private interface CheckBehavior {
        AccessResult apply(NetConnection con, Packets.ConnectPacket packet);
    }
}
