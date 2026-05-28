package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import arc.files.Fi;
import arc.struct.Seq;
import arc.struct.StringMap;
import jakarta.inject.Provider;
import mindustry.core.GameState;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.NetConnection;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.EventEditorService;
import org.xcore.plugin.service.EventService;
import org.xcore.plugin.service.EventViewService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.EventMenu;
import org.xcore.plugin.ui.menu.MapMenu;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.vote.VoteService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventMenuTest {

    private SessionService sessionService;
    private EventDataRepository eventDataRepository;
    private MapDataRepository mapDataRepository;
    private PlayerDataRepository playerDataRepository;
    private MapService mapService;
    private EventService eventService;
    private VoteService voteService;
    private Provider<MapMenu> mapMenuProvider;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private EventMenu eventMenu;
    private MapMenu mapMenu;
    private Session session;
    private GlobalConfig globalConfig;
    private GameState originalState;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionService = mock(SessionService.class);
        eventDataRepository = mock(EventDataRepository.class);
        mapDataRepository = mock(MapDataRepository.class);
        playerDataRepository = mock(PlayerDataRepository.class);
        mapService = mock(MapService.class);
        eventService = mock(EventService.class);
        voteService = mock(VoteService.class);
        mapMenuProvider = mock(Provider.class);
        gateway = mock(MindustryMenuGateway.class);

        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        globalConfig = new GlobalConfig();
        globalConfig.eventsPerPage = 10;
        globalConfig.mapsPerPage = 10;
        EventEditorService eventEditorService = new EventEditorService(eventDataRepository, mapDataRepository, playerDataRepository);
        EventViewService eventViewService = new EventViewService(eventDataRepository, mapDataRepository, playerDataRepository);
        eventMenu = new EventMenu(
                globalConfig,
                sessionService,
                mapService,
                eventService,
                eventEditorService,
                eventViewService,
                voteService,
                mapMenuProvider,
                menuService
        );
        eventMenu.init();

        Provider<EventMenu> eventMenuProvider = mock(Provider.class);
        when(eventMenuProvider.get()).thenReturn(eventMenu);
        mapMenu = new MapMenu(
                new TomlXcoreConfig(),
                globalConfig,
                sessionService,
                mapDataRepository,
                eventDataRepository,
                mapService,
                eventMenuProvider,
                menuService
        );
        mapMenu.init();
        when(mapMenuProvider.get()).thenReturn(mapMenu);

        originalState = mindustry.Vars.state;
        mindustry.Vars.state = new GameState();
        mindustry.game.Rules rules = mock(mindustry.game.Rules.class);
        when(rules.mode()).thenReturn(Gamemode.pvp);
        mindustry.Vars.state.rules = rules;

        session = session();
        when(sessionService.get(session.data.uuid)).thenReturn(session);

        when(eventDataRepository.findActive()).thenReturn(Optional.empty());
        when(eventDataRepository.count(anyMapOfStatus())).thenReturn(0L);
        when(eventDataRepository.findPage(anyInt(), anyInt(), any())).thenReturn(List.of());
        when(eventDataRepository.findById(any(ObjectId.class))).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        mindustry.Vars.state = originalState;
    }

    @Test
    @DisplayName("createStart opens active prompt via gateway textInput, sets draft author and map, and does not set textHandler")
    void createStart_opensPromptAndSetsDraft() {
        MapData map = new MapData();
        map.id = new ObjectId();

        eventMenu.createStart(session.data.uuid, map);

        EventData draft = session.getDraft(EventData.class);
        assertThat(draft.author).isEqualTo(session.data.id);
        assertThat(draft.map).isEqualTo(map.id);
        assertThat(session.textHandler).isNull();
        assertThat(session.activePrompt()).isNotNull();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.create-start"));
        verify(gateway).textInput(eq(session.player), eq(0), eq("event-menu-create-start-title"), eq("event-menu-create-start-message"), eq(20), anyString(), eq(false));
    }

    @Test
    @DisplayName("main createStart action opens prompt without adding legacy history")
    void main_createStartAction_opensPromptWithoutAddingLegacyHistory() {
        eventMenu.main(session.data.uuid);

        menuService.onMenuOption(session, 1);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.activePrompt()).isNotNull();
    }

    @Test
    @DisplayName("createStart submit sets draft name and returns to edit screen with no active prompt")
    void createStart_submit_setsNameAndReturnsToEdit() {
        MapData map = new MapData();
        map.id = new ObjectId();

        eventMenu.createStart(session.data.uuid, map);
        assertThat(session.activePrompt()).isNotNull();

        menuService.onTextInput(session, "My Event");

        EventData draft = session.getDraft(EventData.class);
        assertThat(draft.name).isEqualTo("My Event");
        assertThat(session.activePrompt()).isNull();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.edit"));
    }

    @Test
    @DisplayName("createStart cancel clears draft and leaves no active prompt or textHandler")
    void createStart_cancel_clearsDraftAndPrompt() {
        MapData map = new MapData();
        map.id = new ObjectId();

        eventMenu.createStart(session.data.uuid, map);
        assertThat(session.activePrompt()).isNotNull();

        menuService.onTextInput(session, null);

        assertThat(session.activePrompt()).isNull();
        assertThat(session.textHandler).isNull();
        assertThat(session.hasDraft(EventData.class)).isFalse();
        assertThat(session.activeScreen()).isNotNull();
    }

    @Test
    @DisplayName("edit name prompt submit mutates draft name, clears active prompt, leaves textHandler null")
    void editName_submit_mutatesDraftName() {
        EventData draft = new EventData();
        draft.name = "Old";
        draft.author = session.data.id;
        session.setDraft(draft);

        eventMenu.edit(session.data.uuid);
        menuService.onMenuOption(session, 0);

        assertThat(session.activePrompt()).isNotNull();
        assertThat(session.textHandler).isNull();
        verify(gateway).textInput(eq(session.player), eq(0), eq("event-menu-edit-name-title"), eq(""), eq(24), eq("Old"), eq(false));

        menuService.onTextInput(session, "New Name");

        assertThat(draft.name).isEqualTo("New Name");
        assertThat(session.activePrompt()).isNull();
        assertThat(session.textHandler).isNull();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.edit"));
    }

    @Test
    @DisplayName("edit description prompt submit mutates draft description")
    void editDescription_submit_mutatesDraftDescription() {
        EventData draft = new EventData();
        draft.name = "Event";
        draft.author = session.data.id;
        session.setDraft(draft);

        eventMenu.edit(session.data.uuid);
        menuService.onMenuOption(session, 1);

        assertThat(session.activePrompt()).isNotNull();
        verify(gateway).textInput(eq(session.player), eq(0), eq("event-menu-edit-description-title"), eq(""), eq(1000), eq("Unknown"), eq(false));

        menuService.onTextInput(session, "New Desc");

        assertThat(draft.description).isEqualTo("New Desc");
        assertThat(session.activePrompt()).isNull();
        assertThat(session.textHandler).isNull();
    }

    @Test
    @DisplayName("planned start and end open picker and apply values through routed flow")
    void plannedStartEnd_picker_appliesValues() {
        EventData draft = new EventData();
        draft.name = "Event";
        draft.author = session.data.id;
        draft.plannedEndTime = 999L;
        session.setDraft(draft);

        eventMenu.edit(session.data.uuid);

        // planned start is option 3 and opens the shared picker route
        menuService.onMenuOption(session, 3);
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("shared.datetime-picker"));
        assertThat(session.hasRouteHistory()).isTrue();

        // picker option 0 = Today, option 15 = Apply
        menuService.onMenuOption(session, 0);
        menuService.onMenuOption(session, 15);
        assertThat(draft.plannedStartTime).isGreaterThan(0L);
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.edit"));

        // planned end is option 4 and uses reset/apply to write zero back into the draft
        menuService.onMenuOption(session, 4);
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("shared.datetime-picker"));

        // picker option 16 = Reset, option 15 = Apply
        menuService.onMenuOption(session, 16);
        menuService.onMenuOption(session, 15);
        assertThat(draft.plannedEndTime).isEqualTo(0L);
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.edit"));
    }

    @Test
    @DisplayName("main renders routed screen with route metadata")
    void main_rendersRoutedScreenWithRouteMetadata() {
        eventMenu.main(session.data.uuid);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.main"));
        verify(gateway).menu(eq(session.player), eq(0), eq("event-menu-main-title"), eq("event-menu-main-content"), any());
    }

    @Test
    @DisplayName("main events action opens routed events via route history")
    void main_eventsAction_opensRoutedEventsViaRouteHistory() {
        eventMenu.main(session.data.uuid);

        menuService.onMenuOption(session, 0);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.events").withParam("page", "1"));
    }

    @Test
    @DisplayName("events empty renders routed screen with empty content")
    void events_empty_rendersRoutedEmptyScreen() {
        eventMenu.events(session.data.uuid, 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.events").withParam("page", "1"));
        verify(gateway).menu(eq(session.player), eq(0), eq("event-menu-events-title"), eq("event-menu-events-empty"), any());
    }

    @Test
    @DisplayName("events with events renders page and lists events")
    void events_withEvents_rendersPageWithEvents() {
        EventData event1 = new EventData();
        event1.id = new ObjectId();
        event1.name = "Event One";
        event1.isActive = false;

        when(eventDataRepository.count(anyMapOfStatus())).thenReturn(1L);
        when(eventDataRepository.findPage(anyInt(), anyInt(), any())).thenReturn(List.of(event1));

        eventMenu.events(session.data.uuid, 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.events").withParam("page", "1"));
        assertThat(session.activeScreen().actionCount()).isGreaterThan(0);
        verify(gateway).menu(eq(session.player), eq(0), eq("event-menu-events-title"), eq("event-menu-events-content"), any());
    }

    @Test
    @DisplayName("events next page navigation renders second page")
    void events_nextPage_rendersSecondPage() {
        EventData event1 = new EventData();
        event1.id = new ObjectId();
        event1.name = "Event One";

        EventData event2 = new EventData();
        event2.id = new ObjectId();
        event2.name = "Event Two";

        globalConfig.eventsPerPage = 1;
        when(eventDataRepository.count(anyMapOfStatus())).thenReturn(2L);
        when(eventDataRepository.findPage(anyInt(), anyInt(), any())).thenReturn(List.of(event1), List.of(event2));

        eventMenu.events(session.data.uuid, 1);

        // rows: filters (0..2), next (3), event1 (4), main (5), close (6)
        menuService.onMenuOption(session, 3);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.events").withParam("page", "2"));
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("event-menu-events-title"), anyString(), any());
    }

    @Test
    @DisplayName("events status toggle changes filter and resets to page 1")
    void events_statusToggle_changesFilter() {
        eventMenu.events(session.data.uuid, 2);

        // rows: filters (0..2), main (3), close (4)
        menuService.onMenuOption(session, 0);

        assertThat(session.sortStatus.get("finished")).isEqualTo(StatusEnum.Active);
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.events").withParam("page", "1"));
    }

    @Test
    @DisplayName("events click event opens routed event detail and back returns to events")
    void events_clickEvent_opensRoutedEventAndBackReturnsToEvents() {
        EventData event1 = new EventData();
        event1.id = new ObjectId();
        event1.name = "Event One";
        event1.author = session.data.id;

        when(eventDataRepository.count(anyMapOfStatus())).thenReturn(1L);
        when(eventDataRepository.findPage(anyInt(), anyInt(), any())).thenReturn(List.of(event1));
        when(eventDataRepository.findById(event1.id)).thenReturn(event1);

        eventMenu.events(session.data.uuid, 1);

        // rows: filters (0..2), event1 (3), main (4), back (5), close (6)
        menuService.onMenuOption(session, 3);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.details").withParam("eventId", event1.id.toHexString()));

        menuService.goBack(session);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.events").withParam("page", "1"));
    }

    @Test
    @DisplayName("event renders routed screen with route metadata")
    void event_rendersRoutedScreenWithRouteMetadata() {
        EventData event = new EventData();
        event.id = new ObjectId();
        event.name = "Event One";
        event.author = session.data.id;

        when(eventDataRepository.findById(event.id)).thenReturn(event);

        eventMenu.event(session.data.uuid, event);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.details").withParam("eventId", event.id.toHexString()));
        verify(gateway).menu(eq(session.player), eq(0), eq("event-menu-event-title"), eq("event-menu-event-content"), any());
    }

    @Test
    @DisplayName("event events action opens routed events via route history")
    void event_eventsAction_opensRoutedEventsViaRouteHistory() {
        EventData event = new EventData();
        event.id = new ObjectId();
        event.name = "Event One";
        event.author = session.data.id;

        when(eventDataRepository.findById(event.id)).thenReturn(event);

        eventMenu.event(session.data.uuid, event);

        menuService.onMenuOption(session, 4);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.events").withParam("page", "1"));
    }

    @Test
    @DisplayName("event edit action opens legacy edit without adding legacy history")
    void event_editAction_opensRoutedEditWithoutAddingLegacyHistory() {
        EventData event = new EventData();
        event.id = new ObjectId();
        event.name = "Event One";
        event.author = session.data.id;

        when(eventDataRepository.findById(event.id)).thenReturn(event);

        eventMenu.event(session.data.uuid, event);

        menuService.onMenuOption(session, 3);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.edit"));
        assertThat(session.hasDraft(EventData.class)).isTrue();
        assertThat(session.getDraft(EventData.class)).isSameAs(event);
    }

    @Test
    @DisplayName("edit renders routed screen with route metadata")
    void edit_rendersRoutedScreenWithRouteMetadata() {
        EventData draft = new EventData();
        draft.name = "Event";
        draft.author = session.data.id;
        session.setDraft(draft);

        eventMenu.edit(session.data.uuid);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.edit"));
        verify(gateway).menu(eq(session.player), eq(0), eq("event-menu-edit-title"), eq("event-menu-edit-content"), any());
    }

    @Test
    @DisplayName("edit map action opens routed map selection via route history")
    void edit_mapAction_opensRoutedMapSelectionViaRouteHistory() {
        EventData draft = new EventData();
        draft.name = "Event";
        draft.author = session.data.id;
        session.setDraft(draft);
        when(mapService.getAvailableMaps()).thenReturn(Seq.with(createMap("Map1")));

        eventMenu.edit(session.data.uuid);

        menuService.onMenuOption(session, 2);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.map-selection").withParam("page", "1"));
    }

    @Test
    @DisplayName("map selection choosing map updates draft and returns to routed edit")
    void mapSelection_chooseMap_updatesDraftAndReturnsToRoutedEdit() {
        EventData draft = new EventData();
        draft.name = "Event";
        draft.author = session.data.id;
        session.setDraft(draft);

        MapData selectedMap = new MapData("Map1", "Map1.msav", "author", "pvp");
        selectedMap.id = new ObjectId();
        when(mapService.getAvailableMaps()).thenReturn(Seq.with(createMap("Map1")));
        when(mapDataRepository.findOrCreate("Map1", "Map1.msav", "author", "pvp")).thenReturn(selectedMap);
        when(mapDataRepository.findById(selectedMap.id)).thenReturn(selectedMap);

        eventMenu.edit(session.data.uuid);
        menuService.onMenuOption(session, 2);
        menuService.onMenuOption(session, 0);

        assertThat(draft.map).isEqualTo(selectedMap.id);
        assertThat(session.hasHistory()).isFalse();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.edit"));
    }

    @Test
    @DisplayName("map selection back returns to routed edit")
    void mapSelection_back_returnsToRoutedEdit() {
        EventData draft = new EventData();
        draft.name = "Event";
        draft.author = session.data.id;
        session.setDraft(draft);
        when(mapService.getAvailableMaps()).thenReturn(Seq.with(createMap("Map1")));

        eventMenu.edit(session.data.uuid);
        menuService.onMenuOption(session, 2);
        menuService.onMenuOption(session, 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.edit"));
    }

    @Test
    @DisplayName("event map action opens routed map via route history")
    void event_mapAction_opensRoutedMapViaRouteHistory() {
        EventData event = new EventData();
        event.id = new ObjectId();
        event.name = "Event One";
        event.author = session.data.id;

        MapData map = new MapData("Map One", "Map One.msav", "author", "pvp");
        map.id = new ObjectId();
        event.map = map.id;

        when(eventDataRepository.findById(event.id)).thenReturn(event);
        when(mapDataRepository.findById(map.id)).thenReturn(map);

        eventMenu.event(session.data.uuid, event);

        menuService.onMenuOption(session, 3);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("map.details").withParam("mapId", map.id.toHexString()));
    }

    @Test
    @DisplayName("event not found renders routed fallback screen")
    void event_notFoundRendersRoutedFallbackScreen() {
        ObjectId missingId = new ObjectId();
        EventData event = new EventData();
        event.id = missingId;

        when(eventDataRepository.findById(missingId)).thenReturn(null);

        eventMenu.event(session.data.uuid, event);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("event.details").withParam("eventId", missingId.toHexString()));
        verify(gateway).menu(eq(session.player), eq(0), eq("event-menu-event-title"), eq("error-internal"), any());
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("test-uuid", true);
        data.uuid = "test-uuid";
        data.id = new ObjectId();
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

    private mindustry.maps.Map createMap(String name) {
        return new mindustry.maps.Map(
                new Fi(name + ".msav"),
                100,
                100,
                StringMap.of("name", name, "author", "author"),
                true
        );
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, StatusEnum> anyMapOfStatus() {
        return any(java.util.Map.class);
    }
}
