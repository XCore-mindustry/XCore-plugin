package org.xcore.plugin.ui;

import arc.files.Fi;
import arc.struct.Seq;
import arc.struct.StringMap;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.core.GameState;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.EventDataRepository.Listing;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.EventEditorService;
import org.xcore.plugin.service.EventEditorService.Draft;
import org.xcore.plugin.service.EventEditorService.MapChoice;
import org.xcore.plugin.service.EventService;
import org.xcore.plugin.service.EventViewService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.EventMenu;
import org.xcore.plugin.ui.menu.MapMenu;
import org.xcore.plugin.ui.menu.event.EventUiBackend;
import org.xcore.plugin.ui.menu.event.EventUiController;
import org.xcore.plugin.ui.menu.event.EventUiModel;
import org.xcore.plugin.ui.menu.event.EventUiModel.Catalog;
import org.xcore.plugin.ui.menu.event.EventUiModel.Tab;
import org.xcore.plugin.ui.menu.event.EventUiModel.View;
import org.xcore.plugin.ui.menu.event.EventUiModel.Viewer;
import org.xcore.plugin.ui.menu.event.EventUiModel.VoteState;
import org.xcore.plugin.vote.VoteEvent;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.vote.VoteSession;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventMenuTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T14:52:30Z"), ZoneOffset.ofHours(3));

    private SessionService sessionService;
    private EventDataRepository eventDataRepository;
    private MapDataRepository mapDataRepository;
    private PlayerDataRepository playerDataRepository;
    private MapService mapService;
    private EventService eventService;
    private VoteService voteService;
    private MapMenu mapMenu;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private Deque<Runnable> mainThread;
    private EventMenu eventMenu;
    private Session session;
    private TomlSecretsConfig secretsConfig;
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
        mapMenu = mock(MapMenu.class);
        gateway = mock(MindustryMenuGateway.class);

        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        secretsConfig = new TomlSecretsConfig();
        secretsConfig.pagination.eventsPerPage = 10;
        secretsConfig.pagination.mapsPerPage = 10;
        mainThread = new ArrayDeque<>();
        eventMenu = eventMenu(InlineStorageExecutor.create());

        originalState = mindustry.Vars.state;
        mindustry.Vars.state = new GameState();
        mindustry.game.Rules rules = mock(mindustry.game.Rules.class);
        when(rules.mode()).thenReturn(Gamemode.pvp);
        mindustry.Vars.state.rules = rules;

        session = session();
        when(sessionService.get(session.data.uuid)).thenReturn(session);

        when(eventDataRepository.findActive()).thenReturn(Optional.empty());
        when(eventDataRepository.count(any(Listing.class), any())).thenReturn(0L);
        when(eventDataRepository.findPage(any(Listing.class), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(eventDataRepository.save(any(EventData.class))).thenReturn(true);
        when(mapService.getAvailableMaps()).thenReturn(Seq.with(createMap("Caldera"), createMap("Frozen Forest")));
    }

    @AfterEach
    void tearDown() {
        mindustry.Vars.state = originalState;
    }

    private EventMenu eventMenu(StorageExecutor storage) {
        return new EventMenu(secretsConfig, sessionService, mapService, eventService,
                new EventEditorService(eventDataRepository, mapDataRepository),
                new EventViewService(eventDataRepository, mapDataRepository, playerDataRepository),
                voteService, () -> mapMenu, null, menuService, new Async(storage, mainThread::add), CLOCK);
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("test-uuid", true);
        data.uuid = "test-uuid";
        data.id = new ObjectId();
        data.nickname = "ospx";
        Session session = new Session(new TomlSecretsConfig(), mock(Bundle.class), menuService,
                mock(PlayerDataRepository.class), player, data);
        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;
        return session;
    }

    private static mindustry.maps.Map createMap(String name) {
        return new mindustry.maps.Map(new Fi(name + ".msav"), 100, 100,
                StringMap.of("name", name, "author", "author"), true);
    }

    private List<EventData> stored(int count) {
        List<EventData> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            EventData event = new EventData();
            event.id = new ObjectId();
            event.name = "Event " + i;
            event.author = session.data.id;
            events.add(event);
        }
        return events;
    }

    private void settle() {
        while (!mainThread.isEmpty()) {
            mainThread.poll().run();
        }
    }

    private EventUiModel shown() {
        assertThat(session.hasActiveUiSession()).isTrue();
        return (EventUiModel) session.activeUiSession().model();
    }

    /** How often the client was sent the dialog, whole. */
    private void verifyShown(int times) {
        verify(gateway, times(times)).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), eq(false), eq(true),
                eq(true), any());
    }

    /** A press of the dialog the client has open, with what its fields hold. */
    private void press(String action, String... fields) {
        MenuResult result = new MenuResult(action);
        result.token = session.activeUiSession().token();
        for (int i = 0; i + 1 < fields.length; i += 2) {
            result.values.put(fields[i], fields[i + 1]);
        }
        menuService.onMenuBuilderResult(session, result);
        settle();
    }

    // =========================================================================
    // Opening
    // =========================================================================

    @Test
    @DisplayName("/event opens the list over the whole screen once the events have been read")
    void main_opensTheListOnceRead() {
        List<EventData> events = stored(3);
        when(eventDataRepository.count(Listing.UPCOMING, session.data.id)).thenReturn(3L);
        when(eventDataRepository.findPage(Listing.UPCOMING, session.data.id, 0, 10)).thenReturn(events);

        eventMenu.main(session.data.uuid);
        verify(gateway, never()).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any());

        settle();
        verifyShown(1);
        assertThat(shown().view()).isEqualTo(View.LIST);
        assertThat(shown().catalog().tab()).isEqualTo(Tab.UPCOMING);
        assertThat(shown().catalog().events()).isEqualTo(events);
        assertThat(shown().viewer().id()).isEqualTo(session.data.id);
    }

    @Test
    @DisplayName("/events opens the list on the page asked for")
    void events_opensOnThePage() {
        List<EventData> events = stored(5);
        when(eventDataRepository.count(Listing.UPCOMING, session.data.id)).thenReturn(15L);
        when(eventDataRepository.findPage(Listing.UPCOMING, session.data.id, 10, 10)).thenReturn(events);

        eventMenu.events(session.data.uuid, 2);
        settle();

        assertThat(shown().catalog().page()).isEqualTo(2);
        assertThat(shown().catalog().totalPages()).isEqualTo(2);
        assertThat(shown().catalog().events()).isEqualTo(events);
    }

    @Test
    @DisplayName("the list does not open over what the player opened while it was being read")
    void main_yieldsToANewerMenu() {
        eventMenu.main(session.data.uuid);
        session.nextUiVersion();

        settle();

        assertThat(session.hasActiveUiSession()).isFalse();
        verifyShown(0);
    }

    @Test
    @DisplayName("events that could not be read are said so in the chat, and nothing opens")
    void main_failedLoad() {
        StorageExecutor failing = mock(StorageExecutor.class);
        when(failing.supply(any(Callable.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("storage is down")));

        eventMenu(failing).main(session.data.uuid);
        settle();

        verify(session.localization).send("event-ui-load-failed");
        assertThat(session.hasActiveUiSession()).isFalse();
        verifyShown(0);
    }

    @Test
    @DisplayName("a player the server does not know gets no menu")
    void main_unknownPlayer() {
        eventMenu.main("nobody");
        eventMenu.createStart("nobody", null);
        settle();

        verify(gateway, never()).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any());
    }

    @Test
    @DisplayName("an event made for a map opens in the editor with that map, and is kept with the player")
    void createStart_opensTheEditor() {
        MapData map = new MapData("Caldera", "Caldera.msav", "author", "pvp");
        map.id = new ObjectId();

        eventMenu.createStart(session.data.uuid, map);

        verifyShown(1);
        assertThat(shown().view()).isEqualTo(View.EDITOR);
        assertThat(shown().editing().draft().map()).isEqualTo(MapChoice.of(map));
        assertThat(shown().editing().maps()).extracting(MapChoice::name).containsExactly("Caldera", "Frozen Forest");
        assertThat(session.getDraft(Draft.class).map().id()).isEqualTo(map.id);

        eventMenu.createStart(session.data.uuid, null);
        assertThat(shown().editing().draft().map().id()).isEqualTo(map.id);
    }

    // =========================================================================
    // Through the dialog
    // =========================================================================

    @Test
    @DisplayName("an event is written in the dialog and stored: its map gets a record, its author is the player")
    void create_throughTheDialog() {
        MapData record = new MapData("Frozen Forest", "Frozen Forest.msav", "author", "pvp");
        record.id = new ObjectId();
        when(mapDataRepository.findOrCreate("Frozen Forest", "Frozen Forest.msav", "author", "pvp")).thenReturn(record);
        when(mapDataRepository.findById(record.id)).thenReturn(record);
        ArgumentCaptor<EventData> saved = ArgumentCaptor.forClass(EventData.class);
        // Storage gives a new event its id, as the driver does on an insert.
        when(eventDataRepository.save(saved.capture())).thenAnswer(i -> {
            i.<EventData>getArgument(0).id = new ObjectId();
            return true;
        });
        when(eventDataRepository.findById(any(ObjectId.class))).thenAnswer(i -> saved.getValue());
        eventMenu.main(session.data.uuid);
        settle();

        press("action:create");
        assertThat(shown().view()).isEqualTo(View.EDITOR);

        // Without a map the event is not stored, and the editor says what it lacks.
        press("action:edit:save", "field_event_name", "Турнир", "field_event_description", "Командный");
        assertThat(shown().feedback()).contains("event-ui-error-no-map");
        verify(eventDataRepository, never()).save(any(EventData.class));

        press("action:edit:map", "field_event_name", "Турнир", "field_event_description", "Командный");
        assertThat(shown().view()).isEqualTo(View.MAPS);
        press("action:maps:pick:1");
        press("action:edit:temporary", "field_event_name", "Турнир", "field_event_description", "Командный");
        press("action:edit:end", "field_event_name", "Турнир", "field_event_description", "Командный");
        assertThat(shown().view()).isEqualTo(View.TIME);
        press("action:time:set:plus_three_hours");
        press("action:time:apply");
        press("action:edit:save", "field_event_name", "Турнир", "field_event_description", "Командный");

        EventData event = saved.getValue();
        assertThat(event.name).isEqualTo("Турнир");
        assertThat(event.description).isEqualTo("Командный");
        assertThat(event.author).isEqualTo(session.data.id);
        assertThat(event.map).isEqualTo(record.id);
        assertThat(event.isTemporary).isTrue();
        assertThat(event.plannedEndTime).isEqualTo(Instant.parse("2026-10-16T17:52:00Z").toEpochMilli());
        assertThat(shown().view()).isEqualTo(View.DETAILS);
        assertThat(shown().details().event()).isSameAs(event);
        assertThat(shown().details().map()).isSameAs(record);
        assertThat(shown().feedback()).contains("event-ui-saved");
        assertThat(session.hasDraft(Draft.class)).isFalse();
    }

    @Test
    @DisplayName("a dialog closed by the client is closed for the server too")
    void closedDialog() {
        eventMenu.main(session.data.uuid);
        settle();

        MenuResult closed = new MenuResult();
        closed.token = session.activeUiSession().token();
        menuService.onMenuBuilderResult(session, closed);

        assertThat(session.hasActiveUiSession()).isFalse();
    }

    // =========================================================================
    // What the menu asks of the server
    // =========================================================================

    @Test
    @DisplayName("a page of a tab is read by its listing and comes with the event that runs")
    void catalog() {
        EventData active = new EventData();
        active.id = new ObjectId();
        active.isActive = true;
        when(eventDataRepository.findActive()).thenReturn(Optional.of(active));
        ObjectId viewer = new ObjectId();
        List<EventData> events = stored(2);
        when(eventDataRepository.count(Listing.PAST, viewer)).thenReturn(12L);
        when(eventDataRepository.findPage(Listing.PAST, viewer, 10, 10)).thenReturn(events);

        Catalog catalog = eventMenu.catalog(Tab.PAST, 2, new Viewer(viewer, false, false, false, false, false));

        assertThat(catalog.tab()).isEqualTo(Tab.PAST);
        assertThat(catalog.page()).isEqualTo(2);
        assertThat(catalog.totalPages()).isEqualTo(2);
        assertThat(catalog.total()).isEqualTo(12);
        assertThat(catalog.events()).isEqualTo(events);
        assertThat(catalog.active()).isSameAs(active);
        assertThat(catalog.loaded()).isTrue();
    }

    @Test
    @DisplayName("a player not stored yet has no events of their own, and storage is not searched by no author")
    void catalog_mineWithoutId() {
        Catalog catalog = eventMenu.catalog(Tab.MINE, 1, Viewer.GUEST);

        assertThat(catalog.events()).isEmpty();
        assertThat(catalog.loaded()).isTrue();
        verify(eventDataRepository, never()).count(any(Listing.class), any());
        verify(eventDataRepository, never()).findPage(any(Listing.class), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("an event is read with its map and the name its author goes by")
    void event() {
        EventData event = stored(1).getFirst();
        MapData map = new MapData("Caldera", "Caldera.msav", "author", "pvp");
        map.id = new ObjectId();
        event.map = map.id;
        PlayerData author = new PlayerData("author-uuid", true);
        author.nickname = "plain";
        when(eventDataRepository.findById(event.id)).thenReturn(event);
        when(mapDataRepository.findById(map.id)).thenReturn(map);
        when(playerDataRepository.findById(event.author)).thenReturn(author);

        assertThat(eventMenu.event(event.id)).isEqualTo(new EventUiBackend.Stored(event, map, "plain"));

        author.customNickname = "[gold]custom";
        assertThat(eventMenu.event(event.id).author()).isEqualTo("[gold]custom");

        when(playerDataRepository.findById(event.author)).thenReturn(null);
        when(mapDataRepository.findById(map.id)).thenReturn(null);
        assertThat(eventMenu.event(event.id)).isEqualTo(new EventUiBackend.Stored(event, null, ""));

        assertThat(eventMenu.event(new ObjectId())).isNull();
    }

    @Test
    @DisplayName("the votes are told apart: none, one for an event, one for something else")
    void votes() throws Exception {
        assertThat(eventMenu.votes()).isEqualTo(VoteState.NONE);
        assertThat(eventMenu.votedEvent()).isNull();

        when(voteService.isVoting()).thenReturn(true);
        when(voteService.getCurrentSession()).thenReturn(mock(VoteSession.class));
        assertThat(eventMenu.votes()).isEqualTo(new VoteState(true, null, "", 0, 0));
        assertThat(eventMenu.votes().forEvent()).isFalse();
        eventMenu.stopVote();
        verify(voteService, never()).endVote();

        EventData target = stored(1).getFirst();
        VoteEvent vote = mock(VoteEvent.class);
        var field = VoteEvent.class.getField("target");
        field.setAccessible(true);
        field.set(vote, target);
        when(vote.votes()).thenReturn(2);
        when(vote.votesRequired()).thenReturn(5);
        when(voteService.getCurrentSession()).thenReturn(vote);

        assertThat(eventMenu.votes()).isEqualTo(new VoteState(true, target.id.toHexString(), "Event 0", 2, 5));
        assertThat(eventMenu.votes().isFor(target)).isTrue();
        assertThat(eventMenu.votedEvent()).isSameAs(target);
        eventMenu.stopVote();
        verify(voteService).endVote();
    }

    @Test
    @DisplayName("the maps an event can be given are the server's own, in the mode it plays")
    void maps() {
        assertThat(eventMenu.maps()).containsExactly(
                new MapChoice(null, "Caldera", "Caldera.msav", "author", "pvp"),
                new MapChoice(null, "Frozen Forest", "Frozen Forest.msav", "author", "pvp"));
        assertThat(eventMenu.mapsPerPage()).isEqualTo(10);
    }

    @Test
    @DisplayName("a vote, a rating and a stop go to the service that does them")
    void delegates() {
        EventData event = stored(1).getFirst();
        when(eventService.handleReputation(session.player, true, event)).thenReturn(true);

        eventMenu.startVote(session.player, event, true);
        assertThat(eventMenu.rate(session.player, true, event)).isTrue();
        assertThat(eventMenu.rate(session.player, false, event)).isFalse();
        eventMenu.finishActiveEvent();

        verify(eventService).startVoteSession(session.player, event, true);
        verify(eventService).finishActiveEvent();
    }

    @Test
    @DisplayName("the map of an event opens in the maps menu, and one the server lacks has no picture")
    void map() {
        MapData map = new MapData("Caldera", "Caldera.msav", "author", "pvp");

        eventMenu.openMap(session, map);
        verify(mapMenu).map(session.data.uuid, map);

        assertThat(eventMenu.cachedPreview(map)).isNull();
        List<Throwable> errors = new ArrayList<>();
        eventMenu.requestPreview(session.player, map, (region, error) -> {
            assertThat(region).isNull();
            errors.add(error);
        });
        assertThat(errors).hasSize(1);
    }

    @Test
    @DisplayName("the menu is laid out by the controller of its own session")
    void controller() {
        eventMenu.main(session.data.uuid);
        settle();

        assertThat(EventUiController.viewer(session)).isEqualTo(shown().viewer());
    }
}
