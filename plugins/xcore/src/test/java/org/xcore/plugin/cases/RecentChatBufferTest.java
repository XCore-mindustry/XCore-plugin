package org.xcore.plugin.cases;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.protocol.generated.shared.CaseChatLineV1Role;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RecentChatBufferTest {
    private final CasesWorld world = new CasesWorld();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final RecentChatBuffer chat = new RecentChatBuffer(world.feature, world.sessions, now::get);

    @AfterEach
    void resetThread() {
        GameThread.setGameThreadOverride(null);
    }

    @Test
    void excerptKeepsTwentyNewestTargetLinesAndFillsRemainingWithContext() {
        for (int i = 0; i < 25; i++) {
            chat.append("target", 7, "[red]Target", "target " + i, false);
            now.incrementAndGet();
        }
        for (int i = 0; i < 12; i++) {
            chat.append(i == 11 ? "reporter" : "other", 9, "Other", "context " + i, i == 11);
            now.incrementAndGet();
        }
        var excerpt = chat.excerpt("target", "reporter", 30, 600);
        assertThat(excerpt).hasSize(30);
        assertThat(excerpt.getFirst().text()).isEqualTo("target 5");
        assertThat(excerpt.getFirst().playerName()).isEqualTo("Target");
        assertThat(excerpt.stream().filter(line -> line.role() == CaseChatLineV1Role.TARGET)).hasSize(20);
        assertThat(excerpt.stream().filter(line -> line.role() == CaseChatLineV1Role.OTHER)).hasSize(9);
        assertThat(excerpt.getLast().role()).isEqualTo(CaseChatLineV1Role.REPORTER);
        assertThat(excerpt.getLast().team()).isTrue();
        assertThat(excerpt).isSortedAccordingTo(java.util.Comparator.comparingLong(line -> line.sentAt()));
        assertThat(excerpt.toString()).doesNotContain("uuid");
    }

    @Test
    void windowAndRetentionExpireWhileReadOnlyAndRingIsBounded() {
        chat.append("target", 7, "Target", "old", false);
        now.addAndGet(601_000);
        assertThat(chat.excerpt("target", null, 30, 600)).isEmpty();
        assertThat(chat.excerpt("target", null, 30, 900)).hasSize(1);
        now.addAndGet(300_000);
        assertThat(chat.excerpt("target", null, 30, 1800)).isEmpty();
        for (int i = 0; i < 220; i++) chat.append("other", 7, "Other", "line " + i, false);
        assertThat(chat.excerpt("target", null, 100, 600)).hasSize(30);
        assertThat(chat.excerpt("target", null, 30, 600).getFirst().text()).isEqualTo("line 190");
    }

    @Test
    void clampsTextAndOmitsInvalidPidAndCommands() {
        chat.append("target", PlayerPids.NONE, "[red]" + "x".repeat(80), "[green]" + "y".repeat(300), false);
        chat.append("target", 7, "Target", " /login secret", false);
        var excerpt = chat.excerpt("target", null, 30, 600);
        assertThat(excerpt).hasSize(1);
        assertThat(excerpt.getFirst().text()).hasSize(256);
        assertThat(excerpt.getFirst().playerName()).hasSize(64);
        assertThat(excerpt.getFirst().playerPid()).isNull();
    }

    @Test
    void featureOffAndLocalKillSwitchAreInertAndClearPreviousEvidence() {
        chat.append("target", 7, "Target", "before", false);
        world.secrets.cases.reports.enabled = false;
        for (int i = 0; i < 300; i++) chat.append("target", 7, "Target", "off", false);
        assertThat(chat.excerpt("target", null, 30, 600)).isEmpty();
        world.secrets.cases.reports.enabled = true;
        assertThat(chat.excerpt("target", null, 30, 600)).isEmpty();
        world.config.runtime.disabledFeatures.add("reports");
        chat.append("target", 7, "Target", "killed", false);
        assertThat(chat.excerpt("target", null, 30, 600)).isEmpty();
    }

    @Test
    void gameThreadIsRequired() throws Exception {
        GameThread.setGameThreadOverride(Thread.currentThread());
        var failed = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                chat.excerpt("target", null, 30, 600);
            } catch (Throwable error) {
                failed.set(error);
            }
        });
        worker.start();
        worker.join();
        assertThat(failed.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stopDiscardsEvidence() {
        chat.append("target", 7, "Target", "before", false);
        chat.stop();
        assertThat(chat.excerpt("target", null, 30, 600)).isEmpty();
    }
}
