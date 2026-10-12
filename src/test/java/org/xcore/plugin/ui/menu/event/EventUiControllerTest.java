package org.xcore.plugin.ui.menu.event;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Localizer;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.permission.PermissionService;
import org.xcore.plugin.service.EventEditorService.Draft;
import org.xcore.plugin.service.EventEditorService.Editor;
import org.xcore.plugin.service.EventEditorService.MapChoice;
import org.xcore.plugin.service.EventEditorService.Problem;
import org.xcore.plugin.service.EventEditorService.SaveResult;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.plugin.ui.menu.event.EventUiEvent.EditorAction;
import org.xcore.plugin.ui.menu.event.EventUiEvent.Fields;
import org.xcore.plugin.ui.menu.event.EventUiModel.Catalog;
import org.xcore.plugin.ui.menu.event.EventUiModel.Confirm;
import org.xcore.plugin.ui.menu.event.EventUiModel.Tab;
import org.xcore.plugin.ui.menu.event.EventUiModel.TimeField;
import org.xcore.plugin.ui.menu.event.EventUiModel.View;
import org.xcore.plugin.ui.menu.event.EventUiModel.Viewer;
import org.xcore.plugin.ui.menu.event.EventUiModel.VoteState;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.UiSession;
import org.xcore.ui.runtime.UpdateResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventUiControllerTest {

    private static final String UUID = "viewer-uuid";
    /** 16.10.2026 17:52 at the server. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T14:52:30Z"), ZoneOffset.ofHours(3));
    private static final long NOW = CLOCK.millis();
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;

    private FakeBackend backend;
    private Deque<Runnable> mainThread;
    private Async async;
    private Session session;
    private MenuService menuService;
    private Set<String> granted;
    private Localization keys;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        backend = new FakeBackend();
        mainThread = new ArrayDeque<>();
        async = new Async(InlineStorageExecutor.create(), mainThread::add);

        PlayerData data = new PlayerData(UUID, true);
        data.id = new ObjectId();
        data.nickname = "[#ff8800]ospx";
        Bundle bundle = mock(Bundle.class);
        Localizer localizer = mock(Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        menuService = mock(MenuService.class);
        session = new Session(new TomlSecretsConfig(), bundle, menuService, mock(PlayerDataRepository.class),
                player, data);

        // The texts as their keys: what a reducer answers with is then told apart by name.
        keys = mock(Localization.class);
        when(keys.t(anyString())).thenAnswer(i -> i.getArgument(0));
        when(keys.t(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(keys.getLocale()).thenReturn(Locale.ENGLISH);
        session.localization = keys;

        granted = new HashSet<>();
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.has(any(Session.class), anyString())).thenAnswer(i -> granted.contains(i.<String>getArgument(1)));
        session.permissions = permissions;
    }

    // =========================================================================
    // What the tests are made of
    // =========================================================================

    /** The server as the menu sees it, kept in memory. */
    private static final class FakeBackend implements EventUiBackend {
        final List<EventData> events = new ArrayList<>();
        final Map<ObjectId, MapData> storedMaps = new HashMap<>();
        final List<MapChoice> maps = new ArrayList<>();
        final List<Draft> saved = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        final List<BiConsumer<String, Throwable>> previews = new ArrayList<>();
        VoteState votes = VoteState.NONE;
        @Nullable EventData voted;
        @Nullable String cachedPreview;
        @Nullable Problem saveProblem;
        boolean rateChanges = true;
        /** Storage keeps the id of what it stores to itself. */
        boolean forgetsId;
        /** Storage is down: what it is asked for comes back empty. */
        boolean failing;
        int mapsPerPage = 10;

        @Override
        public Catalog catalog(Tab tab, int page, Viewer viewer) {
            calls.add("catalog:" + tab.id() + ":" + page);
            if (failing) {
                return null;
            }
            List<EventData> found = events.stream().filter(event -> switch (tab) {
                case UPCOMING -> !event.isFinished && !event.isActive;
                case MAJOR -> event.isMajor && !event.isFinished;
                case PAST -> event.isFinished;
                case MINE -> viewer.id() != null && viewer.id().equals(event.author);
            }).toList();
            int pages = Math.max(1, (found.size() + 9) / 10);
            int shown = Math.clamp(page, 1, pages);
            return new Catalog(tab, shown, pages, found.size(),
                    found.subList((shown - 1) * 10, Math.min(found.size(), shown * 10)),
                    events.stream().filter(event -> event.isActive).findFirst().orElse(null));
        }

        @Override
        public @Nullable Stored event(ObjectId id) {
            calls.add("event");
            if (failing) {
                return null;
            }
            return events.stream().filter(event -> id.equals(event.id)).findFirst()
                    .map(event -> new Stored(event, event.map == null ? null : storedMaps.get(event.map),
                            "ОченьДлинныйНикнеймАвтораСобытияБезПробелов"))
                    .orElse(null);
        }

        @Override
        public SaveResult save(Draft draft, Editor editor, String fallbackName) {
            saved.add(draft);
            if (failing) {
                return null;
            }
            if (saveProblem != null) {
                return new SaveResult(null, saveProblem);
            }
            EventData event = draft.isNew() ? new EventData() : events.stream()
                    .filter(stored -> draft.id().equals(stored.id)).findFirst().orElseThrow();
            if (draft.isNew()) {
                event.id = new ObjectId();
                event.author = editor.id();
                events.add(event);
            }
            if (forgetsId) {
                EventData copy = new EventData();
                copy.name = event.name;
                return new SaveResult(copy, null);
            }
            event.name = draft.name().isBlank() ? fallbackName : draft.name();
            event.description = draft.description();
            event.isTemporary = draft.temporary();
            event.isMajor = draft.major();
            event.plannedStartTime = draft.plannedStart();
            event.plannedEndTime = draft.plannedEnd();
            return new SaveResult(event, null);
        }

        @Override
        public void finishActiveEvent() {
            calls.add("finish");
            for (EventData event : events) {
                if (event.isActive) {
                    event.isActive = false;
                    event.isFinished = true;
                    event.endTime = NOW;
                }
            }
        }

        @Override
        public VoteState votes() {
            return votes;
        }

        @Override
        public @Nullable EventData votedEvent() {
            return voted;
        }

        @Override
        public void startVote(Player player, EventData event, boolean forced) {
            calls.add((forced ? "force:" : "vote:") + event.name);
        }

        @Override
        public void stopVote() {
            calls.add("stop_vote");
            votes = VoteState.NONE;
            voted = null;
        }

        @Override
        public boolean rate(Player player, boolean like, EventData event) {
            calls.add(like ? "like" : "dislike");
            return rateChanges;
        }

        @Override
        public List<MapChoice> maps() {
            return maps;
        }

        @Override
        public int mapsPerPage() {
            return mapsPerPage;
        }

        @Override
        public void openMap(Session session, MapData map) {
            calls.add("map:" + map.name);
        }

        @Override
        public @Nullable String cachedPreview(MapData map) {
            return cachedPreview;
        }

        @Override
        public void requestPreview(Player player, MapData map, BiConsumer<String, Throwable> done) {
            previews.add(done);
        }
    }

    /** A dialog of the menu: what it shows, and the answers of storage coming back to it. */
    private final class Dialog {
        final EventUiController controller = new EventUiController(backend, async, session, CLOCK);
        final List<EventUiEvent> delivered = new ArrayList<>();
        private final Deque<EventUiEvent> dispatched = new ArrayDeque<>();
        final UiSession<EventUiModel, EventUiEvent> ui;
        EventUiModel model;
        UpdateResult<EventUiModel> last;
        boolean closed;

        @SuppressWarnings("unchecked")
        Dialog(EventUiModel model) {
            this.model = model;
            ui = mock(UiSession.class);
            doAnswer(i -> dispatched.add(i.getArgument(0))).when(ui).dispatch(any());
            session.setActiveUiSession(ui);
        }

        /** The event as the dialog takes it, before storage has answered anything. */
        UpdateResult<EventUiModel> press(EventUiEvent event) {
            last = controller.update(model, event, null);
            model = last.model();
            closed |= last.close();
            return last;
        }

        /** The event and everything it sets going, to the end. */
        UpdateResult<EventUiModel> send(EventUiEvent event) {
            press(event);
            settle();
            return last;
        }

        void settle() {
            while (!mainThread.isEmpty() || !dispatched.isEmpty()) {
                while (!mainThread.isEmpty()) {
                    mainThread.poll().run();
                }
                while (!dispatched.isEmpty()) {
                    EventUiEvent event = dispatched.poll();
                    delivered.add(event);
                    press(event);
                }
            }
        }

        Draft draft() {
            return model.editing().draft();
        }
    }

    private Dialog open() {
        EventUiController controller = new EventUiController(backend, async, session, CLOCK);
        Viewer viewer = EventUiController.viewer(session);
        return new Dialog(controller.model(viewer, controller.fetch(Tab.UPCOMING, 1, viewer)));
    }

    private EventData event(String name) {
        EventData event = new EventData();
        event.id = new ObjectId();
        event.name = name;
        event.author = session.data.id;
        event.createdModelTime = NOW - 3 * DAY;
        backend.events.add(event);
        return event;
    }

    private MapData mapOf(EventData event) {
        MapData map = new MapData("Ancient Caldera [Of] The Very Long Map Name", "caldera.msav",
                "[#00ff88]ОченьДлинныйАвторКартыБезПробелов", "pvp");
        map.id = new ObjectId();
        event.map = map.id;
        backend.storedMaps.put(map.id, map);
        return map;
    }

    /** A server with more than a page of events to come, one running, some over, and a vote for one. */
    private void crowded() {
        for (int i = 0; i < 12; i++) {
            EventData event = event("[#ff8800]ОченьДлинноеНазваниеСобытияБезПробелов" + i);
            mapOf(event);
            event.description = "Командный турнир";
            event.isMajor = i % 4 == 0;
            event.isTemporary = i % 3 == 0;
            event.plannedStartTime = i % 5 == 4 ? 0L : NOW + i * DAY;
            event.plannedEndTime = event.plannedStartTime == 0L ? 0L : event.plannedStartTime + 3 * HOUR;
            event.like = i * 37;
            event.dislike = i;
        }
        EventData running = event("Tower Defense Marathon Of The Weekend");
        mapOf(running);
        running.isActive = true;
        running.isTemporary = true;
        running.startTime = NOW - HOUR;
        running.plannedEndTime = NOW + 2 * HOUR;
        for (int i = 0; i < 3; i++) {
            EventData past = event("Прошедшее событие " + i);
            past.isFinished = true;
            past.startTime = NOW - (i + 2) * DAY;
            past.endTime = i == 0 ? 0L : past.startTime + HOUR;
            past.like = 1234;
            past.dislike = 567;
        }
        EventData voted = backend.events.get(2);
        backend.voted = voted;
        backend.votes = new VoteState(true, voted.id.toHexString(), voted.name, 3, 5);
        for (int i = 0; i < 25; i++) {
            backend.maps.add(new MapChoice(null, i % 2 == 0 ? "Ancient Caldera [Of] The Very Long Map Name " + i
                    : "Карта " + i, "map" + i + ".msav", "ОченьДлинныйАвторКартыБезПробелов" + i, "pvp"));
        }
    }

    private void staff() {
        granted.addAll(List.of(PermissionNodes.EVENTS_STOP, PermissionNodes.VOTES_CANCEL,
                PermissionNodes.EVENTS_FORCE_VOTE, PermissionNodes.EVENTS_CREATE_MAJOR,
                PermissionNodes.EVENTS_EDIT_OTHERS));
    }

    private static VoteState voteFor(EventData event) {
        return new VoteState(true, event.id.toHexString(), event.name, 2, 4);
    }

    /** Everything a player must never be shown: a text the bundle lacks, and the ids of storage. */
    private void assertReadable(VNode rendered, String what) {
        LayoutAssert.assertFitsPacket(rendered, what);
        assertThat(LayoutAssert.allText(rendered)).as(what).doesNotContain("event-ui-", "event-menu-", "map-ui-");
        String dsl = LayoutAssert.dsl(rendered);
        for (EventData event : backend.events) {
            assertThat(dsl).as(what).doesNotContain(event.id.toHexString());
        }
        assertThat(dsl).as(what).doesNotContain(session.data.id.toHexString(), UUID);
    }

    private void assertLaidOut(Dialog dialog, String what, String... actions) {
        for (Screen screen : Screen.ALL) {
            VNode window = dialog.controller.window(dialog.model, screen);
            LayoutAssert.assertLaidOut(window, screen);
            if (actions.length > 0) {
                assertThat(LayoutAssert.actions(window)).as("%s on %s", what, screen).contains(actions);
            }
        }
        assertReadable(dialog.controller.render(dialog.model), what);
    }

    // =========================================================================
    // Layout
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en", "be", "cs", "de", "es", "fr", "pl"})
    @DisplayName("a full page of events, with one running and a vote for another, fits every screen")
    void list_isLaidOutForEveryScreen(String language) {
        session.localization = LayoutAssert.localization(language);
        crowded();
        staff();
        session.pushHistory(() -> {
        });
        Dialog dialog = open();

        assertThat(dialog.model.catalog().events()).hasSize(10);
        assertThat(dialog.model.catalog().totalPages()).isEqualTo(2);
        assertLaidOut(dialog, "the list in " + language, "action:tab:upcoming", "action:tab:major", "action:tab:past",
                "action:tab:mine", "action:active", "action:voted", "action:event:0", "action:event:9",
                "action:page:next", "action:refresh", "action:create", "action:back");

        for (Tab tab : Tab.values()) {
            dialog.send(new EventUiEvent.SelectTab(tab));
            assertLaidOut(dialog, "the tab " + tab + " in " + language, "action:create");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en", "be", "cs", "de", "es", "fr", "pl"})
    @DisplayName("a server without events says so on every tab, and before the first page is read says it is loading")
    void emptyList_saysSo(String language) {
        session.localization = LayoutAssert.localization(language);
        Dialog dialog = open();

        for (Tab tab : Tab.values()) {
            dialog.send(new EventUiEvent.SelectTab(tab));
            assertLaidOut(dialog, "the empty tab " + tab + " in " + language, "action:create");
            assertThat(LayoutAssert.actions(dialog.controller.render(dialog.model)))
                    .doesNotContain("action:event:0", "action:active", "action:voted", "action:back",
                            "action:page:next", "action:page:prev");
        }

        Dialog unread = new Dialog(dialog.controller.initialModel(null));
        assertThat(unread.model.catalog().loaded()).isFalse();
        assertLaidOut(unread, "the list being read in " + language);
        assertThat(LayoutAssert.allText(unread.controller.render(unread.model)))
                .contains(arc.util.Strings.stripColors(session.localization.t("map-ui-loading")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en", "be", "cs", "de", "es", "fr", "pl"})
    @DisplayName("an event fits every screen whatever state it is in, with a description as long as the field takes")
    void event_isLaidOutForEveryScreen(String language) {
        session.localization = LayoutAssert.localization(language);
        crowded();
        staff();
        EventData upcoming = backend.events.get(0);
        upcoming.description = ("Командный турнир на выживание, приходите все. ".repeat(30)
                + "[accent]" + "Ы".repeat(120) + "[]").substring(0, 1000);
        EventData other = backend.events.get(1);
        EventData running = backend.events.stream().filter(event -> event.isActive).findFirst().orElseThrow();
        EventData past = backend.events.stream().filter(event -> event.isFinished).findFirst().orElseThrow();
        backend.votes = VoteState.NONE;
        backend.voted = null;

        Dialog dialog = open();
        dialog.send(new EventUiEvent.OpenEvent(0));
        assertThat(dialog.model.view()).isEqualTo(View.DETAILS);
        assertThat(dialog.model.details().previewLoading()).isTrue();
        assertLaidOut(dialog, "an event to come in " + language, "action:like", "action:dislike", "action:vote",
                "action:force", "action:map", "action:list", "action:edit");

        dialog.send(new EventUiEvent.ForceVote());
        assertThat(dialog.model.details().confirm()).isEqualTo(Confirm.FORCE);
        assertLaidOut(dialog, "a forced start being confirmed in " + language, "action:force", "action:confirm:cancel");

        backend.previews.getFirst().accept("xcore-map-preview", null);
        dialog.settle();
        assertThat(dialog.model.details().preview()).isEqualTo("xcore-map-preview");
        assertLaidOut(dialog, "an event with its map drawn in " + language);

        dialog.model = dialog.model.withVote(voteFor(upcoming))
                .withDetails(dialog.model.details().withConfirm(Confirm.NONE).withPreview(null, false));
        assertLaidOut(dialog, "an event being voted for in " + language, "action:stop_vote");

        dialog.model = dialog.model.withVote(voteFor(other));
        assertLaidOut(dialog, "an event while another is voted for in " + language);
        assertThat(LayoutAssert.actions(dialog.controller.render(dialog.model)))
                .doesNotContain("action:vote", "action:force", "action:stop_vote");

        dialog.send(new EventUiEvent.BackToList());
        dialog.send(new EventUiEvent.OpenActive());
        assertThat(dialog.model.details().event()).isSameAs(running);
        assertLaidOut(dialog, "the running event in " + language, "action:stop");
        dialog.send(new EventUiEvent.StopEvent());
        assertLaidOut(dialog, "a stop being confirmed in " + language, "action:stop", "action:confirm:cancel");

        dialog.send(new EventUiEvent.BackToList());
        dialog.send(new EventUiEvent.SelectTab(Tab.PAST));
        dialog.send(new EventUiEvent.OpenEvent(0));
        assertThat(dialog.model.details().event()).isSameAs(past);
        assertLaidOut(dialog, "an event that is over in " + language, "action:vote", "action:list");
        assertThat(LayoutAssert.actions(dialog.controller.render(dialog.model))).doesNotContain("action:edit", "action:map");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en", "be", "cs", "de", "es", "fr", "pl"})
    @DisplayName("the editor, the maps and the time fit every screen, with what the editor refuses said in words")
    void editor_isLaidOutForEveryScreen(String language) {
        session.localization = LayoutAssert.localization(language);
        crowded();
        staff();
        Dialog dialog = open();

        dialog.send(new EventUiEvent.Create());
        assertLaidOut(dialog, "a new event in " + language, "action:edit:map", "action:edit:start", "action:edit:end",
                "action:edit:temporary", "action:edit:major", "action:edit:save", "action:edit:cancel");

        Fields typed = new Fields("[#ff8800]ОченьДлинноеНазвание24", "Описание ".repeat(110));
        dialog.send(new EventUiEvent.EditorPress(typed, EditorAction.SAVE));
        assertThat(dialog.model.feedback()).isNotEmpty();
        assertLaidOut(dialog, "an event that lacks a map in " + language, "action:edit:save");

        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.PICK_MAP));
        assertLaidOut(dialog, "the maps in " + language, "action:maps:search", "action:maps:pick:0",
                "action:maps:pick:9", "action:maps:next", "action:editor");
        dialog.send(new EventUiEvent.SearchMaps("нет такой карты"));
        assertLaidOut(dialog, "no maps found in " + language, "action:maps:search", "action:editor");
        dialog.send(new EventUiEvent.SearchMaps(""));
        dialog.send(new EventUiEvent.ChooseMap(0));
        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.TOGGLE_TEMPORARY));
        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.TOGGLE_MAJOR));
        assertLaidOut(dialog, "an event with its map in " + language, "action:edit:map");

        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.PICK_START));
        assertLaidOut(dialog, "a start not set in " + language, "action:time:day_forth", "action:time:hour_back",
                "action:time:minute_forth", "action:time:set:now", "action:time:set:tomorrow_noon",
                "action:time:apply", "action:editor");
        dialog.send(new EventUiEvent.TimeShortcut(EventTime.Shortcut.TOMORROW_NOON));
        assertLaidOut(dialog, "a start in " + language, "action:time:clear");
        dialog.send(new EventUiEvent.ApplyTime());

        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.PICK_END));
        dialog.send(new EventUiEvent.TimeShortcut(EventTime.Shortcut.PLUS_THREE_HOURS));
        assertLaidOut(dialog, "an end in " + language, "action:time:set:plus_hour", "action:time:set:plus_day");
        dialog.send(new EventUiEvent.TimeStep(EventTime.Step.DAY_BACK));
        dialog.send(new EventUiEvent.TimeStep(EventTime.Step.DAY_BACK));
        dialog.send(new EventUiEvent.ApplyTime());
        assertThat(dialog.model.view()).isEqualTo(View.EDITOR);
        assertLaidOut(dialog, "an end before the start in " + language, "action:edit:save");

        // An event of someone else's, opened to be changed.
        EventData stored = backend.events.get(1);
        stored.author = new ObjectId();
        dialog.send(new EventUiEvent.CancelEdit());
        dialog.send(new EventUiEvent.OpenEvent(1));
        dialog.send(new EventUiEvent.Edit());
        assertThat(dialog.draft().id()).isEqualTo(stored.id);
        assertLaidOut(dialog, "an event being changed in " + language, "action:edit:save");
    }

    // =========================================================================
    // The list
    // =========================================================================

    @Test
    @DisplayName("what a player may do is read from their permissions when the menu opens")
    void viewer_followsPermissions() {
        assertThat(EventUiController.viewer(session))
                .isEqualTo(new Viewer(session.data.id, false, false, false, false, false));
        granted.add(PermissionNodes.EVENTS_FORCE_VOTE);
        granted.add(PermissionNodes.EVENTS_EDIT_OTHERS);
        assertThat(EventUiController.viewer(session))
                .isEqualTo(new Viewer(session.data.id, false, false, true, false, true));
        assertThat(EventUiController.viewer(null)).isEqualTo(Viewer.GUEST);
    }

    @Test
    @DisplayName("a turned page patches the list alone, another tab redraws the window")
    void pagesAndTabs() {
        crowded();
        Dialog dialog = open();

        UpdateResult<EventUiModel> next = dialog.send(new EventUiEvent.NextPage());
        assertThat(next.model().catalog().page()).isEqualTo(2);
        assertThat(next.model().catalog().events()).hasSize(2);
        assertThat(next.dirtySlots()).containsExactlyElementsOf(Screen.slots(EventUiController.SLOT_NOW,
                EventUiController.SLOT_LIST, EventUiController.SLOT_PAGER));
        assertThat(dialog.send(new EventUiEvent.NextPage()).isNoop()).isTrue();

        assertThat(dialog.send(new EventUiEvent.PrevPage()).model().catalog().page()).isEqualTo(1);
        assertThat(dialog.send(new EventUiEvent.PrevPage()).isNoop()).isTrue();

        UpdateResult<EventUiModel> past = dialog.send(new EventUiEvent.SelectTab(Tab.PAST));
        assertThat(past.fullRerender()).isTrue();
        assertThat(past.model().catalog().tab()).isEqualTo(Tab.PAST);
        assertThat(past.model().catalog().events()).hasSize(3);
        // The tab that is open is not read again by pressing it.
        backend.calls.clear();
        assertThat(dialog.send(new EventUiEvent.SelectTab(Tab.PAST)).isNoop()).isTrue();
        assertThat(backend.calls).isEmpty();

        assertThat(dialog.send(new EventUiEvent.SelectTab(Tab.MINE)).model().catalog().total()).isEqualTo(16);
        assertThat(dialog.send(new EventUiEvent.SelectTab(Tab.MAJOR)).model().catalog().events())
                .allMatch(event -> event.isMajor);
    }

    @Test
    @DisplayName("only the answer to the latest request reaches the dialog, and none reaches a dialog that is gone")
    void staleAnswers_areDropped() {
        crowded();
        Dialog dialog = open();

        dialog.press(new EventUiEvent.SelectTab(Tab.MAJOR));
        dialog.press(new EventUiEvent.SelectTab(Tab.PAST));
        dialog.settle();
        assertThat(dialog.delivered).hasSize(1);
        assertThat(dialog.model.catalog().tab()).isEqualTo(Tab.PAST);

        dialog.press(new EventUiEvent.SelectTab(Tab.MINE));
        session.setActiveUiSession(mock(UiSession.class));
        dialog.settle();
        assertThat(dialog.delivered).hasSize(1);
        assertThat(dialog.model.catalog().tab()).isEqualTo(Tab.PAST);
    }

    @Test
    @DisplayName("a list storage did not give is said to have failed, and the next one that comes takes the notice away")
    void failedLoad_isSaid() {
        crowded();
        Dialog dialog = open();

        backend.failing = true;
        UpdateResult<EventUiModel> failed = dialog.send(new EventUiEvent.Refresh());
        assertThat(failed.fullRerender()).isTrue();
        assertThat(failed.model().feedback()).contains("event-ui-load-failed");
        assertThat(failed.model().success()).isFalse();
        assertThat(failed.model().catalog().events()).hasSize(10);

        backend.failing = false;
        UpdateResult<EventUiModel> loaded = dialog.send(new EventUiEvent.Refresh());
        assertThat(loaded.fullRerender()).isTrue();
        assertThat(loaded.model().feedback()).isEmpty();
    }

    @Test
    @DisplayName("the way back is offered only where there is something to go back to")
    void back() {
        Dialog dialog = open();
        assertThat(dialog.send(new EventUiEvent.Back()).isNoop()).isTrue();
        assertThat(dialog.closed).isFalse();

        session.pushHistory(() -> {
        });
        dialog.send(new EventUiEvent.Back());
        assertThat(dialog.closed).isTrue();
        verify(menuService).goBack(session);
    }

    // =========================================================================
    // An event
    // =========================================================================

    @Test
    @DisplayName("an event opens with the viewer's own vote, and its map's picture comes when the client has it")
    void openEvent() {
        crowded();
        EventData second = backend.events.get(1);
        session.data.eventVotes.put(second.id.toString(), false);
        Dialog dialog = open();

        UpdateResult<EventUiModel> opened = dialog.send(new EventUiEvent.OpenEvent(1));
        assertThat(opened.fullRerender()).isTrue();
        assertThat(opened.model().view()).isEqualTo(View.DETAILS);
        assertThat(opened.model().details().event()).isSameAs(second);
        assertThat(opened.model().details().ownVote()).isFalse();
        assertThat(opened.model().details().previewLoading()).isTrue();
        assertThat(backend.previews).hasSize(1);

        // The picture of an event the player has left is not theirs to get.
        assertThat(dialog.send(new EventUiEvent.PreviewReady("another", "region")).isNoop()).isTrue();

        backend.previews.getFirst().accept("region", null);
        dialog.settle();
        assertThat(dialog.last.dirtySlots()).containsExactlyElementsOf(Screen.slots(EventUiController.SLOT_PREVIEW));
        assertThat(dialog.model.details().preview()).isEqualTo("region");
        assertThat(dialog.model.details().previewLoading()).isFalse();

        assertThat(dialog.send(new EventUiEvent.OpenEvent(99)).isNoop()).isTrue();
    }

    @Test
    @DisplayName("a picture the client already has is shown at once, one that cannot be made is given up on, and no map asks for none")
    void previews() {
        crowded();
        Dialog dialog = open();

        backend.cachedPreview = "cached";
        dialog.send(new EventUiEvent.OpenEvent(0));
        assertThat(dialog.model.details().preview()).isEqualTo("cached");
        assertThat(backend.previews).isEmpty();

        backend.cachedPreview = null;
        dialog.send(new EventUiEvent.BackToList());
        dialog.send(new EventUiEvent.OpenEvent(1));
        backend.previews.getFirst().accept(null, new IllegalStateException("no such map"));
        dialog.settle();
        assertThat(dialog.model.details().preview()).isNull();
        assertThat(dialog.model.details().previewLoading()).isFalse();

        backend.events.get(3).map = null;
        dialog.send(new EventUiEvent.BackToList());
        dialog.send(new EventUiEvent.OpenEvent(3));
        assertThat(dialog.model.details().map()).isNull();
        assertThat(dialog.model.details().previewLoading()).isFalse();
        assertThat(backend.previews).hasSize(1);
        assertThat(dialog.send(new EventUiEvent.OpenMap()).isNoop()).isTrue();
        assertThat(backend.calls).noneMatch(call -> call.startsWith("map:"));
    }

    @Test
    @DisplayName("the running event and the one voted for open from the rows above the tabs")
    void openFromNow() {
        crowded();
        Dialog dialog = open();

        dialog.send(new EventUiEvent.OpenActive());
        assertThat(dialog.model.details().event().isActive).isTrue();

        dialog.send(new EventUiEvent.BackToList());
        assertThat(dialog.model.view()).isEqualTo(View.LIST);
        assertThat(dialog.model.details()).isNull();
        dialog.send(new EventUiEvent.OpenVoted());
        assertThat(dialog.model.details().event()).isSameAs(backend.voted);

        // The vote ended while the list was open: its row goes, nothing opens.
        dialog.send(new EventUiEvent.BackToList());
        backend.voted = null;
        backend.votes = VoteState.NONE;
        UpdateResult<EventUiModel> gone = dialog.send(new EventUiEvent.OpenVoted());
        assertThat(gone.model().view()).isEqualTo(View.LIST);
        assertThat(gone.model().vote()).isEqualTo(VoteState.NONE);
        assertThat(gone.dirtySlots()).containsExactlyElementsOf(Screen.slots(EventUiController.SLOT_NOW));
    }

    @Test
    @DisplayName("an event that is gone when it is opened leaves the list with a notice")
    void openEvent_gone() {
        crowded();
        Dialog dialog = open();
        backend.events.remove(dialog.model.catalog().events().getFirst());

        UpdateResult<EventUiModel> opened = dialog.send(new EventUiEvent.OpenEvent(0));

        assertThat(opened.model().view()).isEqualTo(View.LIST);
        assertThat(opened.model().feedback()).contains("event-ui-load-failed");
    }

    @Test
    @DisplayName("a vote on an event patches its buttons alone, and one that changes nothing changes nothing")
    void rate() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.OpenEvent(0));

        UpdateResult<EventUiModel> liked = dialog.send(new EventUiEvent.Rate(true));
        assertThat(liked.model().details().ownVote()).isTrue();
        assertThat(liked.dirtySlots()).containsExactlyElementsOf(Screen.slots(EventUiController.SLOT_RATING));

        backend.rateChanges = false;
        assertThat(dialog.send(new EventUiEvent.Rate(false)).isNoop()).isTrue();
        assertThat(dialog.model.details().ownVote()).isTrue();
        assertThat(backend.calls).contains("like", "dislike");
    }

    @Test
    @DisplayName("starting a vote closes the dialog for the chat, unless another vote is on")
    void startVote() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.OpenEvent(0));

        UpdateResult<EventUiModel> busy = dialog.send(new EventUiEvent.StartVote());
        assertThat(busy.model().feedback()).contains("event-ui-vote-busy");
        assertThat(dialog.closed).isFalse();
        assertThat(backend.calls).noneMatch(call -> call.startsWith("vote:"));

        backend.votes = VoteState.NONE;
        dialog.send(new EventUiEvent.StartVote());
        assertThat(dialog.closed).isTrue();
        assertThat(backend.calls).contains("vote:" + backend.events.getFirst().name);
    }

    @Test
    @DisplayName("an event that is over can be voted for again, the one that runs cannot")
    void startVote_byState() {
        crowded();
        backend.votes = VoteState.NONE;
        Dialog dialog = open();

        dialog.send(new EventUiEvent.OpenActive());
        assertThat(dialog.send(new EventUiEvent.StartVote()).isNoop()).isTrue();
        assertThat(dialog.closed).isFalse();

        dialog.send(new EventUiEvent.BackToList());
        dialog.send(new EventUiEvent.SelectTab(Tab.PAST));
        dialog.send(new EventUiEvent.OpenEvent(0));
        dialog.send(new EventUiEvent.StartVote());
        assertThat(dialog.closed).isTrue();
    }

    @Test
    @DisplayName("a forced start is for those allowed it and takes a second press")
    void forceVote() {
        crowded();
        backend.votes = VoteState.NONE;
        Dialog dialog = open();
        dialog.send(new EventUiEvent.OpenEvent(0));

        assertThat(dialog.send(new EventUiEvent.ForceVote()).isNoop()).isTrue();
        verify(keys).send(PermissionService.ACCESS_DENIED_KEY);

        granted.add(PermissionNodes.EVENTS_FORCE_VOTE);
        UpdateResult<EventUiModel> asked = dialog.send(new EventUiEvent.ForceVote());
        assertThat(asked.model().details().confirm()).isEqualTo(Confirm.FORCE);
        assertThat(asked.dirtySlots()).containsExactlyElementsOf(Screen.slots(EventUiController.SLOT_LAUNCH));
        assertThat(dialog.closed).isFalse();

        UpdateResult<EventUiModel> cancelled = dialog.send(new EventUiEvent.CancelConfirm());
        assertThat(cancelled.model().details().confirm()).isEqualTo(Confirm.NONE);
        assertThat(dialog.send(new EventUiEvent.CancelConfirm()).isNoop()).isTrue();

        dialog.send(new EventUiEvent.ForceVote());
        dialog.send(new EventUiEvent.ForceVote());
        assertThat(dialog.closed).isTrue();
        assertThat(backend.calls).containsOnlyOnce("force:" + backend.events.getFirst().name);
    }

    @Test
    @DisplayName("stopping the running event is for those allowed it, takes a second press and returns to the list")
    void stopEvent() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.OpenActive());

        assertThat(dialog.send(new EventUiEvent.StopEvent()).isNoop()).isTrue();
        assertThat(backend.calls).doesNotContain("finish");

        granted.add(PermissionNodes.EVENTS_STOP);
        UpdateResult<EventUiModel> asked = dialog.send(new EventUiEvent.StopEvent());
        assertThat(asked.model().details().confirm()).isEqualTo(Confirm.STOP);
        assertThat(backend.calls).doesNotContain("finish");

        UpdateResult<EventUiModel> stopped = dialog.send(new EventUiEvent.StopEvent());
        assertThat(backend.calls).containsOnlyOnce("finish");
        assertThat(stopped.fullRerender()).isTrue();
        assertThat(stopped.model().view()).isEqualTo(View.LIST);
        assertThat(stopped.model().details()).isNull();
        assertThat(stopped.model().catalog().active()).isNull();
        assertThat(stopped.model().feedback()).contains("event-ui-stopped");
        assertThat(stopped.model().success()).isTrue();
    }

    @Test
    @DisplayName("the vote for an event is ended by those allowed to end votes")
    void stopVote() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.OpenVoted());

        assertThat(dialog.send(new EventUiEvent.StopVote()).isNoop()).isTrue();
        assertThat(backend.calls).doesNotContain("stop_vote");

        granted.add(PermissionNodes.VOTES_CANCEL);
        UpdateResult<EventUiModel> stopped = dialog.send(new EventUiEvent.StopVote());
        assertThat(backend.calls).contains("stop_vote");
        assertThat(stopped.model().vote()).isEqualTo(VoteState.NONE);
        assertThat(stopped.model().feedback()).contains("event-ui-vote-stopped");
    }

    @Test
    @DisplayName("the map of an event opens in the maps menu")
    void openMap() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.OpenEvent(0));

        dialog.send(new EventUiEvent.OpenMap());

        assertThat(backend.calls).contains("map:Ancient Caldera [Of] The Very Long Map Name");
    }

    // =========================================================================
    // The editor
    // =========================================================================

    @Test
    @DisplayName("an event is changed by its author and by staff, and nobody else is let into its editor")
    void edit() {
        crowded();
        EventData own = backend.events.get(1);
        EventData theirs = backend.events.get(2);
        theirs.author = new ObjectId();
        Dialog dialog = open();

        dialog.send(new EventUiEvent.OpenEvent(1));
        dialog.send(new EventUiEvent.Edit());
        assertThat(dialog.model.view()).isEqualTo(View.EDITOR);
        assertThat(dialog.draft().id()).isEqualTo(own.id);
        assertThat(dialog.draft().map().id()).isEqualTo(own.map);

        // Leaving the editor of a stored event returns to the event.
        dialog.send(new EventUiEvent.CancelEdit());
        assertThat(dialog.model.view()).isEqualTo(View.DETAILS);
        assertThat(dialog.model.editing()).isNull();

        dialog.send(new EventUiEvent.BackToList());
        dialog.send(new EventUiEvent.OpenEvent(2));
        UpdateResult<EventUiModel> refused = dialog.send(new EventUiEvent.Edit());
        assertThat(refused.model().view()).isEqualTo(View.DETAILS);
        assertThat(refused.model().feedback()).contains(Problem.NOT_ALLOWED.key());

        granted.add(PermissionNodes.EVENTS_EDIT_OTHERS);
        assertThat(dialog.send(new EventUiEvent.Edit()).model().view()).isEqualTo(View.EDITOR);

        // A major event is its author's to change only while they may make such.
        dialog.send(new EventUiEvent.CancelEdit());
        dialog.send(new EventUiEvent.BackToList());
        granted.clear();
        dialog.send(new EventUiEvent.OpenEvent(0));
        assertThat(dialog.model.details().event().isMajor).isTrue();
        assertThat(dialog.send(new EventUiEvent.Edit()).model().feedback()).contains(Problem.NOT_ALLOWED.key());
    }

    @Test
    @DisplayName("every press of the editor takes what the fields hold, and a switch patches the switches alone")
    void editorPresses() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.Create());
        assertThat(dialog.draft()).isEqualTo(Draft.blank(null));

        UpdateResult<EventUiModel> temporary = dialog.send(new EventUiEvent.EditorPress(
                new Fields("Турнир", "Командный"), EditorAction.TOGGLE_TEMPORARY));
        assertThat(temporary.dirtySlots()).containsExactlyElementsOf(Screen.slots(EventUiController.SLOT_FLAGS));
        assertThat(dialog.draft().name()).isEqualTo("Турнир");
        assertThat(dialog.draft().description()).isEqualTo("Командный");
        assertThat(dialog.draft().temporary()).isTrue();

        // A press that came without the fields keeps what was typed before.
        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.TOGGLE_TEMPORARY));
        assertThat(dialog.draft().name()).isEqualTo("Турнир");
        assertThat(dialog.draft().temporary()).isFalse();

        UpdateResult<EventUiModel> refused = dialog.send(new EventUiEvent.EditorPress(
                new Fields("Турнир 2", null), EditorAction.TOGGLE_MAJOR));
        assertThat(refused.isNoop()).isTrue();
        assertThat(dialog.draft().major()).isFalse();
        assertThat(dialog.draft().name()).isEqualTo("Турнир 2");
        assertThat(dialog.draft().description()).isEqualTo("Командный");
        verify(keys).send(PermissionService.ACCESS_DENIED_KEY);

        granted.add(PermissionNodes.EVENTS_CREATE_MAJOR);
        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.TOGGLE_MAJOR));
        assertThat(dialog.draft().major()).isTrue();
    }

    @Test
    @DisplayName("an event that cannot be saved is not sent to storage, and the editor says what it lacks")
    void save_refused() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.Create());

        UpdateResult<EventUiModel> noMap = dialog.send(new EventUiEvent.EditorPress(
                new Fields("Турнир", ""), EditorAction.SAVE));
        assertThat(noMap.fullRerender()).isTrue();
        assertThat(noMap.model().feedback()).contains(Problem.NO_MAP.key());
        assertThat(noMap.model().view()).isEqualTo(View.EDITOR);

        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.PICK_MAP));
        dialog.send(new EventUiEvent.ChooseMap(0));
        // A switch takes the notice of the refused save away with it.
        dialog.model = dialog.model.withFeedback("refused", false);
        UpdateResult<EventUiModel> temporary = dialog.send(new EventUiEvent.EditorPress(null, EditorAction.TOGGLE_TEMPORARY));
        assertThat(temporary.fullRerender()).isTrue();
        assertThat(temporary.model().feedback()).isEmpty();

        assertThat(dialog.send(new EventUiEvent.EditorPress(null, EditorAction.SAVE)).model().feedback())
                .contains(Problem.NO_END.key());
    }

    @Test
    @DisplayName("a saved event opens with a notice, is stored once however often Save is pressed, and its draft is let go")
    void save() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.Create());
        dialog.send(new EventUiEvent.EditorPress(new Fields("", "Командный турнир"), EditorAction.PICK_MAP));
        dialog.send(new EventUiEvent.ChooseMap(1));
        assertThat(session.hasDraft(Draft.class)).isTrue();

        dialog.press(new EventUiEvent.EditorPress(null, EditorAction.SAVE));
        dialog.press(new EventUiEvent.EditorPress(null, EditorAction.SAVE));
        dialog.settle();

        assertThat(backend.saved).hasSize(1);
        assertThat(backend.saved.getFirst().map()).isEqualTo(backend.maps.get(1));
        assertThat(dialog.model.view()).isEqualTo(View.DETAILS);
        assertThat(dialog.model.editing()).isNull();
        assertThat(dialog.model.feedback()).contains("event-ui-saved");
        assertThat(dialog.model.success()).isTrue();
        assertThat(dialog.model.details().event().name).isEqualTo("event-menu-create-start-default");
        assertThat(dialog.model.details().event().author).isEqualTo(session.data.id);
        assertThat(session.hasDraft(Draft.class)).isFalse();
    }

    @DisplayName("closing during a save discards a saved draft and preserves a refused one")
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void save_afterDialogClosed(boolean succeeds) {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.Create());
        dialog.send(new EventUiEvent.EditorPress(new Fields("Турнир", "Командный"), EditorAction.PICK_MAP));
        dialog.send(new EventUiEvent.ChooseMap(0));
        Draft draft = dialog.draft();
        if (!succeeds) {
            backend.saveProblem = Problem.FAILED;
        }

        dialog.press(new EventUiEvent.EditorPress(null, EditorAction.SAVE));
        dialog.press(new EventUiEvent.Close());
        session.setActiveUiSession(null);
        dialog.settle();

        assertThat(dialog.closed).isTrue();
        assertThat(dialog.delivered).noneMatch(EventUiEvent.Saved.class::isInstance);
        assertThat(backend.saved).containsExactly(draft);
        assertThat(session.hasDraft(Draft.class)).isEqualTo(!succeeds);
        Dialog reopened = open();
        reopened.send(new EventUiEvent.Create());
        if (succeeds) {
            assertThat(reopened.draft()).isEqualTo(Draft.blank(null));
            assertThat(reopened.model.feedback()).isEmpty();
        } else {
            assertThat(reopened.draft()).isEqualTo(draft);
            assertThat(reopened.model.feedback()).contains("event-ui-draft-restored");
        }
    }

    @Test
    @DisplayName("a late save completion keeps a replacement draft created in another dialog")
    void save_keepsReplacementDraft() {
        crowded();
        Dialog first = open();
        first.send(new EventUiEvent.Create());
        first.send(new EventUiEvent.EditorPress(new Fields("Первый", ""), EditorAction.PICK_MAP));
        first.send(new EventUiEvent.ChooseMap(0));
        first.press(new EventUiEvent.EditorPress(null, EditorAction.SAVE));
        first.press(new EventUiEvent.Close());

        Dialog second = open();
        second.press(new EventUiEvent.Create());
        second.press(new EventUiEvent.CancelEdit());
        second.press(new EventUiEvent.Create());
        second.press(new EventUiEvent.EditorPress(new Fields("Второй", ""), EditorAction.TOGGLE_TEMPORARY));
        Draft replacement = second.draft();
        first.settle();

        assertThat(backend.saved).hasSize(1);
        assertThat(first.delivered).noneMatch(EventUiEvent.Saved.class::isInstance);
        assertThat(session.getDraft(Draft.class)).isSameAs(replacement);
        Dialog reopened = open();
        reopened.send(new EventUiEvent.Create());
        assertThat(reopened.draft()).isEqualTo(replacement);
    }

    @Test
    @DisplayName("an event stored without an id to read it back by is shown in the list, with the same notice")
    void save_withoutId() {
        crowded();
        backend.forgetsId = true;
        Dialog dialog = open();
        dialog.send(new EventUiEvent.Create());
        dialog.send(new EventUiEvent.EditorPress(new Fields("Турнир", ""), EditorAction.PICK_MAP));
        dialog.send(new EventUiEvent.ChooseMap(0));

        UpdateResult<EventUiModel> saved = dialog.send(new EventUiEvent.EditorPress(null, EditorAction.SAVE));

        assertThat(saved.fullRerender()).isTrue();
        assertThat(saved.model().view()).isEqualTo(View.LIST);
        assertThat(saved.model().editing()).isNull();
        assertThat(saved.model().catalog().total()).isEqualTo(13);
        assertThat(saved.model().feedback()).contains("event-ui-saved");
        assertThat(session.hasDraft(Draft.class)).isFalse();
    }

    @Test
    @DisplayName("a save storage refused keeps the editor open with the reason, and can be tried again")
    void save_failed() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.OpenEvent(1));
        dialog.send(new EventUiEvent.Edit());

        backend.saveProblem = Problem.GONE;
        UpdateResult<EventUiModel> gone = dialog.send(new EventUiEvent.EditorPress(
                new Fields("Новое имя", "Новый текст"), EditorAction.SAVE));
        assertThat(gone.model().view()).isEqualTo(View.EDITOR);
        assertThat(gone.model().feedback()).contains(Problem.GONE.key());
        assertThat(dialog.draft().name()).isEqualTo("Новое имя");

        backend.saveProblem = null;
        backend.failing = true;
        assertThat(dialog.send(new EventUiEvent.EditorPress(null, EditorAction.SAVE)).model().feedback())
                .contains(Problem.FAILED.key());

        backend.failing = false;
        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.SAVE));
        assertThat(backend.saved).hasSize(3);
        assertThat(dialog.model.view()).isEqualTo(View.DETAILS);
        assertThat(dialog.model.details().event().name).isEqualTo("Новое имя");
        assertThat(dialog.model.feedback()).contains("event-ui-saved");
    }

    @Test
    @DisplayName("a new event closed by accident is there when the editor is opened again, and cancelled it is gone")
    void draft_isKept() {
        crowded();
        Dialog first = open();
        first.send(new EventUiEvent.Create());
        assertThat(first.model.feedback()).isEmpty();
        first.send(new EventUiEvent.EditorPress(new Fields("Турнир", "Командный"), EditorAction.TOGGLE_TEMPORARY));

        Dialog second = open();
        UpdateResult<EventUiModel> resumed = second.send(new EventUiEvent.Create());
        assertThat(resumed.model().feedback()).contains("event-ui-draft-restored");
        assertThat(second.draft().name()).isEqualTo("Турнир");
        assertThat(second.draft().temporary()).isTrue();

        // Made for a map from the maps menu, it is still that event, now for that map.
        MapChoice map = backend.maps.get(4);
        EventUiModel forMap = second.controller.creating(EventUiController.viewer(session), map);
        assertThat(forMap.view()).isEqualTo(View.EDITOR);
        assertThat(forMap.editing().draft().name()).isEqualTo("Турнир");
        assertThat(forMap.editing().draft().map()).isEqualTo(map);

        UpdateResult<EventUiModel> cancelled = second.send(new EventUiEvent.CancelEdit());
        assertThat(cancelled.model().view()).isEqualTo(View.LIST);
        assertThat(cancelled.model().editing()).isNull();
        assertThat(session.hasDraft(Draft.class)).isFalse();
        assertThat(open().send(new EventUiEvent.Create()).model().feedback()).isEmpty();
    }

    @Test
    @DisplayName("an editor opened from the maps menu leaves to a list that is then read")
    void creating_leavesToTheList() {
        crowded();
        EventUiController controller = new EventUiController(backend, async, session, CLOCK);
        Dialog dialog = new Dialog(controller.creating(EventUiController.viewer(session), backend.maps.getFirst()));
        assertThat(dialog.model.catalog().loaded()).isFalse();

        dialog.send(new EventUiEvent.CancelEdit());

        assertThat(dialog.model.view()).isEqualTo(View.LIST);
        assertThat(dialog.model.catalog().loaded()).isTrue();
        assertThat(dialog.model.catalog().events()).hasSize(10);
    }

    // =========================================================================
    // The maps and the time
    // =========================================================================

    @Test
    @DisplayName("the maps are paged and searched, and the one pressed becomes the event's")
    void maps() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.Create());
        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.PICK_MAP));
        assertThat(dialog.model.view()).isEqualTo(View.MAPS);

        UpdateResult<EventUiModel> second = dialog.send(new EventUiEvent.MapsPage(1));
        assertThat(second.model().editing().mapsPage()).isEqualTo(2);
        assertThat(second.dirtySlots()).containsExactlyElementsOf(Screen.slots(EventUiController.SLOT_MAPS,
                EventUiController.SLOT_MAPS_PAGER));
        assertThat(LayoutAssert.actions(dialog.controller.render(dialog.model)))
                .contains("action:maps:pick:10", "action:maps:pick:19", "action:maps:prev", "action:maps:next")
                .doesNotContain("action:maps:pick:9", "action:maps:pick:20");
        assertThat(dialog.send(new EventUiEvent.MapsPage(1)).model().editing().mapsPage()).isEqualTo(3);
        assertThat(dialog.send(new EventUiEvent.MapsPage(1)).model().editing().mapsPage()).isEqualTo(3);
        assertThat(dialog.send(new EventUiEvent.MapsPage(-5)).model().editing().mapsPage()).isEqualTo(1);

        UpdateResult<EventUiModel> found = dialog.send(new EventUiEvent.SearchMaps("  КАРТА 1 "));
        assertThat(found.model().editing().found()).extracting(MapChoice::name)
                .containsExactly("Карта 1", "Карта 11", "Карта 13", "Карта 15", "Карта 17", "Карта 19");
        assertThat(dialog.send(new EventUiEvent.ChooseMap(6)).isNoop()).isTrue();

        // The position is one of what was found, not of all the maps.
        UpdateResult<EventUiModel> chosen = dialog.send(new EventUiEvent.ChooseMap(2));
        assertThat(chosen.model().view()).isEqualTo(View.EDITOR);
        assertThat(dialog.draft().map().name()).isEqualTo("Карта 13");
        assertThat(session.getDraft(Draft.class).map().name()).isEqualTo("Карта 13");

        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.PICK_MAP));
        assertThat(dialog.model.editing().mapsQuery()).isEmpty();
        assertThat(dialog.send(new EventUiEvent.BackToEditor()).model().view()).isEqualTo(View.EDITOR);
        assertThat(dialog.draft().map().name()).isEqualTo("Карта 13");
    }

    @Test
    @DisplayName("a time is set by steps and shortcuts and becomes the event's only when confirmed")
    void time() {
        crowded();
        Dialog dialog = open();
        dialog.send(new EventUiEvent.Create());

        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.PICK_START));
        assertThat(dialog.model.view()).isEqualTo(View.TIME);
        assertThat(dialog.model.editing().field()).isEqualTo(TimeField.START);
        assertThat(dialog.model.editing().picked()).isZero();

        UpdateResult<EventUiModel> stepped = dialog.send(new EventUiEvent.TimeStep(EventTime.Step.HOUR_FORTH));
        assertThat(stepped.dirtySlots()).containsExactlyElementsOf(Screen.slots(EventUiController.SLOT_TIME));
        assertThat(stepped.model().editing().picked()).isEqualTo(EventTime.step(0L, EventTime.Step.HOUR_FORTH, CLOCK));

        // Left without confirming, the screen changed nothing.
        dialog.send(new EventUiEvent.BackToEditor());
        assertThat(dialog.draft().plannedStart()).isZero();

        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.PICK_START));
        dialog.send(new EventUiEvent.TimeShortcut(EventTime.Shortcut.TOMORROW_NOON));
        long start = EventTime.shortcut(EventTime.Shortcut.TOMORROW_NOON, 0L, CLOCK);
        UpdateResult<EventUiModel> applied = dialog.send(new EventUiEvent.ApplyTime());
        assertThat(applied.model().view()).isEqualTo(View.EDITOR);
        assertThat(applied.model().feedback()).isEmpty();
        assertThat(dialog.draft().plannedStart()).isEqualTo(start);
        assertThat(session.getDraft(Draft.class).plannedStart()).isEqualTo(start);

        // The end counts from the start.
        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.PICK_END));
        assertThat(dialog.model.editing().field()).isEqualTo(TimeField.END);
        dialog.send(new EventUiEvent.TimeShortcut(EventTime.Shortcut.PLUS_THREE_HOURS));
        assertThat(dialog.model.editing().picked()).isEqualTo(start + 3 * HOUR);
        dialog.send(new EventUiEvent.TimeStep(EventTime.Step.DAY_BACK));
        UpdateResult<EventUiModel> early = dialog.send(new EventUiEvent.ApplyTime());
        assertThat(dialog.draft().plannedEnd()).isEqualTo(start + 3 * HOUR - DAY);
        assertThat(early.model().feedback()).contains(Problem.END_BEFORE_START.key());

        dialog.send(new EventUiEvent.EditorPress(null, EditorAction.PICK_END));
        assertThat(dialog.model.editing().picked()).isEqualTo(start + 3 * HOUR - DAY);
        dialog.send(new EventUiEvent.ClearTime());
        dialog.send(new EventUiEvent.ApplyTime());
        assertThat(dialog.draft().plannedEnd()).isZero();
        assertThat(dialog.model.feedback()).isEmpty();
    }

    // =========================================================================
    // Presses
    // =========================================================================

    private static MenuResult result(String action) {
        return new MenuResult(action);
    }

    @Test
    @DisplayName("every button of the menu is read back as the event it stands for")
    void parseEvent() {
        EventUiController controller = new EventUiController(backend, async, session, CLOCK);
        Map<String, EventUiEvent> presses = new HashMap<>();
        presses.put("action:tab:major", new EventUiEvent.SelectTab(Tab.MAJOR));
        presses.put("action:page:next", new EventUiEvent.NextPage());
        presses.put("action:page:prev", new EventUiEvent.PrevPage());
        presses.put("action:refresh", new EventUiEvent.Refresh());
        presses.put("action:event:7", new EventUiEvent.OpenEvent(7));
        presses.put("action:active", new EventUiEvent.OpenActive());
        presses.put("action:voted", new EventUiEvent.OpenVoted());
        presses.put("action:create", new EventUiEvent.Create());
        presses.put("action:list", new EventUiEvent.BackToList());
        presses.put("action:like", new EventUiEvent.Rate(true));
        presses.put("action:dislike", new EventUiEvent.Rate(false));
        presses.put("action:vote", new EventUiEvent.StartVote());
        presses.put("action:force", new EventUiEvent.ForceVote());
        presses.put("action:stop", new EventUiEvent.StopEvent());
        presses.put("action:stop_vote", new EventUiEvent.StopVote());
        presses.put("action:confirm:cancel", new EventUiEvent.CancelConfirm());
        presses.put("action:map", new EventUiEvent.OpenMap());
        presses.put("action:edit", new EventUiEvent.Edit());
        presses.put("action:edit:cancel", new EventUiEvent.CancelEdit());
        presses.put("action:edit:map", new EventUiEvent.EditorPress(null, EditorAction.PICK_MAP));
        presses.put("action:edit:start", new EventUiEvent.EditorPress(null, EditorAction.PICK_START));
        presses.put("action:edit:end", new EventUiEvent.EditorPress(null, EditorAction.PICK_END));
        presses.put("action:edit:temporary", new EventUiEvent.EditorPress(null, EditorAction.TOGGLE_TEMPORARY));
        presses.put("action:edit:major", new EventUiEvent.EditorPress(null, EditorAction.TOGGLE_MAJOR));
        presses.put("action:edit:save", new EventUiEvent.EditorPress(null, EditorAction.SAVE));
        presses.put("action:maps:search", new EventUiEvent.SearchMaps(""));
        presses.put("action:maps:prev", new EventUiEvent.MapsPage(-1));
        presses.put("action:maps:next", new EventUiEvent.MapsPage(1));
        presses.put("action:maps:pick:12", new EventUiEvent.ChooseMap(12));
        presses.put("action:editor", new EventUiEvent.BackToEditor());
        presses.put("action:time:hour_back", new EventUiEvent.TimeStep(EventTime.Step.HOUR_BACK));
        presses.put("action:time:set:in_hour", new EventUiEvent.TimeShortcut(EventTime.Shortcut.IN_HOUR));
        presses.put("action:time:clear", new EventUiEvent.ClearTime());
        presses.put("action:time:apply", new EventUiEvent.ApplyTime());
        presses.put("action:back", new EventUiEvent.Back());
        presses.forEach((action, event) ->
                assertThat(controller.parseEvent(result(action))).as(action).isEqualTo(event));

        // A dialog closed without a press, and a press that is not of this menu.
        assertThat(controller.parseEvent(new MenuResult())).isEqualTo(new EventUiEvent.Close());
        assertThat(controller.parseEvent(null)).isEqualTo(new EventUiEvent.Close());
        for (String unknown : List.of("action:nonsense", "action:tab:nonsense", "action:event:x",
                "action:maps:pick:", "action:edit:nonsense", "action:time:nonsense", "action:time:set:nonsense")) {
            assertThat(controller.parseEvent(result(unknown))).as(unknown).isNull();
        }
    }

    @Test
    @DisplayName("a press of the editor and of the search comes with what the fields hold")
    void parseEvent_readsFields() {
        EventUiController controller = new EventUiController(backend, async, session, CLOCK);

        MenuResult save = result("action:edit:save");
        save.values.put(EventUiController.FIELD_NAME, "Турнир");
        save.values.put(EventUiController.FIELD_DESCRIPTION, "Командный");
        assertThat(controller.parseEvent(save)).isEqualTo(
                new EventUiEvent.EditorPress(new Fields("Турнир", "Командный"), EditorAction.SAVE));

        MenuResult search = result("action:maps:search");
        search.values.put(EventUiController.FIELD_MAP_QUERY, "caldera");
        assertThat(controller.parseEvent(search)).isEqualTo(new EventUiEvent.SearchMaps("caldera"));
    }

    @Test
    @DisplayName("an event of another screen changes nothing on the one that is open")
    void eventsOfOtherScreens_areIgnored() {
        crowded();
        Dialog dialog = open();
        List<EventUiEvent> foreign = List.of(new EventUiEvent.BackToList(), new EventUiEvent.Rate(true),
                new EventUiEvent.StartVote(), new EventUiEvent.ForceVote(), new EventUiEvent.StopEvent(),
                new EventUiEvent.StopVote(), new EventUiEvent.CancelConfirm(), new EventUiEvent.OpenMap(),
                new EventUiEvent.Edit(), new EventUiEvent.EditorPress(new Fields("a", "b"), EditorAction.SAVE),
                new EventUiEvent.CancelEdit(), new EventUiEvent.SearchMaps("a"), new EventUiEvent.MapsPage(1),
                new EventUiEvent.ChooseMap(0), new EventUiEvent.BackToEditor(),
                new EventUiEvent.TimeStep(EventTime.Step.DAY_FORTH),
                new EventUiEvent.TimeShortcut(EventTime.Shortcut.NOW), new EventUiEvent.ClearTime(),
                new EventUiEvent.ApplyTime());
        EventUiModel before = dialog.model;
        backend.calls.clear();

        for (EventUiEvent event : foreign) {
            UpdateResult<EventUiModel> result = dialog.send(event);
            assertThat(result.isNoop()).as("%s", event).isTrue();
            assertThat(result.model()).as("%s", event).isEqualTo(before);
        }

        assertThat(dialog.closed).isFalse();
        assertThat(backend.calls).isEmpty();
        assertThat(backend.saved).isEmpty();
        verify(keys, never()).send(anyString());
    }
}
