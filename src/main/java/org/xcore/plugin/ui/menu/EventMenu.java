package org.xcore.plugin.ui.menu;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.EventEditorService;
import org.xcore.plugin.service.EventEditorService.Draft;
import org.xcore.plugin.service.EventEditorService.Editor;
import org.xcore.plugin.service.EventEditorService.MapChoice;
import org.xcore.plugin.service.EventEditorService.SaveResult;
import org.xcore.plugin.service.EventService;
import org.xcore.plugin.service.EventViewService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.service.map.MapPreviewService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.menu.event.EventUiBackend;
import org.xcore.plugin.ui.menu.event.EventUiController;
import org.xcore.plugin.ui.menu.event.EventUiModel.Catalog;
import org.xcore.plugin.ui.menu.event.EventUiModel.Tab;
import org.xcore.plugin.ui.menu.event.EventUiModel.Viewer;
import org.xcore.plugin.ui.menu.event.EventUiModel.VoteState;
import org.xcore.plugin.vote.VoteEvent;
import org.xcore.plugin.vote.VoteService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Entry point of the events menu ({@code /event}, {@code /events}), and what that menu asks of
 * the server: the events and their votes, the maps an event can be given, the save of an event.
 */
@Singleton
public class EventMenu extends Menu implements EventUiBackend {

    private final MapService mapService;
    private final EventService eventService;
    private final EventEditorService eventEditorService;
    private final EventViewService eventViewService;
    private final VoteService voteService;
    private final Provider<MapMenu> mapMenu;
    private final MapPreviewService mapPreviewService;
    private final MenuService menuService;
    private final Async async;
    private final Clock clock;

    @Inject
    public EventMenu(TomlSecretsConfig secretsConfig, SessionService sessionService,
                     MapService mapService, EventService eventService, EventEditorService eventEditorService,
                     EventViewService eventViewService, VoteService voteService, Provider<MapMenu> mapMenu,
                     MapPreviewService mapPreviewService, MenuService menuService, Async async) {
        // The server's own zone: the times of an event are the ones its staff plans by.
        this(secretsConfig, sessionService, mapService, eventService, eventEditorService, eventViewService,
                voteService, mapMenu, mapPreviewService, menuService, async, Clock.systemDefaultZone());
    }

    public EventMenu(TomlSecretsConfig secretsConfig, SessionService sessionService,
                     MapService mapService, EventService eventService, EventEditorService eventEditorService,
                     EventViewService eventViewService, VoteService voteService, Provider<MapMenu> mapMenu,
                     MapPreviewService mapPreviewService, MenuService menuService, Async async, Clock clock) {
        super(secretsConfig, sessionService);
        this.mapService = mapService;
        this.eventService = eventService;
        this.eventEditorService = eventEditorService;
        this.eventViewService = eventViewService;
        this.voteService = voteService;
        this.mapMenu = mapMenu;
        this.mapPreviewService = mapPreviewService;
        this.menuService = menuService;
        this.async = async;
        this.clock = clock;
    }

    /** {@code /event}: the events to come, with the one that runs and the vote for another above them. */
    public void main(String uuid) {
        events(uuid, 1);
    }

    /** {@code /events}: the list on {@code page}, opened once that page has been read off the game thread. */
    public void events(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null || session.player == null) return;
        session.clear();

