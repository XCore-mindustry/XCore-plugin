package org.xcore.plugin.cases;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.session.Session;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CaseNoticeServiceTest {
    private final CasesWorld world = new CasesWorld();
    private final Session player = world.session("player", 1, "address");
    private final CaseNoticeRepository repository = mock(CaseNoticeRepository.class);
    private final CaseNoticeService notices = new CaseNoticeService(world.feature, repository, world.secrets,
            world.config, world.sessions, world.loop.async);
    private final CaseNotice notice = new CaseNotice("57:player:report_received", "player", "report_received",
            Map.of("number", "57", "ip", "never-render", "uuid", "never-render"), "token", Instant.now().plusSeconds(120));

    @BeforeEach
    void setUp() {
        GameThread.setGameThreadOverride(Thread.currentThread());
        when(repository.claim(notice.id(), "player", "survival")).thenAnswer(invocation -> {
            assertThat(GameThread.isGameThread()).isFalse();
            return notice;
        });
        when(repository.acknowledge(notice, "survival")).thenAnswer(invocation -> {
            assertThat(GameThread.isGameThread()).isFalse();
            return true;
        });
        doAnswer(invocation -> {
            assertThat(GameThread.isGameThread()).isTrue();
            Map<String, Object> args = invocation.getArgument(1);
            assertThat(args).containsExactlyEntriesOf(Map.of("number", 57L));
            return null;
        }).when(player.localization).send(eq("case-notice-report-received"), anyMap());
    }

    @AfterEach
    void reset() {
        GameThread.setGameThreadOverride(null);
        mindustry.gen.Groups.clear();
    }

    @Test
    void rejectedReportSendsOnlyTheNumberAndAKnownReasonCodeToTheBundle() throws Exception {
        for (var reason : Map.of("abuse", "abuse", "[scarlet]never-render", "other").entrySet()) {
            var rejected = new CaseNotice("57:player:report_rejected:" + reason.getValue(), "player", "report_rejected",
                    Map.of("number", "57", "reason", reason.getKey(), "comment", "never-render"), "token",
                    Instant.now().plusSeconds(120));
            when(repository.claim(rejected.id(), "player", "survival")).thenReturn(rejected);
            notices.completeJoin(player, List.of(rejected));
            world.loop.worker();
            world.loop.main.runTurn();
            verify(player.locale()).send("case-notice-report-rejected", Map.of("number", 57L, "reason", reason.getValue()));
            world.loop.worker(); // acknowledgement
        }
    }

    @Test
    void joinShowsAtMostConfiguredNoticesAndAcknowledgesOnlyAfterDisplay() throws Exception {
        world.secrets.cases.notices.maxPerJoin = 1;
        notices.completeJoin(player, List.of(notice, new CaseNotice("other", "player", "report_resolved",
                Map.of("number", "58"), null, null)));
        verifyNoInteractions(repository);
        world.loop.worker();
        verify(repository).claim(notice.id(), "player", "survival");
        verify(repository, never()).acknowledge(any(), anyString());
        verify(player.locale(), never()).send(anyString(), anyMap());
        world.loop.main.runTurn();
        verify(player.locale()).send(eq("case-notice-report-received"), anyMap());
        verify(repository, never()).acknowledge(any(), anyString());
        world.loop.worker();
        verify(repository).acknowledge(notice, "survival");
        verify(repository, never()).claim(eq("other"), anyString(), anyString());
        verify(repository, never()).release(any());
    }

    @Test
    void duplicateWakeUpAndJoinCannotShowSameInflightNoticeTwice() throws Exception {
        notices.wakeUp("player", notice.id());
        notices.wakeUp("player", notice.id());
        notices.completeJoin(player, List.of(notice));
        world.loop.drain();
        verify(repository, times(1)).claim(notice.id(), "player", "survival");
        verify(player.locale(), times(1)).send(anyString(), anyMap());
        doReturn(null).when(repository).claim(notice.id(), "player", "survival");
        notices.wakeUp("player", notice.id());
        world.loop.drain();
        verify(player.locale(), times(1)).send(anyString(), anyMap());
    }

    @Test
    void disconnectBetweenClaimAndShowReleasesAndNextServerCanDeliver() throws Exception {
        notices.wakeUp("player", notice.id());
        world.loop.worker();
        when(player.player.con.isConnected()).thenReturn(false);
        world.loop.main.runTurn();
        world.loop.worker();
        verify(repository).release(notice);
        verify(repository, never()).acknowledge(any(), anyString());
        verify(player.locale(), never()).send(anyString(), anyMap());
        when(player.player.con.isConnected()).thenReturn(true);
        world.config.server.name = "another-server";
        when(repository.claim(notice.id(), "player", "another-server")).thenReturn(notice);
        when(repository.acknowledge(notice, "another-server")).thenReturn(true);
        notices.completeJoin(player, List.of(notice));
        world.loop.drain();
        verify(player.locale()).send(anyString(), anyMap());
        verify(repository).acknowledge(notice, "another-server");
    }

    @Test
    void oldConnectionCannotDeliverIntoNewSessionWithSameUuid() throws Exception {
        notices.wakeUp("player", notice.id());
        world.loop.worker();
        world.session("player", 2, "new-address");
        world.loop.main.runTurn();
        world.loop.worker();
        verify(repository).release(notice);
        verify(player.locale(), never()).send(anyString(), anyMap());
    }

    @Test
    void claimedByAnotherServerAndOfflineWakeUpsAreSkipped() throws Exception {
        doReturn(null).when(repository).claim(notice.id(), "player", "survival");
        notices.wakeUp("player", notice.id());
        world.loop.drain();
        verify(player.locale(), never()).send(anyString(), anyMap());
        notices.wakeUp("unknown", notice.id());
        assertThat(world.loop.workers()).isZero();
        verify(repository, times(1)).claim(anyString(), anyString(), anyString());
    }

    @Test
    void expiredLeaseCannotShowAndReleaseKeepsItsOriginalToken() throws Exception {
        var expired = new CaseNotice(notice.id(), "player", "report_received", notice.params(), "old-token", Instant.EPOCH);
        doReturn(expired).when(repository).claim(notice.id(), "player", "survival");
        notices.wakeUp("player", notice.id());
        world.loop.drain();
        verify(player.locale(), never()).send(anyString(), anyMap());
        verify(repository).release(expired);
    }

    @Test
    void sendFailureReleasesRatherThanAcknowledging() throws Exception {
        doThrow(new IllegalStateException("connection lost")).when(player.localization).send(anyString(), anyMap());
        notices.wakeUp("player", notice.id());
        world.loop.drain();
        verify(repository).release(notice);
        verify(repository, never()).acknowledge(any(), anyString());
    }

    @Test
    void acknowledgementFailureDoesNotUndoDisplayAndLeavesDurableLeaseForRecovery() throws Exception {
        doThrow(new IllegalStateException("Mongo down")).when(repository).acknowledge(notice, "survival");
        notices.wakeUp("player", notice.id());
        world.loop.drain();
        verify(player.locale()).send(anyString(), anyMap());
        verify(repository, never()).release(any());
        // At-least-once recovery: after lease expiry a later join may display this again.
        notices.wakeUp("player", notice.id());
        world.loop.drain();
        verify(repository, times(2)).claim(anyString(), anyString(), anyString());
    }

    @Test
    void joinPreparationDoesNotClaimAndDatabaseFailureCannotBreakLogin() throws Exception {
        when(repository.findPending("player", 5)).thenReturn(List.of(notice));
        var prepared = world.loop.async.supply(() -> notices.prepareJoin("player"));
        world.loop.worker();
        assertThat(prepared.future().join()).containsExactly(notice);
        verify(repository, never()).claim(anyString(), anyString(), anyString());
        when(repository.findPending("player", 5)).thenThrow(new IllegalStateException("Mongo down"));
        var failed = world.loop.async.supply(() -> notices.prepareJoin("player"));
        world.loop.worker();
        assertThat(failed.future().join()).isEmpty();
    }

    @Test
    void disabledAndStoppedServiceHasNoRepositoryOrDisplayEffects() throws Exception {
        world.secrets.cases.reports.enabled = false;
        assertThat(notices.prepareJoin("player")).isEmpty();
        notices.completeJoin(player, List.of(notice));
        notices.wakeUp("player", notice.id());
        world.loop.drain();
        verifyNoInteractions(repository);
        world.secrets.cases.reports.enabled = true;
        notices.wakeUp("player", notice.id());
        world.loop.worker();
        notices.stop();
        world.loop.drain();
        verify(repository).release(notice);
        verify(player.locale(), never()).send(anyString(), anyMap());
    }

    @Test
    void noticeForAnotherPlayerCannotBeShownByJoin() {
        notices.completeJoin(player, List.of(new CaseNotice("other", "someone-else", "report_received", Map.of(), null, null)));
        verifyNoInteractions(repository);
    }
}
