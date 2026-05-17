package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.TopMenuService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.ActiveMenuScreen;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.plugin.ui.menu.TopMenu;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TopMenuTest {

    private MindustryMenuGateway gateway;
    private MenuService menuService;

    @BeforeEach
    void setUp() {
        gateway = mock(MindustryMenuGateway.class);
        menuService = new MenuService(null, gateway);
    }

    @Test
    @DisplayName("top hides previous on first cursor page and exposes next when available")
    void top_hidesPreviousOnFirstCursorPageAndExposesNextWhenAvailable() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
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

        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(
                MenuRoute.of("top.list").withParam("category", "MINI_PVP").withParam("page", "1")
        );
        verify(gateway).followUpMenu(eq(session.player), eq(menuService.getMenuId()), any(), any(), any());

        TopMenu.TopMenuState state = session.getDraft(TopMenu.TopMenuState.class);
        assertThat(state.currentPage).isEqualTo(1);
        assertThat(state.currentCursor).isNull();
        assertThat(state.nextCursor).isEqualTo(nextCursor);

        ActiveMenuScreen screen = session.activeScreen();
        assertThat(screen).isNotNull();
        assertThat(screen.hasFlow()).isTrue();
        assertThat(optionIndexOf(screen, "previous")).isEqualTo(-1);
        assertThat(optionIndexOf(screen, "next")).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("next from first page stores first-page marker and previous returns to page one")
    void nextFromFirstPage_storesMarkerAndPreviousReturnsToPageOne() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
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

        menu.top("viewer-1", TopCategory.MINI_PVP, 1);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

        ActiveMenuScreen firstScreen = session.activeScreen();
        int nextIndex = optionIndexOf(firstScreen, "next");
        menuService.onMenuOption(session, nextIndex);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

        TopMenu.TopMenuState state = session.getDraft(TopMenu.TopMenuState.class);
        assertThat(state.backStack).hasSize(1);
        assertThat(state.backStack.getLast().pid()).isEqualTo(-2);

        ActiveMenuScreen secondScreen = session.activeScreen();
        int previousIndex = optionIndexOf(secondScreen, "previous");
        menuService.onMenuOption(session, previousIndex);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

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
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
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

        menu.top("viewer-1", TopCategory.MINI_PVP, 2);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

        ActiveMenuScreen screen = session.activeScreen();
        int nextIndex = optionIndexOf(screen, "next");
        menuService.onMenuOption(session, nextIndex);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

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
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
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

        menu.top("viewer-1", TopCategory.MINI_PVP, 2);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

        ActiveMenuScreen firstScreen = session.activeScreen();
        int nextIndex = optionIndexOf(firstScreen, "next");
        menuService.onMenuOption(session, nextIndex);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

        ActiveMenuScreen secondScreen = session.activeScreen();
        int previousIndex = optionIndexOf(secondScreen, "previous");
        menuService.onMenuOption(session, previousIndex);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

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
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
        TopMenu.TopMenuState state = session.getDraft(TopMenu.TopMenuState.class);
        state.category = TopCategory.MINI_PVP;
        state.currentPage = 4;
        state.currentCursor = new LeaderboardCursor(1400, 0, 11);
        state.nextCursor = new LeaderboardCursor(1350, 0, 21);
        state.backStack.addLast(new LeaderboardCursor(1450, 0, 5));

        when(sessionService.get("viewer-1")).thenReturn(session);

        TopMenuService.TopCursorPage topPage = new TopMenuService.TopCursorPage(
                TopCategory.PLAYTIME, 1, 3, 10, 30, 2,
                List.of(player("top-1", 1)), null, new LeaderboardCursor(500, 0, 1), true
        );
        when(topMenuService.loadCursorPage(TopCategory.PLAYTIME, null, 1, 10, session.data)).thenReturn(topPage);

        menu.top("viewer-1", TopCategory.PLAYTIME, 1);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

        assertThat(state.category).isEqualTo(TopCategory.PLAYTIME);
        assertThat(state.currentPage).isEqualTo(1);
        assertThat(state.backStack).isEmpty();
        assertThat(state.currentCursor).isNull();
    }

    @Test
    @DisplayName("player row action uses route history before opening profile")
    void playerRowAction_usesRouteHistoryBeforeOpeningProfile() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
        PlayerData target = player("target-1", 14);
        when(session.playerDataRepository.findByUuid("target-1")).thenReturn(target);
        when(sessionService.get("viewer-1")).thenReturn(session);

        LeaderboardCursor currentCursor = new LeaderboardCursor(1500, 0, 14);
        TopMenuService.TopCursorPage topPage = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 2, 5, 10, 50, 4,
                List.of(target), currentCursor, null, false
        );
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, null, 2, 10, session.data)).thenReturn(topPage);
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, currentCursor, 2, 10, session.data)).thenReturn(topPage);

        menu.top("viewer-1", TopCategory.MINI_PVP, 2);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

        ActiveMenuScreen screen = session.activeScreen();
        int profileIndex = optionIndexOf(screen, "profile:target-1");
        menuService.onMenuOption(session, profileIndex);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();

        InOrder inOrder = inOrder(gateway, playerMenu);
        inOrder.verify(gateway).hideFollowUpMenu(eq(session.player), eq(menuService.getMenuId()));
        inOrder.verify(playerMenu).player("viewer-1", target);

        menuService.goBack(session);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("top.list");
        verify(topMenuService).loadCursorPage(TopCategory.MINI_PVP, null, 2, 10, session.data);
        verify(topMenuService).loadCursorPage(TopCategory.MINI_PVP, currentCursor, 2, 10, session.data);
    }

    @Test
    @DisplayName("category history callback captures immutable page snapshot")
    void categoryHistoryCallback_capturesImmutablePageSnapshot() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
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

        menu.top("viewer-1", TopCategory.MINI_PVP, 2);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        assertThat(session.activeScreen().route().id()).isEqualTo("top.list");

        ActiveMenuScreen screen = session.activeScreen();
        int categoryIndex = optionIndexOf(screen, "category");
        menuService.onMenuOption(session, categoryIndex);

        InOrder inOrder = inOrder(gateway);
        inOrder.verify(gateway).hideFollowUpMenu(eq(session.player), eq(menuService.getMenuId()));
        inOrder.verify(gateway).menu(eq(session.player), eq(menuService.getMenuId()), any(), any(), any());

        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("top.categories");
        assertThat(session.hasHistory()).isTrue();
        Runnable historyCallback = session.popHistory();

        menu.top("viewer-1", TopCategory.PLAYTIME, 1);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

        historyCallback.run();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("top.list");

        verify(topMenuService, times(1)).loadCursorPage(TopCategory.MINI_PVP, miniCursor, 2, 10, session.data);
        verify(topMenuService, times(1)).loadCursorPage(TopCategory.PLAYTIME, null, 1, 10, session.data);
    }

    @Test
    @DisplayName("close action clears active screen")
    void closeAction_clearsActiveScreen() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
        when(sessionService.get("viewer-1")).thenReturn(session);

        TopMenuService.TopCursorPage topPage = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 1, 1, 10, 0, null,
                List.of(), null, null, false
        );
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, null, 1, 10, session.data)).thenReturn(topPage);

        menu.top("viewer-1", TopCategory.MINI_PVP, 1);

        ActiveMenuScreen screen = session.activeScreen();
        assertThat(screen).isNotNull();

        int closeIndex = optionIndexOf(screen, "close");
        menuService.onMenuOption(session, closeIndex);

        verify(gateway).hideFollowUpMenu(eq(session.player), eq(menuService.getMenuId()));
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("back action hides follow-up before restoring previous normal menu")
    void backAction_hidesFollowUpBeforeRestoringPreviousMenu() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
        when(sessionService.get("viewer-1")).thenReturn(session);

        Runnable previousMenu = mock(Runnable.class);
        session.pushHistory(previousMenu);

        TopMenuService.TopCursorPage topPage = new TopMenuService.TopCursorPage(
                TopCategory.MINI_PVP, 1, 1, 10, 0, null,
                List.of(), null, null, false
        );
        when(topMenuService.loadCursorPage(TopCategory.MINI_PVP, null, 1, 10, session.data)).thenReturn(topPage);

        menu.top("viewer-1", TopCategory.MINI_PVP, 1);
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);

        ActiveMenuScreen screen = session.activeScreen();
        int backIndex = optionIndexOf(screen, "back");
        menuService.onMenuOption(session, backIndex);

        InOrder inOrder = inOrder(gateway, previousMenu);
        inOrder.verify(gateway).hideFollowUpMenu(eq(session.player), eq(menuService.getMenuId()));
        inOrder.verify(previousMenu).run();
    }

    @Test
    @DisplayName("categories renders routed categories screen with route metadata")
    void categories_rendersRoutedCategoriesScreen() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
        when(sessionService.get("viewer-1")).thenReturn(session);
        when(topMenuService.resolveDefaultCategory()).thenReturn(TopCategory.MINI_PVP);
        TopMenuService.TopCursorPage hexedPage = new TopMenuService.TopCursorPage(
                TopCategory.HEXED, 1, 1, 10, 0, null,
                List.of(), null, null, false
        );
        when(topMenuService.loadCursorPage(TopCategory.HEXED, null, 1, 10, session.data)).thenReturn(hexedPage);

        menu.categories("viewer-1", TopCategory.PLAYTIME);

        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(
                MenuRoute.of("top.categories").withParam("category", "PLAYTIME")
        );
        verify(gateway).menu(eq(session.player), eq(menuService.getMenuId()), any(), any(), any());
    }

    @Test
    @DisplayName("categories selecting category renders top list route")
    void categories_selectingCategoryRendersTopListRoute() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
        when(sessionService.get("viewer-1")).thenReturn(session);
        when(topMenuService.resolveDefaultCategory()).thenReturn(TopCategory.MINI_PVP);
        TopMenuService.TopCursorPage hexedPage = new TopMenuService.TopCursorPage(
                TopCategory.HEXED, 1, 1, 10, 0, null,
                List.of(), null, null, false
        );
        when(topMenuService.loadCursorPage(TopCategory.HEXED, null, 1, 10, session.data)).thenReturn(hexedPage);

        menu.categories("viewer-1", TopCategory.PLAYTIME);

        ActiveMenuScreen screen = session.activeScreen();
        int hexedIndex = optionIndexOf(screen, "category:HEXED");
        menuService.onMenuOption(session, hexedIndex);

        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(
                MenuRoute.of("top.list").withParam("category", "HEXED").withParam("page", "1")
        );
    }

    @Test
    @DisplayName("categories use namespaced category action ids")
    void categories_useNamespacedCategoryActionIds() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
        when(sessionService.get("viewer-1")).thenReturn(session);
        when(topMenuService.resolveDefaultCategory()).thenReturn(TopCategory.MINI_PVP);

        menu.categories("viewer-1", TopCategory.PLAYTIME);

        ActiveMenuScreen screen = session.activeScreen();
        assertThat(optionIndexOf(screen, "category:MINI_PVP")).isGreaterThanOrEqualTo(0);
        assertThat(optionIndexOf(screen, "category:PLAYTIME")).isGreaterThanOrEqualTo(0);
        assertThat(optionIndexOf(screen, "category:HEXED")).isGreaterThanOrEqualTo(0);
        assertThat(optionIndexOf(screen, "HEXED")).isEqualTo(-1);
    }

    @Test
    @DisplayName("categories back button restores previous menu via goBack")
    void categories_backButtonRestoresPreviousMenu() {
        SessionService sessionService = mock(SessionService.class);
        TopMenuService topMenuService = mock(TopMenuService.class);
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        TopMenu menu = new TopMenu(new Config(), new GlobalConfig(), sessionService, menuService, topMenuService, playerMenu);
        menu.init();

        Session session = session("viewer-1");
        when(sessionService.get("viewer-1")).thenReturn(session);

        Runnable previousMenu = mock(Runnable.class);
        session.pushHistory(previousMenu);

        when(topMenuService.resolveDefaultCategory()).thenReturn(TopCategory.MINI_PVP);

        menu.categories("viewer-1", TopCategory.PLAYTIME);

        ActiveMenuScreen screen = session.activeScreen();
        int backIndex = optionIndexOf(screen, "back");
        menuService.onMenuOption(session, backIndex);

        verify(previousMenu).run();
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
        return session(uuid, menuService);
    }

    private static Session session(String uuid, MenuService menuService) {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = player(uuid, 1);
        data.uuid = uuid;

        PlayerDataRepository repository = mock(PlayerDataRepository.class);

        Session session = new Session(
                new GlobalConfig(),
                mock(Bundle.class),
                menuService,
                repository,
                player,
                data
        );

        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;

        return session;
    }

    private static PlayerData player(String uuid, int pid) {
        PlayerData data = new PlayerData(uuid, true);
        data.uuid = uuid;
        data.pid = pid;
        data.nickname = uuid;
        return data;
    }
}
