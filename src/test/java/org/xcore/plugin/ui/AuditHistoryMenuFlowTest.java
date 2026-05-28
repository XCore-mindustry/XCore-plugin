package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.AuditRecordSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.Slice;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.ActiveMenuScreen;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.menu.AuditHistoryMenu;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditHistoryMenuFlowTest {

    private MindustryMenuGateway gateway;
    private MenuService menuService;

    @BeforeEach
    void setUp() {
        gateway = mock(MindustryMenuGateway.class);
        menuService = new MenuService(null, gateway);
    }

    @Test
    @DisplayName("history flow renders next when hasNext and hides previous on first page")
    void historyFlow_rendersNextWhenHasNextAndHidesPreviousOnFirstPage() {
        SessionService sessionService = mock(SessionService.class);
        AuditService auditService = mock(AuditService.class);
        AuditHistoryMenu menu = createMenu(sessionService, auditService);

        Session session = session("viewer-1");
        PlayerData target = playerData("target-1", 1);
        when(sessionService.get("viewer-1")).thenReturn(session);

        AuditCursor nextCursor = new AuditCursor(100L, "audit-2");
        AuditRecordSummary summary = new AuditRecordSummary(
                "audit-1", AuditAction.BAN, "Target", "Moderator", "Reason", null, null, 10L
        );
        when(auditService.findSummaryByTargetUuid("target-1", null, 10))
                .thenReturn(new Slice<>(List.of(summary), true, nextCursor));

        menu.history("viewer-1", target);

        AuditHistoryMenu.AuditHistoryState state = session.getDraft(AuditHistoryMenu.AuditHistoryState.class);
        assertThat(state.currentCursor).isNull();
        assertThat(state.nextCursor).isEqualTo(nextCursor);

        ActiveMenuScreen screen = session.activeScreen();
        assertThat(screen).isNotNull();
        assertThat(screen.hasFlow()).isTrue();
        assertThat(optionIndexOf(screen, "previous")).isEqualTo(-1);
        assertThat(optionIndexOf(screen, "next")).isGreaterThanOrEqualTo(0);
        assertThat(optionIndexOf(screen, "details:audit-1")).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("next action pushes first-page marker and loads next cursor page")
    void nextAction_pushesFirstPageMarkerAndLoadsNextCursorPage() {
        SessionService sessionService = mock(SessionService.class);
        AuditService auditService = mock(AuditService.class);
        AuditHistoryMenu menu = createMenu(sessionService, auditService);

        Session session = session("viewer-1");
        PlayerData target = playerData("target-1", 1);
        when(sessionService.get("viewer-1")).thenReturn(session);

        AuditCursor nextCursor = new AuditCursor(100L, "audit-2");
        AuditRecordSummary firstSummary = new AuditRecordSummary(
                "audit-3", AuditAction.BAN, "T", "A", "R", null, null, 20L
        );
        AuditRecordSummary nextSummary = new AuditRecordSummary(
                "audit-2", AuditAction.MUTE, "T", "A", "R", null, null, 10L
        );

        when(auditService.findSummaryByTargetUuid("target-1", null, 10))
                .thenReturn(new Slice<>(List.of(firstSummary), true, nextCursor));
        when(auditService.findSummaryByTargetUuid("target-1", nextCursor, 10))
                .thenReturn(new Slice<>(List.of(nextSummary), false, null));

        menu.history("viewer-1", target);

        int nextIndex = optionIndexOf(session.activeScreen(), "next");
        menuService.onMenuOption(session, nextIndex);

        AuditHistoryMenu.AuditHistoryState state = session.getDraft(AuditHistoryMenu.AuditHistoryState.class);
        assertThat(state.backStack).hasSize(1);
        assertThat(state.currentCursor).isEqualTo(nextCursor);
        verify(auditService).findSummaryByTargetUuid("target-1", nextCursor, 10);
    }

    @Test
    @DisplayName("fresh history open resets pagination state while keeping route metadata")
    void freshHistoryOpen_resetsPaginationStateWhileKeepingRouteMetadata() {
        SessionService sessionService = mock(SessionService.class);
        AuditService auditService = mock(AuditService.class);
        AuditHistoryMenu menu = createMenu(sessionService, auditService);

        Session session = session("viewer-1");
        PlayerData target = playerData("target-1", 1);
        when(sessionService.get("viewer-1")).thenReturn(session);

        AuditCursor nextCursor = new AuditCursor(100L, "audit-2");
        AuditRecordSummary firstSummary = new AuditRecordSummary(
                "audit-3", AuditAction.BAN, "T", "A", "R", null, null, 20L
        );
        AuditRecordSummary nextSummary = new AuditRecordSummary(
                "audit-2", AuditAction.MUTE, "T", "A", "R", null, null, 10L
        );

        when(auditService.findSummaryByTargetUuid("target-1", null, 10))
                .thenReturn(new Slice<>(List.of(firstSummary), true, nextCursor));
        when(auditService.findSummaryByTargetUuid("target-1", nextCursor, 10))
                .thenReturn(new Slice<>(List.of(nextSummary), false, null));

        menu.history("viewer-1", target);
        menuService.onMenuOption(session, optionIndexOf(session.activeScreen(), "next"));

        AuditHistoryMenu.AuditHistoryState pagedState = session.getDraft(AuditHistoryMenu.AuditHistoryState.class);
        assertThat(pagedState.currentCursor).isEqualTo(nextCursor);
        assertThat(pagedState.backStack).hasSize(1);

        menu.history("viewer-1", target);

        AuditHistoryMenu.AuditHistoryState resetState = session.getDraft(AuditHistoryMenu.AuditHistoryState.class);
        assertThat(resetState.currentCursor).isNull();
        assertThat(resetState.backStack).isEmpty();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("audit.history");
        verify(auditService, times(2)).findSummaryByTargetUuid("target-1", null, 10);
    }

    @Test
    @DisplayName("previous action restores first page cursor from marker")
    void previousAction_restoresFirstPageCursorFromMarker() {
        SessionService sessionService = mock(SessionService.class);
        AuditService auditService = mock(AuditService.class);
        AuditHistoryMenu menu = createMenu(sessionService, auditService);

        Session session = session("viewer-1");
        PlayerData target = playerData("target-1", 1);
        when(sessionService.get("viewer-1")).thenReturn(session);

        AuditCursor nextCursor = new AuditCursor(100L, "audit-2");
        AuditRecordSummary firstSummary = new AuditRecordSummary(
                "audit-3", AuditAction.BAN, "T", "A", "R", null, null, 20L
        );
        AuditRecordSummary nextSummary = new AuditRecordSummary(
                "audit-2", AuditAction.MUTE, "T", "A", "R", null, null, 10L
        );

        when(auditService.findSummaryByTargetUuid("target-1", null, 10))
                .thenReturn(new Slice<>(List.of(firstSummary), true, nextCursor));
        when(auditService.findSummaryByTargetUuid("target-1", nextCursor, 10))
                .thenReturn(new Slice<>(List.of(nextSummary), false, null));

        menu.history("viewer-1", target);

        menuService.onMenuOption(session, optionIndexOf(session.activeScreen(), "next"));
        menuService.onMenuOption(session, optionIndexOf(session.activeScreen(), "previous"));

        AuditHistoryMenu.AuditHistoryState state = session.getDraft(AuditHistoryMenu.AuditHistoryState.class);
        assertThat(state.currentCursor).isNull();
        assertThat(state.backStack).isEmpty();
        verify(auditService, times(2)).findSummaryByTargetUuid("target-1", null, 10);
    }

    @Test
    @DisplayName("details action opens details via follow-up menu and active screen mode FOLLOW_UP")
    void detailsAction_opensDetailsViaFollowUpMenu() {
        SessionService sessionService = mock(SessionService.class);
        AuditService auditService = mock(AuditService.class);
        AuditHistoryMenu menu = createMenu(sessionService, auditService);

        Session session = session("viewer-1");
        PlayerData target = playerData("target-1", 1);
        when(sessionService.get("viewer-1")).thenReturn(session);

        AuditRecordSummary summary = new AuditRecordSummary(
                "audit-3", AuditAction.BAN, "T", "A", "R", null, null, 20L
        );
        AuditRecord record = AuditRecord.builder()
                .auditId("audit-3")
                .action(AuditAction.BAN)
                .actor(AuditActor.builder().nameSnapshot("Actor").build())
                .reason("Reason")
                .build();

        when(auditService.findSummaryByTargetUuid("target-1", null, 10))
                .thenReturn(new Slice<>(List.of(summary), false, null));
        when(auditService.findByAuditId("audit-3")).thenReturn(Optional.of(record));

        menu.history("viewer-1", target);

        int detailsIndex = optionIndexOf(session.activeScreen(), "details:audit-3");
        menuService.onMenuOption(session, detailsIndex);

        assertThat(session.hasRouteHistory()).isTrue();
        ActiveMenuScreen detailsScreen = session.activeScreen();
        assertThat(detailsScreen).isNotNull();
        assertThat(detailsScreen.mode()).isEqualTo(MenuMode.FOLLOW_UP);
        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("audit-menu-details-title"), any(), any());
    }

    @Test
    @DisplayName("back from details hides follow-up and restores list via normal gateway menu")
    void backFromDetails_hidesFollowUpAndRestoresList() {
        SessionService sessionService = mock(SessionService.class);
        AuditService auditService = mock(AuditService.class);
        AuditHistoryMenu menu = createMenu(sessionService, auditService);

        Session session = session("viewer-1");
        PlayerData target = playerData("target-1", 1);
        when(sessionService.get("viewer-1")).thenReturn(session);

        AuditRecordSummary summary = new AuditRecordSummary(
                "audit-3", AuditAction.BAN, "T", "A", "R", null, null, 20L
        );
        AuditRecord record = AuditRecord.builder()
                .auditId("audit-3")
                .action(AuditAction.BAN)
                .actor(AuditActor.builder().nameSnapshot("Actor").build())
                .reason("Reason")
                .build();

        when(auditService.findSummaryByTargetUuid("target-1", null, 10))
                .thenReturn(new Slice<>(List.of(summary), false, null));
        when(auditService.findByAuditId("audit-3")).thenReturn(Optional.of(record));

        menu.history("viewer-1", target);

        int detailsIndex = optionIndexOf(session.activeScreen(), "details:audit-3");
        menuService.onMenuOption(session, detailsIndex);

        ActiveMenuScreen detailsScreen = session.activeScreen();
        assertThat(detailsScreen).isNotNull();
        assertThat(detailsScreen.mode()).isEqualTo(MenuMode.FOLLOW_UP);

        // Back is at index 0 when history exists
        menuService.onMenuOption(session, 0);

        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("audit-menu-details-title"), any(), any());
        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
        verify(gateway, times(2)).menu(eq(session.player), eq(0), any(), any(), any());
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
    }

    @Test
    @DisplayName("close from details hides follow-up and clears active screen")
    void closeFromDetails_hidesFollowUpAndClearsActiveScreen() {
        SessionService sessionService = mock(SessionService.class);
        AuditService auditService = mock(AuditService.class);
        AuditHistoryMenu menu = createMenu(sessionService, auditService);

        Session session = session("viewer-1");
        PlayerData target = playerData("target-1", 1);
        when(sessionService.get("viewer-1")).thenReturn(session);

        AuditRecordSummary summary = new AuditRecordSummary(
                "audit-3", AuditAction.BAN, "T", "A", "R", null, null, 20L
        );
        AuditRecord record = AuditRecord.builder()
                .auditId("audit-3")
                .action(AuditAction.BAN)
                .actor(AuditActor.builder().nameSnapshot("Actor").build())
                .reason("Reason")
                .build();

        when(auditService.findSummaryByTargetUuid("target-1", null, 10))
                .thenReturn(new Slice<>(List.of(summary), false, null));
        when(auditService.findByAuditId("audit-3")).thenReturn(Optional.of(record));

        menu.history("viewer-1", target);

        int detailsIndex = optionIndexOf(session.activeScreen(), "details:audit-3");
        menuService.onMenuOption(session, detailsIndex);

        // Close is at index 1 when history exists (back at 0)
        menuService.onMenuOption(session, 1);

        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("audit-menu-details-title"), any(), any());
        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("dismiss from details hides follow-up and clears active screen while preserving history")
    void dismissFromDetails_hidesFollowUpAndClearsActiveScreen() {
        SessionService sessionService = mock(SessionService.class);
        AuditService auditService = mock(AuditService.class);
        AuditHistoryMenu menu = createMenu(sessionService, auditService);

        Session session = session("viewer-1");
        PlayerData target = playerData("target-1", 1);
        when(sessionService.get("viewer-1")).thenReturn(session);

        AuditRecordSummary summary = new AuditRecordSummary(
                "audit-3", AuditAction.BAN, "T", "A", "R", null, null, 20L
        );
        AuditRecord record = AuditRecord.builder()
                .auditId("audit-3")
                .action(AuditAction.BAN)
                .actor(AuditActor.builder().nameSnapshot("Actor").build())
                .reason("Reason")
                .build();

        when(auditService.findSummaryByTargetUuid("target-1", null, 10))
                .thenReturn(new Slice<>(List.of(summary), false, null));
        when(auditService.findByAuditId("audit-3")).thenReturn(Optional.of(record));

        menu.history("viewer-1", target);

        int detailsIndex = optionIndexOf(session.activeScreen(), "details:audit-3");
        menuService.onMenuOption(session, detailsIndex);

        assertThat(session.hasRouteHistory()).isTrue();

        // Dismiss with option == -1
        menuService.onMenuOption(session, -1);

        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("audit-menu-details-title"), any(), any());
        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
        assertThat(session.activeScreen()).isNull();
        assertThat(session.hasRouteHistory()).isTrue();
    }

    @Test
    @DisplayName("close action clears active screen")
    void closeAction_clearsActiveScreen() {
        SessionService sessionService = mock(SessionService.class);
        AuditService auditService = mock(AuditService.class);
        AuditHistoryMenu menu = createMenu(sessionService, auditService);

        Session session = session("viewer-1");
        PlayerData target = playerData("target-1", 1);
        when(sessionService.get("viewer-1")).thenReturn(session);
        when(auditService.findSummaryByTargetUuid("target-1", null, 10))
                .thenReturn(new Slice<>(List.of(), false, null));

        menu.history("viewer-1", target);

        assertThat(session.activeScreen()).isNotNull();
        menuService.onMenuOption(session, optionIndexOf(session.activeScreen(), "close"));

        assertThat(session.activeScreen()).isNull();
    }

    private AuditHistoryMenu createMenu(SessionService sessionService, AuditService auditService) {
        var menu = new AuditHistoryMenu(new GlobalConfig(), sessionService, auditService, menuService);
        menu.init();
        return menu;
    }

    private static int optionIndexOf(ActiveMenuScreen screen, String actionId) {
        if (screen == null) return -1;
        for (int i = 0; i < screen.actionCount(); i++) {
            if (actionId.equals(screen.actionIdAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private Session session(String uuid) {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = playerData(uuid, 1);
        data.uuid = uuid;

        Session session = new Session(
                new GlobalConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );

        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;

        return session;
    }

    private static PlayerData playerData(String uuid, int pid) {
        PlayerData data = new PlayerData(uuid, true);
        data.uuid = uuid;
        data.pid = pid;
        data.nickname = uuid;
        return data;
    }
}
