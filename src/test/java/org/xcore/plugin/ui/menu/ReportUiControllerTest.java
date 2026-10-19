package org.xcore.plugin.ui.menu;

import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.xcore.plugin.cases.ReportDraft;
import org.xcore.plugin.cases.ReportReason;
import org.xcore.plugin.cases.ReportResult;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.ui.runtime.UiSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.xcore.plugin.ui.menu.ReportUiController.Stage;

class ReportUiControllerTest {
    P3ReportUiFixture fixture;
    @BeforeEach void setUp() { fixture = new P3ReportUiFixture(); }
    @AfterEach void tearDown() { fixture.close(); }

    @Test void headlessPagingSelectionReasonCommentConfirmationAndBack() {
        fixture.open();
        assertThat(fixture.model().stage()).isEqualTo(Stage.SELECT);
        assertThat(fixture.lastDsl).contains("action:pick:7", "action:page:2").doesNotContain("action:pick:8");
        fixture.click("action:page:2");
        assertThat(fixture.model().page()).isEqualTo(2);
        assertThat(fixture.lastDsl).contains("action:pick:8", "action:page:1").doesNotContain("action:pick:0");
        fixture.click("action:pick:8");
        assertThat(fixture.model().target().uuid()).isEqualTo("private-target-uuid-8");
        fixture.click("action:reason:other", "Optional comment");
        assertThat(fixture.model().comment()).isEqualTo("Optional comment");
        fixture.click("action:reason:toxicity");
        assertThat(fixture.model().comment()).isEqualTo("Optional comment");
        fixture.click("action:next", "[red]literal comment");
        assertThat(fixture.model().stage()).isEqualTo(Stage.CONFIRM);
        assertThat(fixture.lastDsl).contains("[[red]literal comment", "Recent chat and building history", "False reports");
        fixture.click("action:back");
        assertThat(fixture.model().stage()).isEqualTo(Stage.REASON);
        assertThat(fixture.model().reason()).isEqualTo(ReportReason.TOXICITY);
        assertThat(fixture.model().comment()).isEqualTo("[red]literal comment");
        fixture.click("action:back", "Updated draft");
        assertThat(fixture.model().stage()).isEqualTo(Stage.SELECT);
        assertThat(fixture.model().page()).isEqualTo(2);
        fixture.click("action:page:1"); fixture.click("action:pick:0");
        assertThat(fixture.model().reason()).isNull();
        assertThat(fixture.model().comment()).isEmpty();
        verify(fixture.reports, never()).submit(any(), any());
    }

    @Test void otherRequiresNonblankCommentAndReasonIsRequired() {
        fixture.open(); fixture.click("action:pick:0"); fixture.click("action:next", "Comment");
        assertThat(fixture.model().stage()).isEqualTo(Stage.REASON);
        assertThat(fixture.model().validation()).isEqualTo("report-menu-pick-reason");
        fixture.click("action:reason:other", " \t "); fixture.click("action:next");
        assertThat(fixture.model().stage()).isEqualTo(Stage.REASON);
        assertThat(fixture.lastDsl).contains("A comment is required");
        fixture.click("action:next", "An explanation");
        assertThat(fixture.model().stage()).isEqualTo(Stage.CONFIRM);
        verify(fixture.reports, never()).submit(any(), any());
    }

    @Test void commentIsCappedServerSideAndPreservedOnEveryReasonClick() {
        fixture.open(); fixture.click("action:pick:0"); fixture.click("action:reason:other", "a".repeat(999));
        assertThat(fixture.model().comment()).hasSize(300);
        for (ReportReason reason : ReportReason.values()) {
            fixture.click("action:reason:" + reason.code());
            assertThat(fixture.model().reason()).isEqualTo(reason);
            assertThat(fixture.model().comment()).hasSize(300);
        }
        fixture.click("action:next", "b".repeat(500));
        assertThat(fixture.model().comment()).isEqualTo("b".repeat(300));
        fixture.click("action:back"); fixture.click("action:reason:griefing", ""); fixture.click("action:next");
        assertThat(fixture.model().stage()).isEqualTo(Stage.CONFIRM);
        assertThat(fixture.model().comment()).isEmpty();
    }

    @Test void doubleClickSubmitsOnceAndImmediateCompletionDoesNotLoseResult() {
        when(fixture.reports.submit(any(), any())).thenReturn(CompletableFuture.completedFuture(new ReportResult.Sent()));
        fixture.confirm();
        var ui = fixture.ui();
        // Two packets with the same display token before the sending window reaches the client.
        MenuResult send = new MenuResult("action:send"); send.token = ui.token();
        ui.handle(send); ui.handle(send);
        assertThat(ui.model().stage()).isEqualTo(Stage.SENDING);
        var sending = ui.model();
        // The reducer must also reject a second send independently of stale-token filtering.
        ui.dispatch(new ReportUiController.Event.Send());
        assertThat(ui.model()).isSameAs(sending);
        verify(fixture.reports, never()).submit(any(), any());
        fixture.drain();
        assertThat(fixture.model().result()).isEqualTo(new ReportResult.Sent());
        var draft = org.mockito.ArgumentCaptor.forClass(ReportDraft.class);
        verify(fixture.reports, times(1)).submit(eq(fixture.session), draft.capture());
        assertThat(draft.getValue().reporterUuid()).isEqualTo("private-reporter-uuid");
        assertThat(draft.getValue().target().pid()).isZero();
        assertThat(draft.getValue().reason()).isEqualTo(ReportReason.GRIEFING);
        assertThat(draft.getValue().comment()).isEqualTo("Broken core");
        assertThat(fixture.lastDsl).contains("Report sent").doesNotContain("private-reporter-uuid", "private-target-uuid");
        fixture.click("action:send");
        verify(fixture.reports, times(1)).submit(any(), any());
        fixture.click("action:close");
        assertThat(fixture.session.activeUiSession()).isNull();
        assertThat(fixture.loop.client().isVisible(fixture.menus.getMenuBuilderId())).isFalse();
    }

