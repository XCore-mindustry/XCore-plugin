package org.xcore.plugin.cases;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRateLimiterTest {
    private final TomlSecretsConfig config = new TomlSecretsConfig();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final ReportRateLimiter limiter = new ReportRateLimiter(config, now::get);

    @BeforeEach
    void disableOtherWindows() {
        config.cases.reports.cooldownSeconds = 0;
        config.cases.reports.sameTargetCooldownMinutes = 0;
        config.cases.reports.dailyLimit = 0;
        config.cases.reports.networkBurstLimit = 0;
    }

    @Test
    void cooldownRoundsUpAndExpiresAtBoundary() {
        config.cases.reports.cooldownSeconds = 60;
        assertThat(limiter.reserve("a", "b", "address").refusal()).isNull();
        now.addAndGet(1);
        assertThat(limiter.reserve("a", "c", "address").refusal()).isEqualTo(new ReportResult.Cooldown(60));
        now.addAndGet(59_999);
        assertThat(limiter.reserve("a", "c", "address").refusal()).isNull();
    }

    @Test
    void sameTargetWindowLeavesOtherTargetsAvailable() {
        config.cases.reports.sameTargetCooldownMinutes = 30;
        limiter.reserve("a", "b", "address");
        now.addAndGet(29 * 60_000L);
        assertThat(limiter.reserve("a", "b", "address").refusal()).isInstanceOf(ReportResult.SameTarget.class);
        assertThat(limiter.reserve("a", "c", "address").refusal()).isNull();
        now.addAndGet(60_000L);
        assertThat(limiter.reserve("a", "b", "address").refusal()).isNull();
    }

    @Test
    void dailyWindowIsRollingAndIndependentForEachReporter() {
        config.cases.reports.dailyLimit = 2;
        limiter.reserve("a", "b", "address");
        now.addAndGet(60_000);
        limiter.reserve("a", "c", "address");
        assertThat(limiter.reserve("a", "d", "address").refusal()).isInstanceOf(ReportResult.DailyLimit.class);
        assertThat(limiter.reserve("another", "d", "address").refusal()).isNull();
        now.addAndGet(24 * 60 * 60_000L - 60_001);
        assertThat(limiter.reserve("a", "d", "address").refusal()).isInstanceOf(ReportResult.DailyLimit.class);
        now.incrementAndGet();
        assertThat(limiter.reserve("a", "d", "address").refusal()).isNull();
    }

    @Test
    void networkBurstAggregatesDistinctUuidsWithoutSharingReporterCooldowns() {
        config.cases.reports.cooldownSeconds = 60;
        config.cases.reports.networkBurstLimit = 2;
        limiter.reserve("a", "b", "shared");
        assertThat(limiter.reserve("c", "b", "shared").refusal()).isNull();
        assertThat(limiter.reserve("d", "b", "shared").refusal()).isEqualTo(new ReportResult.Cooldown(600));
        assertThat(limiter.reserve("e", "b", "different").refusal()).isNull();
        now.addAndGet(600_000);
        assertThat(limiter.reserve("d", "b", "shared").refusal()).isNull();
    }

    @Test
    void returnedReservationRestoresEveryWindowAndCannotReturnAnotherSlot() {
        config.cases.reports.cooldownSeconds = 60;
        config.cases.reports.sameTargetCooldownMinutes = 30;
        config.cases.reports.dailyLimit = 1;
        config.cases.reports.networkBurstLimit = 1;
        var first = limiter.reserve("a", "b", "address");
        limiter.release(first.reservation());
        var second = limiter.reserve("a", "b", "address");
        assertThat(second.refusal()).isNull();
        limiter.release(first.reservation());
        assertThat(limiter.reserve("a", "b", "address").refusal()).isInstanceOf(ReportResult.Cooldown.class);
    }

    @Test
    void stateIsCappedWithoutEvictingUnexpiredAllowances() {
        for (int i = 0; i < ReportRateLimiter.CAPACITY; i++) limiter.reserve("r" + i, "t", "");
        assertThat(limiter.reserve("extra", "t", "").refusal()).isInstanceOf(ReportResult.Failed.class);
        now.addAndGet(24 * 60 * 60_000L);
        assertThat(limiter.reserve("extra", "t", "").refusal()).isNull();
    }

    @Test
    void shutdownReleasesStateAndRefusesNewReservations() {
        limiter.reserve("a", "b", "address");
        limiter.stop();
        assertThat(limiter.reserve("a", "b", "address").refusal()).isInstanceOf(ReportResult.Disabled.class);
    }
}
