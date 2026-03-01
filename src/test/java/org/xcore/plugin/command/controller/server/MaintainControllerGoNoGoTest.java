package org.xcore.plugin.command.controller.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.command.transport.TransportStage;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaintainControllerGoNoGoTest {

    @Test
    @DisplayName("go decision is true when redis gates and thresholds are satisfied")
    void goDecisionTrueWhenThresholdsSatisfied() {
        Config config = new Config();
        config.transportType = Config.TransportType.REDIS;
        config.redisShadowPublishEnabled = true;
        config.redisConsumeEnabled = true;
        config.redisRpcEnabled = true;
        config.redisMutatingConsumeEnabled = true;
        config.redisCanaryMaxDlqRouted = 1;
        config.redisCanaryMaxRpcTimeouts = 2;
        config.redisCanaryMaxConsumeFailures = 3;

        Map<String, Long> metrics = Map.of(
                "shadow.dlq_routed", 1L,
                "shadow.rpc_timeouts", 2L,
                "shadow.consume_failures", 3L,
                "shadow.active_subscriber_threads", 2L
        );

        var result = MaintainController.evaluateGoNoGo(config, "DUAL", metrics);
        assertThat(result.goDecision()).isTrue();
        assertThat(result.criticalFailed()).isEmpty();
        assertThat(result.nextAction()).isEqualTo("proceed_cutover");
    }

    @Test
    @DisplayName("go decision is false when redis falls back to sock")
    void goDecisionFalseWhenRedisFallsBackToSock() {
        Config config = new Config();
        config.transportType = Config.TransportType.REDIS;
        config.redisShadowPublishEnabled = true;
        config.redisConsumeEnabled = true;

        Map<String, Long> metrics = Map.of("shadow.active_subscriber_threads", 1L);
        var result = MaintainController.evaluateGoNoGo(config, "SOCK", metrics);

        assertThat(result.goDecision()).isFalse();
        assertThat(result.criticalFailed()).isNotEmpty();
        assertThat(result.nextAction()).isEqualTo("hold_and_fix");
    }

    @Test
    @DisplayName("advisory checks report non-critical warning metrics")
    void advisoryChecksReportWarnings() {
        Config config = new Config();
        config.transportType = Config.TransportType.SOCK;

        Map<String, Long> metrics = Map.of(
                "shadow.tracked_failures", 1L,
                "shadow.pending_rpc_contexts", 2L,
                "shadow.publish_failures", 3L
        );

        var result = MaintainController.evaluateGoNoGo(config, "SOCK", metrics);
        assertThat(result.goDecision()).isTrue();
        assertThat(result.advisoryFailed()).contains(
                "tracked_failures > 0",
                "pending_rpc_contexts > 0",
                "publish_failures > 0"
        );
    }

    @Test
    @DisplayName("stage gate publish is ready when publish flag enabled and no publish failures")
    void stageGatePublishReady() {
        Config config = new Config();
        config.transportType = Config.TransportType.DUAL;
        config.redisShadowPublishEnabled = true;

        var result = MaintainController.evaluateStageGate(config, "DUAL", Map.of("shadow.publish_failures", 0L), TransportStage.PUBLISH);

        assertThat(result.ready()).isTrue();
        assertThat(result.blockingReasons()).isEmpty();
    }

    @Test
    @DisplayName("stage gate read-only is blocked when subscriber threads are missing")
    void stageGateReadOnlyBlockedWithoutSubscribers() {
        Config config = new Config();
        config.transportType = Config.TransportType.DUAL;
        config.redisShadowPublishEnabled = true;
        config.redisConsumeEnabled = true;

        var result = MaintainController.evaluateStageGate(config, "DUAL", Map.of("shadow.active_subscriber_threads", 0L), TransportStage.READ_ONLY);

        assertThat(result.ready()).isFalse();
        assertThat(result.blockingReasons()).contains("active_subscriber_threads <= 0");
    }

    @Test
    @DisplayName("stage gate rpc is blocked when timeout threshold is exceeded")
    void stageGateRpcBlockedByTimeoutThreshold() {
        Config config = new Config();
        config.transportType = Config.TransportType.REDIS;
        config.redisShadowPublishEnabled = true;
        config.redisConsumeEnabled = true;
        config.redisRpcEnabled = true;
        config.redisCanaryMaxRpcTimeouts = 1;

        var result = MaintainController.evaluateStageGate(config, "REDIS", Map.of(
                "rpc_timeouts", 3L,
                "active_subscriber_threads", 2L
        ), TransportStage.RPC);

        assertThat(result.ready()).isFalse();
        assertThat(result.blockingReasons()).contains("rpc_timeouts exceeded threshold");
    }
}
