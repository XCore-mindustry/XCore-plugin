package org.xcore.plugin.cases;

import mindustry.gen.Groups;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.integration.blockhistory.BlockHistorySummary;
import org.xcore.plugin.permission.RolesWorld;
import org.xcore.plugin.permission.role.PermissionSet;
import org.xcore.plugin.session.Session;
import org.xcore.protocol.generated.messages.cases.CasesMessages.CasesReportSubmittedV1;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    private final CasesWorld world = new CasesWorld();
    private final Session reporter = world.session("reporter-uuid", 1, "shared-address");
    private final Session target = world.session("target-uuid", 2, "shared-address");

    @BeforeEach
    void gameThread() {
        GameThread.setGameThreadOverride(Thread.currentThread());
    }

    @AfterEach
    void reset() {
        GameThread.setGameThreadOverride(null);
        Groups.clear();
    }

    @Test
    void capturesOnGameThreadAndPublishesOffItWithConfirmation() throws Exception {
        world.history.register((uuid, window) -> {
            assertThat(GameThread.isGameThread()).isTrue();
            assertThat(uuid).isEqualTo(target.data.uuid);
            return Optional.of(new BlockHistorySummary(window, 3, 2, 0, false, List.of(), null, 4));
        });
        world.chat.record(target.player, "destroyed", false);
        doAnswer(invocation -> {
            assertThat(GameThread.isGameThread()).isFalse();
            CasesReportSubmittedV1 message = invocation.getArgument(0);
            assertThat(message.sameNetwork()).isTrue();
            assertThat(message.reporter().ip()).isNull();
            assertThat(message.target().ip()).isNull();
            assertThat(message.reporterLocale()).isEqualTo("en");
            assertThat(message.chat()).hasSize(1);
            assertThat(message.blockSummary().built()).isEqualTo(3);
            assertThat(message.targetOnline()).isTrue();
            return null;
        }).when(world.network).postAwait(any());
        var result = world.reports.submit(reporter, CasesWorld.draft(reporter, target));
        assertThat(result.toCompletableFuture()).isNotDone();
        verifyNoInteractions(world.network);
        world.loop.worker();
        assertThat(result.toCompletableFuture()).isNotDone();
        result.thenAccept(value -> assertThat(GameThread.isGameThread()).isTrue());
        world.loop.main.runTurn();
        assertThat(result.toCompletableFuture().join()).isInstanceOf(ReportResult.Sent.class);
    }

    @Test
    void absentAndBrokenProvidersDoNotPreventPublishing() throws Exception {
        assertSent(world.reports.submit(reporter, CasesWorld.draft(reporter, target)));
        var first = org.mockito.ArgumentCaptor.forClass(CasesReportSubmittedV1.class);
        verify(world.network).postAwait(first.capture());
        assertThat(first.getValue().blockSummary()).isNull();
        world.secrets.cases.reports.cooldownSeconds = 0;
        world.secrets.cases.reports.sameTargetCooldownMinutes = 0;
        world.history.register((uuid, window) -> { throw new UnsatisfiedLinkError("native unavailable"); });
        assertSent(world.reports.submit(reporter, CasesWorld.draft(reporter, target)));
    }

    @Test
    void refusedPublishReturnsAllSlotsAndAllowsRetry() throws Exception {
        world.secrets.cases.reports.dailyLimit = 1;
        world.secrets.cases.reports.networkBurstLimit = 1;
        doThrow(new IllegalStateException("Redis unavailable")).doNothing().when(world.network).postAwait(any());
        var first = world.reports.submit(reporter, CasesWorld.draft(reporter, target));
        world.loop.drain();
        assertThat(first.toCompletableFuture().join()).isInstanceOf(ReportResult.Failed.class);
        assertSent(world.reports.submit(reporter, CasesWorld.draft(reporter, target)));
    }

    @Test
    void cooldownSameTargetAndDailyResultsAreReturnedWithoutPublishing() throws Exception {
        assertSent(world.reports.submit(reporter, CasesWorld.draft(reporter, target)));
        assertThat(world.reports.submit(reporter, CasesWorld.draft(reporter, target)).toCompletableFuture().join())
                .isInstanceOf(ReportResult.Cooldown.class);
        world.secrets.cases.reports.cooldownSeconds = 0;
        assertResult(new ReportResult.SameTarget());
        world.secrets.cases.reports.sameTargetCooldownMinutes = 0;
        world.secrets.cases.reports.dailyLimit = 1;
        assertResult(new ReportResult.DailyLimit());
        verify(world.network, times(1)).postAwait(any());
    }

    @Test
    void disabledFlagAndLocalKillSwitchPreventCaptureAndPublish() {
        world.secrets.cases.reports.enabled = false;
        assertResult(new ReportResult.Disabled());
        assertThat(world.reports.targets(reporter)).isEmpty();
        world.secrets.cases.reports.enabled = true;
        world.config.runtime.disabledFeatures.add("reports");
        assertResult(new ReportResult.Disabled());
        verifyNoInteractions(world.network);
    }

    @Test
    void rejectsSelfUnknownStaffForgedReporterAndInsufficientPlaytime() {
        assertThat(world.reports.submit(reporter, CasesWorld.draft(reporter, reporter)).toCompletableFuture().join())
                .isInstanceOf(ReportResult.InvalidTarget.class);
        RolesWorld.asAdmin(target, Instant.now());
        assertResult(new ReportResult.InvalidTarget());
        target.permissionSet = PermissionSet.EMPTY;
        world.secrets.cases.reports.minPlayTimeMinutes = 5;
        reporter.data.totalPlayTime = 2;
        assertResult(new ReportResult.InsufficientPlaytime(3));
        world.secrets.cases.reports.minPlayTimeMinutes = 0;
        var forged = new ReportDraft("forged", CasesWorld.target(target), ReportReason.GRIEFING, "");
        assertThat(world.reports.submit(reporter, forged).toCompletableFuture().join())
                .isInstanceOf(ReportResult.InvalidTarget.class);
        var unknown = new ReportDraft(reporter.data.uuid, new ReportTarget("unknown", 99, "Unknown", false, 0),
                ReportReason.OTHER, "");
        assertThat(world.reports.submit(reporter, unknown).toCompletableFuture().join())
                .isInstanceOf(ReportResult.InvalidTarget.class);
        verifyNoInteractions(world.network);
    }

    @Test
    void listsOnlineAndRecentTargetsWithoutReporterStaffOrAddresses() {
        var staff = world.session("staff", 3, "secret");
        RolesWorld.asAdmin(staff, Instant.now());
        world.recent.remember("left", 4, "Leaver", "hidden", false);
        world.recent.remember("left-staff", 5, "Staff", "hidden", true);
        assertThat(world.reports.targets(reporter)).extracting(ReportTarget::uuid).containsExactly(target.data.uuid, "left");
        assertThat(world.reports.targets(reporter).toString()).doesNotContain("shared-address", "hidden", "secret");
        assertThat(world.reports.reasons()).containsExactly(ReportReason.values());
    }

    @Test
    void rereadsTargetAndIgnoresStaleMenuSnapshot() throws Exception {
        var draft = CasesWorld.draft(reporter, target);
        when(target.player.plainName()).thenReturn("Renamed");
        when(target.player.ip()).thenReturn("different");
        assertSent(world.reports.submit(reporter, draft));
        var message = org.mockito.ArgumentCaptor.forClass(CasesReportSubmittedV1.class);
        verify(world.network).postAwait(message.capture());
        assertThat(message.getValue().target().playerName()).isEqualTo("Renamed");
        assertThat(message.getValue().sameNetwork()).isFalse();
    }

    @Test
    void preservesSignedPidsInTargetsReportsAndChatEvidence() throws Exception {
        reporter.data.pid = 0;
        target.data.pid = -12;
        world.chat.record(reporter.player, "context", false);
        world.chat.record(target.player, "incident", false);
        assertThat(world.reports.targets(reporter)).extracting(ReportTarget::pid).containsExactly(-12);

        assertSent(world.reports.submit(reporter, CasesWorld.draft(reporter, target)));

        var message = org.mockito.ArgumentCaptor.forClass(CasesReportSubmittedV1.class);
        verify(world.network).postAwait(message.capture());
        assertThat(message.getValue().reporter().playerPid()).isZero();
        assertThat(message.getValue().target().playerPid()).isEqualTo(-12);
        assertThat(message.getValue().chat()).extracting(line -> line.playerPid()).containsExactly(0, -12);
    }

    @Test
    void sendsReportOnRecentlyLeftPlayerWithLastAddressAndOfflineEvidence() throws Exception {
        world.chat.record(target.player, "before leaving", true);
        world.recent.left(target, false);
        Groups.player.remove(target.player);
        when(world.sessions.getActive(target.data.uuid)).thenReturn(null);
        assertSent(world.reports.submit(reporter, CasesWorld.draft(reporter, target)));
        var message = org.mockito.ArgumentCaptor.forClass(CasesReportSubmittedV1.class);
        verify(world.network).postAwait(message.capture());
        assertThat(message.getValue().targetOnline()).isFalse();
        assertThat(message.getValue().sameNetwork()).isTrue();
        assertThat(message.getValue().chat()).hasSize(1);
    }

    @Test
    void schedulingRejectionAndCaptureFailureReturnFailedAndRestoreAllowance() {
        doThrow(new java.util.concurrent.RejectedExecutionException()).when(world.loop.storage).supply(any());
        assertResult(new ReportResult.Failed());
        assertThat(world.limiter.reserve(reporter.data.uuid, target.data.uuid, "shared-address").refusal()).isNull();
    }

    @Test
    void shutdownPreventsQueuedPublishAndReleasesReservation() throws Exception {
        var result = world.reports.submit(reporter, CasesWorld.draft(reporter, target));
        world.reports.stop();
        world.loop.drain();
        assertThat(result.toCompletableFuture().join()).isInstanceOf(ReportResult.Failed.class);
        verifyNoInteractions(world.network);
        assertResult(new ReportResult.Disabled());
    }

    @Test
    void dispatcherFailureKeepsConfirmedAllowanceAndReturnsUnconfirmedAllowance() throws Exception {
        var unavailableMain = new org.xcore.plugin.concurrent.Async(world.loop.storage,
                task -> { throw new java.util.concurrent.RejectedExecutionException("main stopped"); });
        var reports = new ReportService(world.feature, world.secrets, world.config, world.sessions, world.recent,
                world.evidence, world.limiter, world.network, unavailableMain);
        var sent = reports.submit(reporter, CasesWorld.draft(reporter, target));
        world.loop.worker();
        assertThat(sent.toCompletableFuture()).isCompletedExceptionally();
        verify(world.network).postAwait(any());
        assertThat(world.limiter.reserve(reporter.data.uuid, target.data.uuid, "shared-address").refusal())
                .isInstanceOf(ReportResult.Cooldown.class);
        var other = world.session("other", 3, "different-address");
        doThrow(new IllegalStateException("Redis down")).when(world.network).postAwait(any());
        var failed = reports.submit(other, CasesWorld.draft(other, target));
        world.loop.worker();
        assertThat(failed.toCompletableFuture()).isCompletedExceptionally();
        assertThat(world.limiter.reserve(other.data.uuid, target.data.uuid, "different-address").refusal()).isNull();
    }

    private void assertSent(CompletionStage<ReportResult> result) throws Exception {
        world.loop.drain();
        assertThat(result.toCompletableFuture().join()).isInstanceOf(ReportResult.Sent.class);
    }

    private void assertResult(ReportResult expected) {
        assertThat(world.reports.submit(reporter, CasesWorld.draft(reporter, target)).toCompletableFuture().join())
                .isEqualTo(expected);
    }
}
