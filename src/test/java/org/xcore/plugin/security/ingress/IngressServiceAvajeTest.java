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

import java.time.Duration;
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
    @DisplayName("a check that throws denies by default: one that never ran has not cleared the connection")
    void throwingCheckDeniesByDefault() {
        var fastThrows = StubIngressCheck.throwing("fast-throws", -20);
        var fastAllow = StubIngressCheck.allow("fast-allow", -10);
        var service = buildService(fastThrows, fastAllow);

        var result = service.validate(null, null);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied -> {
            assertThat(denied.reason()).isEqualTo("Security check unavailable");
            assertThat(denied.silent()).isTrue();
        });
        assertThat(fastThrows.calls).isEqualTo(1);
        assertThat(fastAllow.calls).isZero();
        assertThat(sample(XcoreMetrics.INGRESS_CHECK_ERRORS_TOTAL.name(), "check", "fast-throws", "phase", "fast"))
                .get()
                .extracting(MetricSampleV1::value)
                .isEqualTo(1d);
    }

    @Test
    @DisplayName("a check that declares FAIL_OPEN is skipped, and validation continues")
    void failOpenCheckIsSkipped() {
        var openThrows = StubIngressCheck.throwing("open-throws", -20);
        openThrows.mode = FailureMode.FAIL_OPEN;
        var fastAllow = StubIngressCheck.allow("fast-allow", -10);
        var service = buildService(openThrows, fastAllow);

        var result = service.validate(null, null);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
        assertThat(openThrows.calls).isEqualTo(1);
        assertThat(fastAllow.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("ingressFailureMode=open makes every failed check fail open, overriding per-check posture")
    void operatorOverrideFailsOpen() {
        var config = new TomlXcoreConfig();
        config.server.ingressFailureMode = "open";
        var fastThrows = StubIngressCheck.throwing("fast-throws", -20);
        var service = buildService(config, fastThrows);

        assertThat(service.validate(null, null)).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("a throwing parallel check denies instead of silently admitting")
    void throwingParallelCheckDenies() {
        var slowThrows = StubIngressCheck.throwing("slow-throws", 0);
        var service = buildService(slowThrows);

        var result = service.validate(null, null);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied -> {
            assertThat(denied.reason()).isEqualTo("Security check unavailable");
            assertThat(denied.silent()).isTrue();
        });
        assertThat(sample(XcoreMetrics.INGRESS_CHECK_ERRORS_TOTAL.name(), "check", "slow-throws", "phase", "slow"))
                .get()
                .extracting(MetricSampleV1::value)
                .isEqualTo(1d);
    }

    @Test
    @DisplayName("a check overrunning the handshake budget is bounded, denied, and counted")
    void budgetExceededIsBoundedAndDenied() {
        var slowHang = StubIngressCheck.slow("slow-hang", 0, Duration.ofSeconds(30));
        var service = buildService(slowHang);

        long startNanos = System.nanoTime();
        var result = service.validate(null, null);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied ->
                assertThat(denied.reason()).isEqualTo("Security check unavailable"));
        // The old per-check 5s poll would have burned the full 5s here.
        assertThat(elapsedMillis).isLessThan(2_000);
        assertThat(registry.snapshot().stream()
                .filter(s -> s.name().equals(XcoreMetrics.INGRESS_HANDSHAKE_BUDGET_EXCEEDED_TOTAL.name()))
                .findFirst())
                .isPresent();
    }

    @Test
    @DisplayName("budget overrun admits the connection when the operator chose availability")
    void budgetExceededFailsOpenOnOperatorOverride() {
        var config = new TomlXcoreConfig();
        config.server.ingressFailureMode = "open";
        var slowHang = StubIngressCheck.slow("slow-hang", 0, Duration.ofSeconds(30));
        var service = buildService(config, slowHang);

        assertThat(service.validate(null, null)).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("a definite deny outranks a sibling check overrunning the budget")
    void definiteDenyOutranksBudget() {
        var slowDeny = StubIngressCheck.deny("slow-deny", 0, "banned");
        var slowHang = StubIngressCheck.slow("slow-hang", 1, Duration.ofSeconds(30));
        var service = buildService(slowDeny, slowHang);

        assertThat(service.validate(null, null))
                .isInstanceOfSatisfying(AccessResult.Denied.class, denied ->
                        assertThat(denied.reason()).isEqualTo("banned"));
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
        return buildService(new TomlXcoreConfig(), checks);
    }

    private IngressService buildService(TomlXcoreConfig config, IngressCheck... checks) {
        registry = new LocalMetricRegistry();
        config.telemetry.enabled = true;
        // Short enough to keep the overrun test fast, long enough that healthy checks win.
        config.server.ingressHandshakeBudgetMillis = 200;
        config.server.normalize();
        service = new IngressService(List.of(checks), new DefaultMetricsService(registry, config), config);
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
        private FailureMode mode = FailureMode.FAIL_CLOSED;
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

        static StubIngressCheck slow(String name, int priority, Duration duration) {
            return new StubIngressCheck(name, priority, (con, packet) -> {
                try {
                    Thread.sleep(duration.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return AccessResult.Allowed.INSTANCE;
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
        public FailureMode failureMode() {
            return mode;
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
