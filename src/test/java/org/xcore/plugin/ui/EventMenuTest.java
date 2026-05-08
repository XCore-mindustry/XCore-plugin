package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
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
import org.xcore.plugin.vote.VoteService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private Session session;

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

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.eventsPerPage = 10;
        globalConfig.mapsPerPage = 10;
        EventEditorService eventEditorService = new EventEditorService(eventDataRepository, mapDataRepository, playerDataRepository);
        EventViewService eventViewService = new EventViewService(eventDataRepository, mapDataRepository, playerDataRepository);
        eventMenu = new EventMenu(
                new Config(),
                globalConfig,
                sessionService,
                mapService,
                eventService,
                eventEditorService,
                eventViewService,
                voteService,
                mapMenuProvider
        );

        session = session();
        when(sessionService.get(session.data.uuid)).thenReturn(session);

        when(eventDataRepository.findActive()).thenReturn(Optional.empty());
        when(eventDataRepository.count(anyMapOfStatus())).thenReturn(0L);
        when(eventDataRepository.findPage(anyInt(), anyInt(), any())).thenReturn(List.of());
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
        verify(gateway).textInput(eq(session.player), eq(0), eq("event-menu-create-start-title"), eq("event-menu-create-start-message"), eq(20), anyString(), eq(false));
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
    }

    @Test
    @DisplayName("edit description prompt submit mutates draft description")
    void editDescription_submit_mutatesDraftDescription() {
        EventData draft = new EventData();
        draft.name = "Event";
        draft.author = session.data.id;
        session.setDraft(draft);

        eventMenu.edit(session.data.uuid);
        menuService.onMenuOption(session, 2);

        assertThat(session.activePrompt()).isNotNull();
        verify(gateway).textInput(eq(session.player), eq(0), eq("event-menu-edit-description-title"), eq(""), eq(1000), eq("Unknown"), eq(false));

        menuService.onTextInput(session, "New Desc");

        assertThat(draft.description).isEqualTo("New Desc");
        assertThat(session.activePrompt()).isNull();
        assertThat(session.textHandler).isNull();
    }

    @Test
    @DisplayName("planned start and end prompts apply parseTime correctly")
    void plannedStartEnd_submit_appliesParseTime() {
        EventData draft = new EventData();
        draft.name = "Event";
        draft.author = session.data.id;
        session.setDraft(draft);

        eventMenu.edit(session.data.uuid);

        // planned start is option 5
        menuService.onMenuOption(session, 5);
        assertThat(session.activePrompt()).isNotNull();
        verify(gateway).textInput(eq(session.player), eq(0), eq("event-menu-edit-planned-start-title"), eq(""), eq(64), eq(""), eq(false));

        menuService.onTextInput(session, "12345");
        assertThat(draft.plannedStartTime).isEqualTo(12345L);
        assertThat(session.activePrompt()).isNull();

        // planned end is option 6
        menuService.onMenuOption(session, 6);
        assertThat(session.activePrompt()).isNotNull();
        verify(gateway).textInput(eq(session.player), eq(0), eq("event-menu-edit-planned-end-title"), eq(""), eq(10), eq(""), eq(false));

        menuService.onTextInput(session, "bad");
        assertThat(draft.plannedEndTime).isEqualTo(0L);
        assertThat(session.activePrompt()).isNull();
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

    @SuppressWarnings("unchecked")
    private static Map<String, StatusEnum> anyMapOfStatus() {
        return any(Map.class);
    }
}
