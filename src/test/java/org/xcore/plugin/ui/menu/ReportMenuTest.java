package org.xcore.plugin.ui.menu;

import mindustry.game.Team;
import mindustry.gen.Groups;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xcore.plugin.cases.CasesWorld;
import org.xcore.plugin.cases.ReportReason;
import org.xcore.plugin.cases.ReportResult;
import org.xcore.plugin.cases.ReportTarget;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.VField;
import org.xcore.ui.VNodes;
import org.xcore.ui.VButton;
import org.xcore.plugin.ui.kit.Kit;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportMenuTest {
    P3ReportUiFixture fixture;
    @BeforeEach void setUp() { fixture = new P3ReportUiFixture(); }
    @AfterEach void tearDown() { fixture.close(); GameThread.setGameThreadOverride(null); Groups.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"en", "ru", "uk_UA"})
    void allScreensAndOutcomesFitWithEightMaximumNamesAndExtremeValues(String language) {
        fixture.session.localization = LayoutAssert.localization(language.replace('_', '-'));
        var controller = new ReportUiController(fixture.reports, fixture.sessions, fixture.async, fixture.session);
        var targets = new ArrayList<ReportTarget>();
        Integer[] pids = {0, -12, Integer.MIN_VALUE + 1, Integer.MAX_VALUE, null, PlayerPids.NONE, 42, -1};
        for (int i = 0; i < 8; i++) targets.add(new ReportTarget("private-target-uuid-" + i, pids[i],
                "[red]" + "W".repeat(256), i < 4, i < 4 ? 0 : 1));
        var first = ReportUiController.Model.selection(targets, java.util.Collections.nCopies(8, "ffd37f"),
                List.of(ReportReason.values()), Long.MAX_VALUE);
        var reason = first.pick(2);
        var other = ReportUiController.transition(reason, new ReportUiController.Event.Reason(ReportReason.OTHER, "W".repeat(300))).model();
        var confirm = ReportUiController.transition(other, new ReportUiController.Event.Next(null)).model();
        var sending = ReportUiController.transition(confirm, new ReportUiController.Event.Send()).model();
        var invalid = ReportUiController.transition(first.pick(0), new ReportUiController.Event.Next(null)).model();
        var required = ReportUiController.transition(ReportUiController.transition(first.pick(0),
                new ReportUiController.Event.Reason(ReportReason.OTHER, "")).model(), new ReportUiController.Event.Next(null)).model();
        var models = new ArrayList<>(List.of(first, reason, other, confirm, sending, invalid, required));
        for (var result : outcomes()) models.add(sending.completed(result));
        for (var model : models) {
            for (Screen screen : Screen.ALL) {
                var window = controller.window(model, screen);
                LayoutAssert.assertLaidOut(window, screen);
                LayoutAssert.assertFitsPacket(window, language + " " + model.stage() + " " + screen);
            }
            var rendered = controller.render(model);
            LayoutAssert.assertFitsPacket(rendered, language + " " + model.stage());
            assertThat(LayoutAssert.dsl(rendered)).doesNotContain("private-target-uuid", "private-reporter-uuid", "field_comment:private");
            assertThat(LayoutAssert.allText(rendered)).doesNotContain("report-menu-", "report-reason-", "report-result-", "{ $", "map-ui-loading");
        }
        String text = LayoutAssert.allText(controller.render(first));
        assertThat(text).contains("#0", "#-12", "#-2147483647", "#2147483647").doesNotContain("#-2147483648");
        assertThat(VNodes.walk(controller.render(reason))).filteredOn(node -> node instanceof VField).hasSize(2)
                .allSatisfy(node -> assertThat(((VField) node).maxLength()).isEqualTo(300));
        for (Screen screen : Screen.ALL) {
            var window = controller.window(reason, screen);
            var fields = VNodes.collect(window, node -> node instanceof VField).stream()
                    .map(node -> (VField) node).toList();
            assertThat(fields).hasSize(1);
            assertThat(fields.getFirst().layout().height()).isEqualTo(Kit.FIELD_HEIGHT);
            var reasons = VNodes.collect(window,
                    node -> node instanceof VButton button && button.clicked().startsWith("action:reason:")).stream()
                    .map(node -> (VButton) node).toList();
            assertThat(reasons).hasSize(6).allSatisfy(button -> {
                assertThat(button.layout().height()).isEqualTo(Kit.BUTTON_HEIGHT);
                assertThat(button.layout().width()).isEqualTo(fields.getFirst().layout().width());
            });
        }
    }

    static List<ReportResult> outcomes() {
        return List.of(new ReportResult.Sent(), new ReportResult.Cooldown(Long.MAX_VALUE), new ReportResult.SameTarget(),
                new ReportResult.DailyLimit(), new ReportResult.InvalidTarget(), new ReportResult.InsufficientPlaytime(Long.MAX_VALUE),
                new ReportResult.Disabled(), new ReportResult.Failed());
    }

    @Test void opensFillScreenAndHandlesEmptyAndDisabledWithoutListing() {
        when(fixture.reports.targets(fixture.session)).thenReturn(List.of());
        fixture.open();
        assertThat(fixture.lastDsl).contains("Nobody to report", "Discord");
        assertThat(fixture.lastDsl).contains("condition: \"width >= 490\"", "condition: \"width < 490\"", "noBarPane");
        assertThat(LayoutAssert.actions(new ReportUiController(fixture.reports, fixture.sessions, fixture.async,
                fixture.session).render(fixture.model()))).containsOnly("action:close");
        verify(fixture.gateway).menuBuilder(eq(fixture.player), anyInt(), anyLong(), isNull(), eq(false), eq(true), eq(true), any());
        when(fixture.reports.enabled()).thenReturn(false);
        clearInvocations(fixture.reports);
        fixture.open();
        assertThat(fixture.model().result()).isEqualTo(new ReportResult.Disabled());
        verify(fixture.reports, never()).targets(any());
        verify(fixture.reports, never()).submit(any(), any());
    }

    @Test void directTargetsUseSignedPidsAndEligibleNamesWithoutGuessing() {
        var targets = List.of(new ReportTarget("hidden-a", 0, "Same Name", true, 0),
                new ReportTarget("hidden-b", -12, "Same Name", false, 1),
                new ReportTarget("hidden-c", null, "Third Player", true, 0),
                new ReportTarget("hidden-d", PlayerPids.NONE, "Unknown PID", true, 0));
        when(fixture.reports.targets(fixture.session)).thenReturn(targets);
        fixture.menu.open(fixture.session, "#0");
        assertThat(fixture.model().target()).isEqualTo(targets.get(0));
        fixture.menu.open(fixture.session, "#-12");
        assertThat(fixture.model().target()).isEqualTo(targets.get(1));
        for (String query : List.of("0", "+0", "#+0", "-12")) {
            fixture.menu.open(fixture.session, query);
            assertThat(fixture.model().target()).isEqualTo(targets.get(query.equals("-12") ? 1 : 0));
        }
        fixture.menu.open(fixture.session, "third player");
        assertThat(fixture.model().target()).isEqualTo(targets.get(2));
        fixture.menu.open(fixture.session, "Same Name");
        assertThat(fixture.model().stage()).isEqualTo(ReportUiController.Stage.SELECT);
        fixture.menu.open(fixture.session, "Player");
        assertThat(fixture.model().target()).isEqualTo(targets.get(2));
        for (String query : List.of("missing", "#-2147483648", "#999999999999", "hidden-c", "[red]")) {
            fixture.menu.open(fixture.session, query);
            assertThat(fixture.model().result()).isEqualTo(new ReportResult.InvalidTarget());
        }
        assertThat(ReportMenu.matching(targets, "Same")).containsExactly(0, 1);
        assertThat(ReportMenu.matching(targets, "  [red][] ")).isEmpty();
        assertThat(ReportMenu.matching(List.of(targets.getFirst()), "[red]")).isEmpty();
        when(fixture.reports.targets(fixture.session)).thenReturn(List.of(targets.getFirst(),
                new ReportTarget("another-hidden-uuid", 0, "Another player", true, 0)));
        fixture.menu.open(fixture.session, "#0");
        assertThat(fixture.model().stage()).isEqualTo(ReportUiController.Stage.SELECT);
        assertThat(fixture.model().target()).isNull();
        assertThat(fixture.lastDsl).contains("action:pick:0", "action:pick:1").doesNotContain("another-hidden-uuid");
    }

    @Test void unknownOrStaleSessionDoesNotOpen() {
        fixture.menu.open(null, null);
        when(fixture.sessions.getActive(fixture.player)).thenReturn(null);
        fixture.menu.open(fixture.session, null);
        verify(fixture.reports, never()).targets(any());
        assertThat(fixture.session.activeUiSession()).isNull();
    }

    @Test void realServiceEligibilityAndRecentLeaverSubmissionPublishOnce() throws Exception {
        GameThread.setGameThreadOverride(Thread.currentThread());
        var world = new CasesWorld();
        var online = world.session("online-private", -12, "private-address");
        when(online.player.team()).thenReturn(Team.sharded);
        var staff = world.session("staff-private", 2, "private-staff-address");
        staff.player.admin = true;
        when(fixture.player.plainName()).thenReturn("Reporter");
        Groups.player.add(fixture.player);
        world.recent.left(fixture.session, false);
        var leaver = world.session("left-private", 0, "private-leaver-address");
        when(leaver.player.plainName()).thenReturn("Leaver");
        world.recent.left(leaver, false);
        Groups.player.remove(leaver.player);
        when(leaver.player.con.isConnected()).thenReturn(false);
        var leftStaff = world.session("left-staff-private", 3, "staff-address");
        world.recent.left(leftStaff, true);
        Groups.player.remove(leftStaff.player);
        when(world.sessions.getActive(fixture.player)).thenReturn(fixture.session);
        var menu = new ReportMenu(world.reports, fixture.menus, world.sessions, fixture.async);
        menu.open(fixture.session, null); fixture.drain();
        assertThat(fixture.model().targets()).extracting(ReportTarget::uuid).containsExactly("online-private", "left-private");
        assertThat(fixture.lastDsl).contains("Leaver", "Left recently", "#0").doesNotContain(
                "staff-private", "left-private", "private-address", "reporter-address", "staff-address");
        fixture.click("action:pick:1"); fixture.click("action:reason:griefing"); fixture.click("action:next");
        fixture.click("action:send"); fixture.click("action:send");
        assertThat(fixture.model().stage()).isEqualTo(ReportUiController.Stage.SENDING);
        world.loop.drain(); fixture.drain();
        assertThat(fixture.model().result()).isEqualTo(new ReportResult.Sent());
        var published = org.mockito.ArgumentCaptor.forClass(org.xcore.protocol.generated.messages.cases.CasesMessages.CasesReportSubmittedV1.class);
        verify(world.network, times(1)).postAwait(published.capture());
        assertThat(published.getValue().targetOnline()).isFalse();
        assertThat(published.getValue().target().playerPid()).isZero();
    }
}