    static List<Arguments> results() {
        return List.of(
                Arguments.of(new ReportResult.Sent(), "Report sent. Thank you!"),
                Arguments.of(new ReportResult.Cooldown(123), "Wait [accent]123 s[scarlet] before the next report."),
                Arguments.of(new ReportResult.SameTarget(), "You have already reported this player recently."),
                Arguments.of(new ReportResult.DailyLimit(), "Daily report limit reached. Try again tomorrow."),
                Arguments.of(new ReportResult.InvalidTarget(), "This player cannot be reported."),
                Arguments.of(new ReportResult.InsufficientPlaytime(456), "Play for another [accent]456 min[scarlet] before sending reports."),
                Arguments.of(new ReportResult.Disabled(), "Reports are disabled on this server."),
                Arguments.of(new ReportResult.Failed(), "Could not send the report. Try again."));
    }
    @ParameterizedTest @MethodSource("results")
    void headlessTypedResultsAreDeliveredOnGameQueue(ReportResult outcome, String feedback) {
        var pending = new CompletableFuture<ReportResult>();
        when(fixture.reports.submit(any(), any())).thenReturn(pending);
        fixture.confirm(); fixture.click("action:send");
        assertThat(fixture.model().stage()).isEqualTo(Stage.SENDING);
        assertThat(fixture.lastDsl).contains("Loading...").doesNotContain("action:send", "action:back");
        pending.complete(outcome);
        assertThat(fixture.model().stage()).isEqualTo(Stage.SENDING);
        fixture.drain();
        assertThat(fixture.model().stage()).isEqualTo(Stage.RESULT);
        assertThat(fixture.model().result()).isEqualTo(outcome);
        assertThat(fixture.lastDsl).contains(feedback);
        assertThat(LayoutAssert.actions(new ReportUiController(fixture.reports, fixture.sessions, fixture.async,
                fixture.session).render(fixture.model()))).containsOnly("action:close");
    }

    @Test void exceptionalAsyncCompletionAndSynchronousFailureShowFailed() throws Exception {
        var pending = new CompletableFuture<ReportResult>();
        when(fixture.reports.submit(any(), any())).thenReturn(pending);
        fixture.confirm(); fixture.click("action:send");
        Thread worker = new Thread(() -> pending.completeExceptionally(new IllegalStateException("private backend details")));
        worker.start(); worker.join(5000);
        assertThat(worker.isAlive()).isFalse();
        assertThat(fixture.model().stage()).isEqualTo(Stage.SENDING);
        fixture.drain();
        assertThat(fixture.model().result()).isEqualTo(new ReportResult.Failed());
        assertThat(fixture.lastDsl).doesNotContain("private backend details");
        when(fixture.reports.submit(any(), any())).thenThrow(new IllegalStateException("capture failed"));
        fixture.confirm(); fixture.click("action:send");
        assertThat(fixture.model().result()).isEqualTo(new ReportResult.Failed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"disconnected", "removed", "reconnected", "connection", "session", "identity", "version", "navigation", "close"})
    void lateResultsCannotReplaceDisconnectedOrNewerScreen(String change) {
        var pending = new CompletableFuture<ReportResult>();
        when(fixture.reports.submit(any(), any())).thenReturn(pending);
        fixture.confirm(); fixture.click("action:send");
        UiSession<ReportUiController.Model, ReportUiController.Event> original = fixture.ui();
        switch (change) {
            case "disconnected" -> when(fixture.player.con.isConnected()).thenReturn(false);
            case "removed" -> when(fixture.player.isAdded()).thenReturn(false);
            case "reconnected" -> fixture.session.player = mock(Player.class);
            case "connection" -> fixture.player.con = mock(NetConnection.class);
            case "session" -> when(fixture.sessions.getActive(fixture.player)).thenReturn(mock(Session.class));
            case "identity" -> fixture.session.data.uuid = "new-private-uuid";
            case "version" -> fixture.session.nextUiVersion();
            case "navigation" -> fixture.open();
            case "close" -> fixture.click("action:close");
        }
        long sends = fixture.shows();
        pending.complete(new ReportResult.Sent()); fixture.drain();
        assertThat(fixture.shows()).isEqualTo(sends);
        assertThat(original.model().stage()).isIn(Stage.SENDING, Stage.CLOSED);
        if (change.equals("navigation")) assertThat(fixture.model().stage()).isEqualTo(Stage.SELECT);
        if (change.equals("close")) assertThat(fixture.session.activeUiSession()).isNull();
    }