        EventUiController controller = controller(session);
        Viewer viewer = EventUiController.viewer(session);
        long requested = session.nextUiVersion();
        async.supply(() -> controller.fetch(Tab.UPCOMING, page, viewer)).thenMain((catalog, error) -> {
            if (session.uiVersion() != requested) {
                return; // The player opened something else while this was loading.
            }
            if (error != null || catalog == null) {
                Log.err("Failed to load the events for " + uuid, error);
                if (session.locale() != null) {
                    session.locale().send("event-ui-load-failed");
                }
                return;
            }
            menuService.openUi(session, controller, controller.model(viewer, catalog), true);
        });
    }

    /**
     * The editor of a new event.
     *
     * @param map the map the event is for; {@code null} leaves it to be picked
     */
    public void createStart(String uuid, @Nullable MapData map) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null || session.player == null) return;
        session.clear();

        EventUiController controller = controller(session);
        menuService.openUi(session, controller,
                controller.creating(EventUiController.viewer(session), map == null ? null : MapChoice.of(map)), true);
    }

    EventUiController controller(Session session) {
        return new EventUiController(this, async, session, clock);
    }

    // =========================================================================
    // What the menu asks of the server
    // =========================================================================

    @Override
    public Catalog catalog(Tab tab, int page, Viewer viewer) {
        EventData active = eventViewService.activeEvent();
        if (tab == Tab.MINE && viewer.id() == null) {
            // A player not stored yet has written nothing; a search by no author would find the events without one.
            return new Catalog(tab, 1, 1, 0, List.of(), active);
        }
        var found = eventViewService.page(page, secretsConfig.pagination.eventsPerPage, tab.listing(), viewer.id());
        return new Catalog(tab, found.page(), found.totalPages(), found.total(), found.events(), active);
    }

    @Override
    public @Nullable Stored event(ObjectId id) {
        var details = eventViewService.details(eventViewService.findById(id));
        if (details.event() == null) {
            return null;
        }
        return new Stored(details.event(), details.map(), name(details.author()));
    }

    private static String name(@Nullable PlayerData author) {
        if (author == null) return "";
        if (author.customNickname != null && !author.customNickname.isBlank()) return author.customNickname;
        return author.nickname != null ? author.nickname : "";
    }

    @Override
    public SaveResult save(Draft draft, Editor editor, String fallbackName) {
        return eventEditorService.save(draft, editor, fallbackName);
    }

    @Override
    public void finishActiveEvent() {
        eventService.finishActiveEvent();
    }

    @Override
    public VoteState votes() {
        if (!voteService.isVoting()) {
            return VoteState.NONE;
        }
        if (voteService.getCurrentSession() instanceof VoteEvent vote && vote.target != null) {
            EventData target = vote.target;
            return new VoteState(true, target.id == null ? null : target.id.toHexString(),
                    target.name == null ? "" : target.name, vote.votes(), vote.votesRequired());
        }
        return new VoteState(true, null, "", 0, 0);
    }

    @Override
    public @Nullable EventData votedEvent() {
        return voteService.getCurrentSession() instanceof VoteEvent vote ? vote.target : null;
    }

    @Override
    public void startVote(Player player, EventData event, boolean forced) {
        eventService.startVoteSession(player, event, forced);
    }

    @Override
    public void stopVote() {
        if (voteService.getCurrentSession() instanceof VoteEvent) {
            voteService.endVote();
        }
    }

    @Override
    public boolean rate(Player player, boolean like, EventData event) {
        return eventService.handleReputation(player, like, event);
    }

    @Override
    public List<MapChoice> maps() {
        Gamemode mode = Vars.state != null && Vars.state.rules != null ? Vars.state.rules.mode() : Gamemode.survival;
        List<MapChoice> choices = new ArrayList<>();
        for (Map map : mapService.getAvailableMaps()) {
            choices.add(new MapChoice(null, map.plainName(), map.file == null ? "" : map.file.name(), map.author(),
                    mode.name()));
        }
        return choices;
    }

    @Override
    public int mapsPerPage() {
        return secretsConfig.pagination.mapsPerPage;
    }

    @Override
    public void openMap(Session session, MapData map) {
        MapMenu maps = mapMenu.get();
        if (maps != null && session.data != null) {
            maps.map(session.data.uuid, map);
        }
    }

    @Override
    public @Nullable String cachedPreview(MapData map) {
        Map file = mapPreviewService == null ? null : mapService.findPersistedMap(map);
        return file == null ? null : mapPreviewService.getCachedRegionName(file);
    }

    @Override
    public void requestPreview(Player player, MapData map, BiConsumer<String, Throwable> done) {
        Map file = mapPreviewService == null ? null : mapService.findPersistedMap(map);
        if (file == null) {
            done.accept(null, new IllegalStateException("The map is not among the maps of this server"));
            return;
        }
        mapPreviewService.requestPreview(player, file, done);
    }
}
