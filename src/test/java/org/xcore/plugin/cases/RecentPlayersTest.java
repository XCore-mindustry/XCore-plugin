package org.xcore.plugin.cases;

import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.PlayerPids;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RecentPlayersTest {
    private final CasesWorld world = new CasesWorld();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final RecentPlayers recent = new RecentPlayers(world.feature, world.secrets, now::get);

    @Test
    void leavesExpireAtConfiguredBoundaryAndJoiningRemovesOldSnapshot() {
        recent.remember("a", 1, "[red]Alice", "address", false);
        assertThat(recent.find("a").target().name()).isEqualTo("Alice");
        now.addAndGet(599_999);
        assertThat(recent.find("a")).isNotNull();
        now.incrementAndGet();
        assertThat(recent.find("a")).isNull();
        recent.remember("a", 1, "Alice", "address", false);
        recent.joined("a");
        assertThat(recent.find("a")).isNull();
    }

    @Test
    void keepsLatestLeaveAndBoundsTheCache() {
        for (int i = 0; i < 600; i++) recent.remember("u" + i, i + 1, "Player", "address", false);
        assertThat(recent.snapshot()).hasSize(RecentPlayers.CAPACITY);
        assertThat(recent.find("u0")).isNull();
        recent.remember("u599", 600, "Changed", "new address", true);
        assertThat(recent.snapshot()).hasSize(RecentPlayers.CAPACITY);
        assertThat(recent.find("u599").staff()).isTrue();
        assertThat(recent.find("u599").address()).isEqualTo("new address");
    }

    @Test
    void signedPidsAreRetainedAndOnlySentinelIsUnknown() {
        recent.remember("zero", 0, "Zero", "address", false);
        recent.remember("negative", -12, "Negative", "address", false);
        recent.remember("unknown", PlayerPids.NONE, "Unknown", "address", false);

        assertThat(recent.find("zero").target().pid()).isZero();
        assertThat(recent.find("negative").target().pid()).isEqualTo(-12);
        assertThat(recent.find("unknown").target().pid()).isNull();
    }

    @Test
    void disabledAndStoppedCachesAreInert() {
        world.secrets.cases.reports.enabled = false;
        recent.remember("a", 1, "Alice", "address", false);
        assertThat(recent.snapshot()).isEmpty();
        world.secrets.cases.reports.enabled = true;
        recent.remember("a", 1, "Alice", "address", false);
        recent.stop();
        assertThat(recent.snapshot()).isEmpty();
    }
}
