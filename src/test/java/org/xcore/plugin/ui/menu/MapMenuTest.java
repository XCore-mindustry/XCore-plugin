package org.xcore.plugin.ui;

import arc.files.Fi;
import arc.struct.Seq;
import arc.struct.StringMap;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.core.GameState;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.MindustryMenuGateway;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.menu.EventMenu;
import org.xcore.plugin.ui.menu.MapMenu;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.service.EventEditorService;
import org.xcore.plugin.service.EventService;
import org.xcore.plugin.service.EventViewService;
import org.xcore.plugin.vote.VoteService;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MapMenuTest {

    private SessionService sessionService;
    private MapDataRepository mapDataRepository;
    private EventDataRepository eventDataRepository;
    private MapService mapService;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private MapMenu mapMenu;
    private Session session;
    private GlobalConfig globalConfig;
    private GameState originalState;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionService = mock(SessionService.class);
        mapDataRepository = mock(MapDataRepository.class);
        eventDataRepository = mock(EventDataRepository.class);
        mapService = mock(MapService.class);
        gateway = mock(MindustryMenuGateway.class);

        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        Config config = new Config();
        globalConfig = new GlobalConfig();
        globalConfig.mapsPerPage = 2;

        Provider<EventMenu> eventMenu = mock(Provider.class);

        mapMenu = new MapMenu(config, globalConfig, sessionService,
                mapDataRepository, eventDataRepository, mapService, eventMenu, menuService);
        mapMenu.init();

        originalState = mindustry.Vars.state;
        mindustry.Vars.state = new GameState();
        mindustry.game.Rules rules = mock(mindustry.game.Rules.class);
        when(rules.mode()).thenReturn(Gamemode.pvp);
        mindustry.Vars.state.rules = rules;

        session = session();
        when(sessionService.get("viewer-1")).thenReturn(session);
    }

    @AfterEach
    void tearDown() {
        mindustry.Vars.state = originalState;
    }

    @Test
    @DisplayName("maps renders routed screen with route metadata")
    void maps_rendersRoutedScreenWithRouteMetadata() {
        when(mapService.getAvailableMaps()).thenReturn(Seq.with(
                createMap("Map1"), createMap("Map2")
        ));

        mapMenu.maps("viewer-1", 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("map.maps");
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        verify(gateway).menu(eq(session.player), eq(0), eq("commands-maps-title"), any(), any());
    }

    @Test
    @DisplayName("maps pagination next and previous transitions pages")
    void maps_pagination_nextAndPreviousTransitionsPages() {
        when(mapService.getAvailableMaps()).thenReturn(Seq.with(
                createMap("Map1"), createMap("Map2"), createMap("Map3")
        ));

        mapMenu.maps("viewer-1", 1);

        assertThat(session.activeScreen().route().intParam("page", 0)).isEqualTo(1);

        menuService.onMenuOption(session, 2); // next after map rows

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route().intParam("page", 0)).isEqualTo(2);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("commands-maps-title"), any(), any());

        menuService.onMenuOption(session, 1); // previous after map row

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route().intParam("page", 0)).isEqualTo(1);
        verify(gateway, times(3)).menu(eq(session.player), eq(0), eq("commands-maps-title"), any(), any());
    }

    @Test
    @DisplayName("maps selecting a map opens legacy map detail and pushes history")
    void maps_selectingMap_opensRoutedMapDetailViaRouteHistory() {
        when(mapService.getAvailableMaps()).thenReturn(Seq.with(createMap("Map1")));
        MapData mapData = new MapData("Map1", "Map1.msav", "author", "pvp");
        mapData.id = new org.bson.types.ObjectId();
        when(mapDataRepository.findOrCreate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mapData);
        when(mapDataRepository.findById(mapData.id)).thenReturn(mapData);
        when(mapService.findPersistedMap(any())).thenReturn(null);

        mapMenu.maps("viewer-1", 1);

        menuService.onMenuOption(session, 0); // select map

        verify(mapDataRepository).findOrCreate(anyString(), anyString(), anyString(), anyString());
        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(
                MenuRoute.of("map.details").withParam("mapId", mapData.id.toHexString())
        );
    }

    @Test
    @DisplayName("map renders routed screen with route metadata")
    void map_rendersRoutedScreenWithRouteMetadata() {
        MapData mapData = new MapData("Map1", "Map1.msav", "author", "pvp");
        mapData.id = new org.bson.types.ObjectId();
        when(mapDataRepository.findById(mapData.id)).thenReturn(mapData);
        when(mapService.findPersistedMap(any())).thenReturn(null);

        mapMenu.map("viewer-1", mapData);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(
                MenuRoute.of("map.details").withParam("mapId", mapData.id.toHexString())
        );
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        verify(gateway).menu(eq(session.player), eq(0), eq("commands-map-title"), eq("commands-map-content"), any());
    }

    @Test
    @DisplayName("map maps action opens routed maps via route history")
    void map_mapsAction_opensRoutedMapsViaRouteHistory() {
        MapData mapData = new MapData("Map1", "Map1.msav", "author", "pvp");
        mapData.id = new org.bson.types.ObjectId();
        when(mapDataRepository.findById(mapData.id)).thenReturn(mapData);
        when(mapService.findPersistedMap(any())).thenReturn(null);
        when(mapService.getAvailableMaps()).thenReturn(Seq.with(createMap("Map1")));

        mapMenu.map("viewer-1", mapData);

        menuService.onMenuOption(session, 3); // maps

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("map.maps").withParam("page", "1"));
    }

    @Test
    @DisplayName("map create start action opens prompt without adding legacy history")
    @SuppressWarnings("unchecked")
    void map_createStartAction_opensPromptWithoutAddingLegacyHistory() {
        Config eventConfig = new Config();
        eventConfig.server = "event";

        SessionService localSessionService = mock(SessionService.class);
        MindustryMenuGateway localGateway = mock(MindustryMenuGateway.class);
        Provider<SessionService> localSessionProvider = mock(Provider.class);
        when(localSessionProvider.get()).thenReturn(localSessionService);
        MenuService localMenuService = new MenuService(localSessionProvider, localGateway);

        Session eventSession = session(eventConfig, localMenuService);
        when(localSessionService.get("viewer-1")).thenReturn(eventSession);

        EventService eventService = mock(EventService.class);
        VoteService voteService = mock(VoteService.class);
        EventEditorService eventEditorService = new EventEditorService(eventDataRepository, mapDataRepository,
                mock(org.xcore.plugin.database.repository.PlayerDataRepository.class));
        EventViewService eventViewService = new EventViewService(eventDataRepository, mapDataRepository,
                mock(org.xcore.plugin.database.repository.PlayerDataRepository.class));

        Provider<MapMenu> localMapProvider = mock(Provider.class);
        EventMenu realEventMenu = new EventMenu(eventConfig, globalConfig, localSessionService,
                mapService, eventService, eventEditorService, eventViewService, voteService, localMapProvider, localMenuService);
        realEventMenu.init();

        Provider<EventMenu> eventMenuProvider = mock(Provider.class);
        when(eventMenuProvider.get()).thenReturn(realEventMenu);
        MapMenu eventMapMenu = new MapMenu(eventConfig, globalConfig, localSessionService,
                mapDataRepository, eventDataRepository, mapService, eventMenuProvider, localMenuService);
        eventMapMenu.init();
        when(localMapProvider.get()).thenReturn(eventMapMenu);

        MapData mapData = new MapData("Map1", "Map1.msav", "author", "pvp");
        mapData.id = new org.bson.types.ObjectId();
        when(mapDataRepository.findById(mapData.id)).thenReturn(mapData);
        when(mapService.findPersistedMap(any())).thenReturn(null);
        when(eventDataRepository.findActive()).thenReturn(Optional.empty());

        eventMapMenu.map("viewer-1", mapData);

        localMenuService.onMenuOption(eventSession, 4);

        assertThat(eventSession.hasHistory()).isFalse();
        assertThat(eventSession.activePrompt()).isNotNull();
        assertThat(eventSession.textHandler).isNull();
        verify(localGateway).textInput(eq(eventSession.player), eq(0), eq("event-menu-create-start-title"), eq("event-menu-create-start-message"), eq(20), anyString(), eq(false));
    }

    @Test
    @DisplayName("maps empty sends empty message and does not render menu")
    void maps_empty_sendsEmptyMessage() {
        when(mapService.getAvailableMaps()).thenReturn(Seq.with());

        mapMenu.maps("viewer-1", 1);

        assertThat(session.activeScreen()).isNull();
        verify(gateway, never()).menu(any(), anyInt(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("maps back action returns to previous menu")
    void maps_backAction_returnsToPreviousMenu() {
        when(mapService.getAvailableMaps()).thenReturn(Seq.with(createMap("Map1")));

        final boolean[] ran = {false};
        session.pushHistory(() -> ran[0] = true);

        mapMenu.maps("viewer-1", 1);

        menuService.onMenuOption(session, 1); // back (after map row)

        assertThat(ran[0]).isTrue();
    }

    @Test
    @DisplayName("maps close action clears active screen")
    void maps_closeAction_clearsActiveScreen() {
        when(mapService.getAvailableMaps()).thenReturn(Seq.with(createMap("Map1")));

        mapMenu.maps("viewer-1", 1);

        menuService.onMenuOption(session, 1); // close

        assertThat(session.activeScreen()).isNull();
    }

    private Map createMap(String name) {
        return new Map(
                new Fi(name + ".msav"),
                100,
                100,
                StringMap.of("name", name, "author", "author"),
                true
        );
    }

    private Session session() {
        return session(new Config(), menuService);
    }

    private Session session(Config config, MenuService menuService) {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("viewer-1", true);
        data.uuid = "viewer-1";
        Session session = new Session(
                globalConfig,
                mock(Bundle.class),
                menuService,
                mock(org.xcore.plugin.database.repository.PlayerDataRepository.class),
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
