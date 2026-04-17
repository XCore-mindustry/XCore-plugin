package org.xcore.plugin.ui.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.TopMenuService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuBuilder;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopMenuTest {

    @Test
    @DisplayName("top hides previous on first cursor page and exposes next when available")
    void top_hidesPreviousOnFirstCursorPageAndExposesNextWhenAvailable() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, topMenuService, playerMenu);

        Session session = session("viewer-1");
        MenuBuilder builder = builder();
        when(session.builder()).thenReturn(builder);
        when(sessionService.get("viewer-1")).thenReturn(session);

        LeaderboardCursor nextCursor = new LeaderboardCursor(1400, 0, 10);
        TopMenuService.TopCursorPage topPage = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP,
                1,
                5,
                10,
                50,
                3,
                List.of(player("top-1", 10)),
                null,
                nextCursor,
                true
        );
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, null, 1, 10, session.data)).thenReturn(topPage);

        menu.top("viewer-1", TopCategory.MINI_PVP, 1);

        TopMenu.TopMenuState state = session.getDraft(TopMenu.TopMenuState.class);
        assertThat(state.currentPage).isEqualTo(1);
        assertThat(state.currentCursor).isNull();
        assertThat(state.nextCursor).isEqualTo(nextCursor);
        verify(builder).ifAddLocal(eq(false), eq("previous"), any(Runnable.class));
        verify(builder).ifAddLocal(eq(true), eq("next"), any(Runnable.class));
    }

    @Test
    @DisplayName("next from first page stores first-page marker and previous returns to page one")
    void nextFromFirstPage_storesMarkerAndPreviousReturnsToPageOne() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, topMenuService, playerMenu);

        Session session = session("viewer-1");
        MenuBuilder firstBuilder = builder();
        MenuBuilder secondBuilder = builder();
        MenuBuilder thirdBuilder = builder();
        when(session.builder()).thenReturn(firstBuilder, secondBuilder, thirdBuilder);
        when(sessionService.get("viewer-1")).thenReturn(session);

        LeaderboardCursor secondPageCursor = new LeaderboardCursor(1400, 0, 10);
        LeaderboardCursor thirdPageCursor = new LeaderboardCursor(1300, 0, 20);
        TopMenuService.TopCursorPage firstPage = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 1, 5, 10, 50, 3,
                List.of(player("top-1", 10)), null, secondPageCursor, true
        );
        TopMenuService.TopCursorPage secondPage = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 2, 5, 10, 50, 3,
                List.of(player("top-2", 20)), secondPageCursor, thirdPageCursor, true
        );

        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, null, 1, 10, session.data)).thenReturn(firstPage, firstPage);
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, secondPageCursor, 2, 10, session.data)).thenReturn(secondPage);

        AtomicReference<Runnable> firstNextAction = new AtomicReference<>();
        AtomicReference<Runnable> secondPreviousAction = new AtomicReference<>();
        captureIfAddLocal(firstBuilder, "next", firstNextAction, new AtomicReference<>());
        captureIfAddLocal(secondBuilder, "next", new AtomicReference<>(), secondPreviousAction);

        menu.top("viewer-1", TopCategory.MINI_PVP, 1);
        firstNextAction.get().run();

        TopMenu.TopMenuState state = session.getDraft(TopMenu.TopMenuState.class);
        assertThat(state.backStack).hasSize(1);
        assertThat(state.backStack.getLast().pid()).isEqualTo(-2);

        secondPreviousAction.get().run();

        assertThat(state.currentPage).isEqualTo(1);
        assertThat(state.currentCursor).isNull();
        verify(topMenuService, times(2)).loadCursorPage(TopCategory.MINI_PVP, null, 1, 10, session.data);
    }

    @Test
    @DisplayName("next action pushes current cursor to back stack and loads next cursor page")
    void nextAction_pushesCurrentCursorToBackStackAndLoadsNextCursorPage() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, topMenuService, playerMenu);

        Session session = session("viewer-1");
        MenuBuilder firstBuilder = builder();
        MenuBuilder secondBuilder = builder();
        when(session.builder()).thenReturn(firstBuilder, secondBuilder);
        when(sessionService.get("viewer-1")).thenReturn(session);

        LeaderboardCursor currentCursor = new LeaderboardCursor(1500, 0, 15);
        LeaderboardCursor nextCursor = new LeaderboardCursor(1450, 0, 25);
        TopMenuService.TopCursorPage currentPage = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 2, 5, 10, 50, 7,
                List.of(player("top-2", 15)), currentCursor, nextCursor, true
        );
        TopMenuService.TopCursorPage nextPage = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 3, 5, 10, 50, 7,
                List.of(player("top-3", 25)), nextCursor, null, false
        );

        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, null, 2, 10, session.data)).thenReturn(currentPage);
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, nextCursor, 3, 10, session.data)).thenReturn(nextPage);

        AtomicReference<Runnable> nextAction = new AtomicReference<>();
        captureIfAddLocal(firstBuilder, "next", nextAction, new AtomicReference<>());

        menu.top("viewer-1", TopCategory.MINI_PVP, 2);
        nextAction.get().run();

        TopMenu.TopMenuState state = session.getDraft(TopMenu.TopMenuState.class);
        assertThat(state.backStack).containsExactly(currentCursor);
        assertThat(state.currentCursor).isEqualTo(nextCursor);
        assertThat(state.currentPage).isEqualTo(3);
        verify(topMenuService).loadCursorPage(TopCategory.MINI_PVP, nextCursor, 3, 10, session.data);
    }

    @Test
    @DisplayName("previous action uses back stack cursor and decrements page")
    void previousAction_usesBackStackCursorAndDecrementsPage() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, topMenuService, playerMenu);

        Session session = session("viewer-1");
        MenuBuilder firstBuilder = builder();
        MenuBuilder secondBuilder = builder();
        MenuBuilder thirdBuilder = builder();
        when(session.builder()).thenReturn(firstBuilder, secondBuilder, thirdBuilder);
        when(sessionService.get("viewer-1")).thenReturn(session);

        LeaderboardCursor currentCursor = new LeaderboardCursor(1500, 0, 15);
        LeaderboardCursor nextCursor = new LeaderboardCursor(1450, 0, 25);
        LeaderboardCursor previousCursor = currentCursor;
        LeaderboardCursor thirdCursor = new LeaderboardCursor(1490, 0, 18);

        TopMenuService.TopCursorPage page2 = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 2, 5, 10, 50, 7,
                List.of(player("top-2", 15)), currentCursor, nextCursor, true
        );
        TopMenuService.TopCursorPage page3 = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 3, 5, 10, 50, 7,
                List.of(player("top-3", 25)), nextCursor, thirdCursor, true
        );
        TopMenuService.TopCursorPage backToPage2 = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 2, 5, 10, 50, 7,
                List.of(player("top-2", 15)), previousCursor, nextCursor, true
        );

        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, null, 2, 10, session.data)).thenReturn(page2);
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, nextCursor, 3, 10, session.data)).thenReturn(page3);
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, previousCursor, 2, 10, session.data)).thenReturn(backToPage2);

        AtomicReference<Runnable> firstNextAction = new AtomicReference<>();
        AtomicReference<Runnable> secondPreviousAction = new AtomicReference<>();
        captureIfAddLocal(firstBuilder, "next", firstNextAction, new AtomicReference<>());
        captureIfAddLocal(secondBuilder, "next", new AtomicReference<>(), secondPreviousAction);

        menu.top("viewer-1", TopCategory.MINI_PVP, 2);
        firstNextAction.get().run();
        secondPreviousAction.get().run();

        TopMenu.TopMenuState state = session.getDraft(TopMenu.TopMenuState.class);
        assertThat(state.currentCursor).isEqualTo(previousCursor);
        assertThat(state.currentPage).isEqualTo(2);
        assertThat(state.backStack).isEmpty();
        verify(topMenuService).loadCursorPage(TopCategory.MINI_PVP, previousCursor, 2, 10, session.data);
    }

    @Test
    @DisplayName("top resets cursor state when category changes")
    void top_resetsCursorStateWhenCategoryChanges() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, topMenuService, playerMenu);

        Session session = session("viewer-1");
        TopMenu.TopMenuState state = session.getDraft(TopMenu.TopMenuState.class);
        state.category = TopCategory.MINI_PVP;
        state.currentPage = 4;
        state.currentCursor = new LeaderboardCursor(1400, 0, 11);
        state.nextCursor = new LeaderboardCursor(1350, 0, 21);
        state.backStack.addLast(new LeaderboardCursor(1450, 0, 5));

        MenuBuilder builder = builder();
        when(session.builder()).thenReturn(builder);
        when(sessionService.get("viewer-1")).thenReturn(session);

        TopMenuService.TopCursorPage topPage = new TopMenuService.TopCursorPage(
                TopCategory.PLAYTIME, 1, 3, 10, 30, 2,
                List.of(player("top-1", 1)), null, new LeaderboardCursor(500, 0, 1), true
        );
        when(topMenuService.loadCursorPage(TopCategory.PLAYTIME, null, 1, 10, session.data)).thenReturn(topPage);

        menu.top("viewer-1", TopCategory.PLAYTIME, 1);

        assertThat(state.category).isEqualTo(TopCategory.PLAYTIME);
        assertThat(state.currentPage).isEqualTo(1);
        assertThat(state.backStack).isEmpty();
        assertThat(state.currentCursor).isNull();
    }

    @Test
    @DisplayName("player row action pushes history with current cursor and page before opening profile")
    void playerRowAction_pushesHistoryWithCurrentCursorAndPageBeforeOpeningProfile() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, topMenuService, playerMenu);

        Session session = session("viewer-1");
        MenuBuilder firstBuilder = builder();
        MenuBuilder secondBuilder = builder();
        when(session.builder()).thenReturn(firstBuilder, secondBuilder);
        when(sessionService.get("viewer-1")).thenReturn(session);

        PlayerData target = player("target-1", 14);
        LeaderboardCursor currentCursor = new LeaderboardCursor(1500, 0, 14);
        TopMenuService.TopCursorPage topPage = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 2, 5, 10, 50, 4,
                List.of(target), currentCursor, null, false
        );
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, null, 2, 10, session.data)).thenReturn(topPage);
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, currentCursor, 2, 10, session.data)).thenReturn(topPage);

        AtomicReference<Runnable> rowAction = new AtomicReference<>();
        doAnswer(invocation -> {
            rowAction.set(invocation.getArgument(1));
            return firstBuilder;
        }).when(firstBuilder).addRow(anyString(), any(Runnable.class));

        menu.top("viewer-1", TopCategory.MINI_PVP, 2);
        rowAction.get().run();

        ArgumentCaptor<Runnable> historyCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(session).pushHistory(historyCaptor.capture());
        verify(playerMenu).player("viewer-1", target);

        historyCaptor.getValue().run();
        verify(topMenuService, times(1)).loadCursorPage(TopCategory.MINI_PVP, currentCursor, 2, 10, session.data);
    }

    @Test
    @DisplayName("category history callback captures immutable page snapshot")
    void categoryHistoryCallback_capturesImmutablePageSnapshot() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, topMenuService, playerMenu);

        Session session = session("viewer-1");
        MenuBuilder firstBuilder = builder();
        MenuBuilder secondBuilder = builder();
        MenuBuilder thirdBuilder = builder();
        when(session.builder()).thenReturn(firstBuilder, secondBuilder, thirdBuilder);
        when(sessionService.get("viewer-1")).thenReturn(session);

        LeaderboardCursor miniCursor = new LeaderboardCursor(1500, 0, 15);
        LeaderboardCursor playtimeCursor = new LeaderboardCursor(500, 0, 4);
        TopMenuService.TopCursorPage miniPage = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 2, 5, 10, 50, 7,
                List.of(player("top-2", 15)), miniCursor, null, false
        );
        TopMenuService.TopCursorPage playtimePage = new TopMenuService.TopCursorPage(
                TopCategory.PLAYTIME, 1, 3, 10, 30, 2,
                List.of(player("top-1", 4)), playtimeCursor, null, false
        );

        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, null, 2, 10, session.data)).thenReturn(miniPage);
        when(topMenuService.loadCursorPage(TopCategory.PLAYTIME, null, 1, 10, session.data)).thenReturn(playtimePage);
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, miniCursor, 2, 10, session.data)).thenReturn(miniPage);

        AtomicReference<Runnable> categoryAction = new AtomicReference<>();
        doAnswer(invocation -> {
            String label = invocation.getArgument(0);
            Runnable action = invocation.getArgument(1);
            if ("top-menu-category-button".equals(label)) {
                categoryAction.set(action);
            }
            return firstBuilder;
        }).when(firstBuilder).add(anyString(), any(Runnable.class));

        menu.top("viewer-1", TopCategory.MINI_PVP, 2);
        categoryAction.get().run();

        ArgumentCaptor<Runnable> historyCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(session).pushHistory(historyCaptor.capture());

        menu.top("viewer-1", TopCategory.PLAYTIME, 1);
        historyCaptor.getValue().run();

        verify(topMenuService, times(1)).loadCursorPage(TopCategory.MINI_PVP, miniCursor, 2, 10, session.data);
        verify(topMenuService, times(1)).loadCursorPage(TopCategory.PLAYTIME, null, 1, 10, session.data);
    }

    private static void captureIfAddLocal(MenuBuilder builder,
                                          String labelToCaptureAsNext,
                                          AtomicReference<Runnable> nextAction,
                                          AtomicReference<Runnable> previousAction) {
        doAnswer(invocation -> {
            boolean visible = invocation.getArgument(0);
            String label = invocation.getArgument(1);
            Runnable action = invocation.getArgument(2);
            if (visible && label.equals(labelToCaptureAsNext)) {
                nextAction.set(action);
            }
            if (visible && label.equals("previous")) {
                previousAction.set(action);
            }
            return builder;
        }).when(builder).ifAddLocal(anyBoolean(), anyString(), any(Runnable.class));
    }

    private static Session session(String uuid) {
        Session session = mock(Session.class);
        session.data = player(uuid, 1);
        session.data.uuid = uuid;

        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        when(session.locale()).thenReturn(localization);

        TopMenu.TopMenuState state = new TopMenu.TopMenuState();
        when(session.getDraft(TopMenu.TopMenuState.class)).thenReturn(state);
        return session;
    }

    private static MenuBuilder builder() {
        MenuBuilder builder = mock(MenuBuilder.class, org.mockito.Mockito.RETURNS_SELF);
        when(builder.show()).thenReturn(true);
        return builder;
    }

    private static PlayerData player(String uuid, int pid) {
        PlayerData data = new PlayerData(uuid, true);
        data.uuid = uuid;
        data.pid = pid;
        data.nickname = uuid;
        return data;
    }
}