    @Test void closeBeforeQueuedEffectPreventsPublishingAndCompletionBeforeNavigationIsIgnored() {
        fixture.confirm();
        fixture.ui().handle(new MenuResult("action:send"));
        fixture.ui().handle(new MenuResult("action:close"));
        fixture.drain();
        verify(fixture.reports, never()).submit(any(), any());
        var pending = new CompletableFuture<ReportResult>();
        when(fixture.reports.submit(any(), any())).thenReturn(pending);
        fixture.confirm(); fixture.click("action:send");
        pending.complete(new ReportResult.Failed());
        fixture.menu.open(fixture.session, null); fixture.drain();
        assertThat(fixture.model().stage()).isEqualTo(Stage.SELECT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disconnected", "removed", "reconnected", "connection", "session", "identity", "version", "navigation"})
    void staleSessionBeforeQueuedSubmissionDoesNotPublish(String change) {
        fixture.confirm();
        var original = fixture.ui();
        original.handle(new MenuResult("action:send"));
        assertThat(original.model().stage()).isEqualTo(Stage.SENDING);
        switch (change) {
            case "disconnected" -> when(fixture.player.con.isConnected()).thenReturn(false);
            case "removed" -> when(fixture.player.isAdded()).thenReturn(false);
            case "reconnected" -> fixture.session.player = mock(Player.class);
            case "connection" -> fixture.player.con = mock(NetConnection.class);
            case "session" -> when(fixture.sessions.getActive(fixture.player)).thenReturn(mock(Session.class));
            case "identity" -> fixture.session.data.uuid = "new-private-uuid";
            case "version" -> fixture.session.nextUiVersion();
            case "navigation" -> fixture.menu.open(fixture.session, null);
        }
        fixture.drain();
        verify(fixture.reports, never()).submit(any(), any());
        assertThat(original.model().stage()).isEqualTo(Stage.SENDING);
    }

    @Test void nullCompletionShowsFailedAndCannotSendAgain() {
        when(fixture.reports.submit(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        fixture.confirm(); fixture.click("action:send");
        assertThat(fixture.model().result()).isEqualTo(new ReportResult.Failed());
        assertThat(fixture.lastDsl).contains("Could not send the report");
        var result = fixture.model();
        fixture.click("action:send");
        assertThat(fixture.model()).isSameAs(result);
        verify(fixture.reports, times(1)).submit(any(), any());
    }

    @Test void commentLimitDoesNotSplitSurrogatePair() {
        fixture.open(); fixture.click("action:pick:0");
        fixture.click("action:reason:other", "a".repeat(299) + "\uD83D\uDE00");
        assertThat(fixture.model().comment()).isEqualTo("a".repeat(299));
    }

    @Test void pureTransitionsIgnoreWrongStageAndInvalidActionsAndKeepSnapshotsImmutable() {
        var candidates = new ArrayList<>(P3ReportUiFixture.targets(9));
        var selection = ReportUiController.Model.selection(candidates, List.of(), List.of(ReportReason.values()), 123L);
        candidates.clear();
        assertThat(selection.targets()).hasSize(9);
        for (var event : List.of(new ReportUiController.Event.Pick(-1), new ReportUiController.Event.Pick(8),
                new ReportUiController.Event.Pick(999), new ReportUiController.Event.Page(0), new ReportUiController.Event.Page(3),
                new ReportUiController.Event.Send(), new ReportUiController.Event.Next(null), new ReportUiController.Event.Back(null),
                new ReportUiController.Event.Completed(new ReportResult.Sent()))) {
            assertThat(ReportUiController.transition(selection, event).model()).isSameAs(selection);
        }
        var closed = ReportUiController.transition(selection, new ReportUiController.Event.Close()).model();
        assertThat(ReportUiController.transition(closed, new ReportUiController.Event.Pick(0)).model()).isSameAs(closed);
        verifyNoInteractions(fixture.reports);
    }

    @Test void malformedRoutesAreIgnoredAndCancellationCloses() {
        var controller = new ReportUiController(fixture.reports, fixture.sessions, fixture.async, fixture.session);
        assertThat(controller.parseEvent(new MenuResult("action:pick:2"))).isEqualTo(new ReportUiController.Event.Pick(2));
        assertThat(controller.parseEvent(new MenuResult("action:page:2"))).isEqualTo(new ReportUiController.Event.Page(2));
        for (String route : List.of("action:pick:uuid", "action:pick:999999999999", "action:page:bad", "action:reason:unknown", "action:none")) {
            assertThat(controller.parseEvent(new MenuResult(route))).isNull();
        }
        assertThat(controller.parseEvent(null)).isEqualTo(new ReportUiController.Event.Close());
        fixture.open();
        fixture.loop.client().dismiss(fixture.menus.getMenuBuilderId()); fixture.drain();
        assertThat(fixture.session.activeUiSession()).isNull();
    }
}
