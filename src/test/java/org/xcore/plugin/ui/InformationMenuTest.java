package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.menu.EventMenu;
import org.xcore.plugin.ui.menu.HelpMenu;
import org.xcore.plugin.ui.menu.InformationMenu;
import org.xcore.plugin.ui.menu.MapMenu;
import org.xcore.plugin.ui.menu.PlayerMenu;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class InformationMenuTest {

    private SessionService sessionService;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private InformationMenu informationMenu;
    private Session session;
    private TomlSecretsConfig secretsConfig;
    private Provider<MapMenu> map;
    private Provider<EventMenu> event;
    private Provider<HelpMenu> help;
    private Provider<PlayerMenu> player;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionService = mock(SessionService.class);
        gateway = mock(MindustryMenuGateway.class);
        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "xcore";
        secretsConfig = new TomlSecretsConfig();
        secretsConfig.externalLinks.discordUrl = "https://discord.example";
        secretsConfig.externalLinks.githubUrl = "https://github.example";
        secretsConfig.externalLinks.donatelloUrl = "https://donate.example";
        secretsConfig.externalLinks.weblateUrl = "https://translate.example";
        secretsConfig.externalLinks.discordRedVSBlueUrl = "https://rvb.example";

        BuildInfo buildInfo = new BuildInfo();
        buildInfo.setVersion("test-version");
        map = mock(Provider.class);
        event = mock(Provider.class);
        help = mock(Provider.class);
        player = mock(Provider.class);
        informationMenu = new InformationMenu(config, secretsConfig, sessionService, buildInfo, menuService, map, event, help, player);
        informationMenu.init();

        session = session();
        when(sessionService.get("viewer-1")).thenReturn(session);
    }

    @Test
    @DisplayName("main renders through route-backed flow runtime")
    void main_rendersThroughRouteBackedFlowRuntime() {
        informationMenu.main("viewer-1");

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().actionCount()).isEqualTo(5);
        verify(gateway).menu(eq(session.player), eq(0), eq("menu-main-title"), eq("menu-main-content"), any());
    }

    @Test
    @DisplayName("main opens information route from action")
    void main_opensInformationRouteFromAction() {
        informationMenu.main("viewer-1");

        menuService.onMenuOption(session, 0);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        assertThat(session.activeScreen().hasRoute()).isTrue();
        verify(gateway).menu(eq(session.player), eq(0), eq("menu-main-title"), eq("menu-main-content"), any());
        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("commands-info-title"), eq("commands-info-text"), any());
    }

    @Test
    @DisplayName("information back action reopens main route")
    void information_backActionReopensMainRoute() {
        informationMenu.main("viewer-1");
        menuService.onMenuOption(session, 0);

        menuService.onMenuOption(session, 5);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("menu-main-title"), eq("menu-main-content"), any());
    }

    @Test
    @DisplayName("information renders through flow runtime")
    void information_rendersThroughFlowRuntime() {
        informationMenu.information("viewer-1");

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        assertThat(session.activeScreen().actionCount()).isEqualTo(6);
        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("commands-info-title"), eq("commands-info-text"), any());
    }

    @Test
    @DisplayName("information includes back action only when history exists")
    void information_includesBackActionOnlyWhenHistoryExists() {
        session.pushHistory(() -> {});

        informationMenu.information("viewer-1");

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().actionCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("information back action runs previous menu")
    void information_backActionRunsPreviousMenu() {
        final boolean[] ran = {false};
        session.pushHistory(() -> ran[0] = true);
        informationMenu.information("viewer-1");

        menuService.onMenuOption(session, 5);

        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
        assertThat(ran[0]).isTrue();
    }

    @Test
    @DisplayName("main help action uses route history and opens help menu")
    void main_helpActionUsesRouteHistoryAndOpensHelpMenu() {
        HelpMenu helpMenu = mock(HelpMenu.class);
        when(help.get()).thenReturn(helpMenu);

        informationMenu.main("viewer-1");
        menuService.onMenuOption(session, 1);

        verify(helpMenu).help("viewer-1", 1);
        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
    }

    @Test
    @DisplayName("main maps action uses route history and opens maps menu")
    void main_mapsActionUsesRouteHistoryAndOpensMapsMenu() {
        MapMenu mapMenu = mock(MapMenu.class);
        when(map.get()).thenReturn(mapMenu);

        informationMenu.main("viewer-1");
        menuService.onMenuOption(session, 2);

        verify(mapMenu).maps("viewer-1", 1);
        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
    }

    @Test
    @DisplayName("main players action uses route history and opens players menu")
    void main_playersActionUsesRouteHistoryAndOpensPlayersMenu() {
        PlayerMenu playerMenu = mock(PlayerMenu.class);
        when(player.get()).thenReturn(playerMenu);

        informationMenu.main("viewer-1");
        menuService.onMenuOption(session, 3);

        verify(playerMenu).players("viewer-1", 1);
        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
    }

    @Test
    @DisplayName("main event main action uses route history and opens event main menu")
    void main_eventMainActionUsesRouteHistoryAndOpensEventMainMenu() {
        SessionService localSessionService = mock(SessionService.class);
        MindustryMenuGateway localGateway = mock(MindustryMenuGateway.class);
        Provider<SessionService> localSessionProvider = mock(Provider.class);
        when(localSessionProvider.get()).thenReturn(localSessionService);
        MenuService localMenuService = new MenuService(localSessionProvider, localGateway);
        Session localSession = session(localMenuService);
        when(localSessionService.get("viewer-1")).thenReturn(localSession);

        EventMenu eventMenu = mock(EventMenu.class);
        Provider<EventMenu> localEvent = mock(Provider.class);
        when(localEvent.get()).thenReturn(eventMenu);

        TomlXcoreConfig eventConfig = new TomlXcoreConfig();
        eventConfig.server.name = "event";
        BuildInfo buildInfo = new BuildInfo();
        buildInfo.setVersion("test-version");
        InformationMenu eventInformationMenu = new InformationMenu(eventConfig, secretsConfig, localSessionService, buildInfo, localMenuService, map, localEvent, help, player);
        eventInformationMenu.init();

        eventInformationMenu.main("viewer-1");
        localMenuService.onMenuOption(localSession, 4);

        verify(eventMenu).main("viewer-1");
        assertThat(localSession.hasHistory()).isFalse();
        assertThat(localSession.hasRouteHistory()).isTrue();
    }

    @Test
    @DisplayName("main event list action uses route history and opens event list menu")
    void main_eventListActionUsesRouteHistoryAndOpensEventListMenu() {
        SessionService localSessionService = mock(SessionService.class);
        MindustryMenuGateway localGateway = mock(MindustryMenuGateway.class);
        Provider<SessionService> localSessionProvider = mock(Provider.class);
        when(localSessionProvider.get()).thenReturn(localSessionService);
        MenuService localMenuService = new MenuService(localSessionProvider, localGateway);
        Session localSession = session(localMenuService);
        when(localSessionService.get("viewer-1")).thenReturn(localSession);

        EventMenu eventMenu = mock(EventMenu.class);
        Provider<EventMenu> localEvent = mock(Provider.class);
        when(localEvent.get()).thenReturn(eventMenu);

        TomlXcoreConfig eventConfig = new TomlXcoreConfig();
        eventConfig.server.name = "event";
        BuildInfo buildInfo = new BuildInfo();
        buildInfo.setVersion("test-version");
        InformationMenu eventInformationMenu = new InformationMenu(eventConfig, secretsConfig, localSessionService, buildInfo, localMenuService, map, localEvent, help, player);
        eventInformationMenu.init();

        eventInformationMenu.main("viewer-1");
        localMenuService.onMenuOption(localSession, 5);

        verify(eventMenu).events("viewer-1", 1);
        assertThat(localSession.hasHistory()).isFalse();
        assertThat(localSession.hasRouteHistory()).isTrue();
    }

    @Test
    @DisplayName("information close action clears active screen")
    void information_closeActionClearsActiveScreen() {
        informationMenu.information("viewer-1");

        menuService.onMenuOption(session, 5);

        assertThat(session.activeScreen()).isNull();
        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
    }

    @Test
    @DisplayName("information discord action routes through gateway openUri")
    void information_discordActionRoutesThroughGatewayOpenUri() {
        informationMenu.information("viewer-1");

        menuService.onMenuOption(session, 0);

        verify(gateway).openUri(eq(session.player), eq(secretsConfig.externalLinks.discordUrl));
    }

    private Session session() {
        return session(menuService);
    }

    private Session session(MenuService menuService) {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("viewer-1", true);
        data.uuid = "viewer-1";
        Session session = new Session(
                new TomlSecretsConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
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
}
