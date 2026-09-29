package org.xcore.plugin.security.ingress;

import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression cover for the two fail-open holes the ingress posture is supposed to close.
 *
 * <p>The whole point of a fail-closed handshake is that a check which cannot answer has not
 * cleared the connection. Both cases here previously returned Allowed.
 */
class IngressServiceFailureModeTest {

    @Test
    @DisplayName("a check that dies with an Error denies the connection instead of admitting it")
    void crashedFailClosedCheck_denies() {
        IngressCheck exploding = new StubCheck("exploding", FailureMode.FAIL_CLOSED) {
            @Override
            public AccessResult check(NetConnection con, ConnectPacket packet) {
                // A Throwable that is not an Exception escapes the catch inside the check.
                throw new NoClassDefFoundError("boom");
            }
        };

        IngressService service = new IngressService(List.of(exploding), metrics(), config());

        AccessResult result = service.validate(connection(), packet());

        assertThat(result).isInstanceOf(AccessResult.Denied.class);
    }

    @Test
    @DisplayName("a crashed check still admits when that check declared FAIL_OPEN")
    void crashedFailOpenCheck_admits() {
        IngressCheck exploding = new StubCheck("exploding", FailureMode.FAIL_OPEN) {
            @Override
            public AccessResult check(NetConnection con, ConnectPacket packet) {
                throw new NoClassDefFoundError("boom");
            }
        };

        IngressService service = new IngressService(List.of(exploding), metrics(), config());

        assertThat(service.validate(connection(), packet())).isInstanceOf(AccessResult.Allowed.class);
    }

    @Test
    @DisplayName("a definite deny is returned even when a sibling check crashes")
    void denyOutranksLaterCrash() {
        // The crash is delayed so it is polled second, and the deny is instant so it is
        // polled first - the ordering that a naive implementation happens to get right.
        IngressCheck denying = new StubCheck("denying", FailureMode.FAIL_CLOSED) {
            @Override
            public AccessResult check(NetConnection con, ConnectPacket packet) {
                return new AccessResult.Denied("banned", false);
            }
        };
        IngressCheck exploding = new StubCheck("exploding", FailureMode.FAIL_CLOSED) {
            @Override
            public AccessResult check(NetConnection con, ConnectPacket packet) {
                throw new NoClassDefFoundError("boom");
            }
        };

        IngressService service = new IngressService(List.of(denying, exploding), metrics(), config());

        AccessResult result = service.validate(connection(), packet());

        assertThat(result).isInstanceOf(AccessResult.Denied.class);
        assertThat(((AccessResult.Denied) result).reason()).isEqualTo("banned");
    }

    @Test
    @DisplayName("a definite deny still wins when the crash is polled first")
    void denyOutranksCrashPolledBeforeIt() {
        // The original test only ever produced the ordering a naive implementation gets
        // right by luck: whichever of the two instantaneous tasks finished first was the
        // deny, so the crash was never seen before the verdict was returned. This is the
        // ordering that actually raced in CI.
        //
        // A NoClassDefFoundError is an Error, not an Exception, so the worker's catch
        // (Exception) does not see it and the task completes exceptionally. Resolving the
        // verdict at that moment would report "unavailable" from the first submitted check
        // and lose the real reason - purely by scheduling.
        //
        // The deny is delayed rather than the crash being instant, because two instant
        // tasks are themselves unordered: holding back a third check would not pin these
        // two against each other.
        IngressCheck exploding = new StubCheck("exploding", FailureMode.FAIL_CLOSED) {
            @Override
            public AccessResult check(NetConnection con, ConnectPacket packet) {
                throw new NoClassDefFoundError("boom");
            }
        };
        IngressCheck denying = new StubCheck("denying", FailureMode.FAIL_CLOSED) {
            @Override
            public AccessResult check(NetConnection con, ConnectPacket packet) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return new AccessResult.Denied("banned", false);
            }
        };

        IngressService service = new IngressService(List.of(exploding, denying), metrics(), config());

        AccessResult result = service.validate(connection(), packet());

        assertThat(result).isInstanceOf(AccessResult.Denied.class);
        assertThat(((AccessResult.Denied) result).reason())
                .as("a real deny must not be replaced by an \"unavailable\" verdict")
                .isEqualTo("banned");
    }

    @Test
    @DisplayName("the global failure-mode override still wins over a crashed check")
    void globalFailOpenOverride_admits() {
        IngressCheck exploding = new StubCheck("exploding", FailureMode.FAIL_CLOSED) {
            @Override
            public AccessResult check(NetConnection con, ConnectPacket packet) {
                throw new NoClassDefFoundError("boom");
            }
        };
        TomlXcoreConfig cfg = config();
        cfg.server.ingressFailureMode = "open";

        IngressService service = new IngressService(List.of(exploding), metrics(), cfg);

        assertThat(service.validate(connection(), packet())).isInstanceOf(AccessResult.Allowed.class);
    }

    @Test
    @DisplayName("a plain allow still allows - the fail-closed path must not swallow normal traffic")
    void healthyCheck_admits() {
        IngressCheck healthy = new StubCheck("healthy", FailureMode.FAIL_CLOSED) {
            @Override
            public AccessResult check(NetConnection con, ConnectPacket packet) {
                return AccessResult.Allowed.INSTANCE;
            }
        };

        IngressService service = new IngressService(List.of(healthy), metrics(), config());

        assertThat(service.validate(connection(), packet())).isInstanceOf(AccessResult.Allowed.class);
    }

    private static TomlXcoreConfig config() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.normalize();
        return config;
    }

    private static MetricsService metrics() {
        return mock(MetricsService.class);
    }

    private static NetConnection connection() {
        return mock(NetConnection.class);
    }

    private static ConnectPacket packet() {
        return new ConnectPacket();
    }

    /** Local rather than a real check: these cases are about the orchestrator, not a check. */
    private abstract static class StubCheck implements IngressCheck {
        private final String name;
        private final FailureMode failureMode;

        StubCheck(String name, FailureMode failureMode) {
            this.name = name;
            this.failureMode = failureMode;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public FailureMode failureMode() {
            return failureMode;
        }
    }
}
