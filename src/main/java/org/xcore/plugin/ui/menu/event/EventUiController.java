package org.xcore.plugin.ui.menu.event;

import arc.util.Log;
import arc.util.Strings;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.ui.builder.MenuResult;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.service.EventEditorService;
import org.xcore.plugin.service.EventEditorService.Draft;
import org.xcore.plugin.service.EventEditorService.Editor;
import org.xcore.plugin.service.EventEditorService.MapChoice;
import org.xcore.plugin.service.EventEditorService.Problem;
import org.xcore.plugin.service.EventEditorService.SaveResult;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.plugin.ui.kit.TextWidth;
import org.xcore.plugin.ui.menu.event.EventUiEvent.EditorAction;
import org.xcore.plugin.ui.menu.event.EventUiEvent.Fields;
import org.xcore.plugin.ui.menu.event.EventUiModel.Catalog;
import org.xcore.plugin.ui.menu.event.EventUiModel.Confirm;
import org.xcore.plugin.ui.menu.event.EventUiModel.Details;
import org.xcore.plugin.ui.menu.event.EventUiModel.Editing;
import org.xcore.plugin.ui.menu.event.EventUiModel.Tab;
import org.xcore.plugin.ui.menu.event.EventUiModel.TimeField;
import org.xcore.plugin.ui.menu.event.EventUiModel.View;
import org.xcore.plugin.ui.menu.event.EventUiModel.Viewer;
import org.xcore.plugin.ui.menu.event.EventUiModel.VoteState;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VImage;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UiSession;
import org.xcore.ui.runtime.UpdateResult;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.ui.kit.Kit.GAP;
import static org.xcore.plugin.ui.kit.Texts.locale;
import static org.xcore.plugin.ui.kit.Texts.t;

/**
 * The events menu ({@code /event}, {@code /events}): the list of events by tabs, an event with
 * its map, its votes and the way to start it, and the editor of an event with the screens that
 * pick its map and its times. Laid out once per {@link Screen}.
 *
 * <p>Storage is read and written off the game thread; what it answers comes back as an event.
 * The buttons carry positions in the model, never an id.
 */
public class EventUiController implements UiController<EventUiModel, EventUiEvent> {

    public static final SlotKey<Object> SLOT_NOW = SlotKey.of("slot_event_now");
    public static final SlotKey<Object> SLOT_LIST = SlotKey.of("slot_event_list");
    public static final SlotKey<Object> SLOT_PAGER = SlotKey.of("slot_event_pager");
    public static final SlotKey<Object> SLOT_PREVIEW = SlotKey.of("slot_event_preview");
    public static final SlotKey<Object> SLOT_RATING = SlotKey.of("slot_event_rating");
    public static final SlotKey<Object> SLOT_LAUNCH = SlotKey.of("slot_event_launch");
    public static final SlotKey<Object> SLOT_FLAGS = SlotKey.of("slot_event_flags");
    public static final SlotKey<Object> SLOT_MAPS = SlotKey.of("slot_event_maps");
    public static final SlotKey<Object> SLOT_MAPS_PAGER = SlotKey.of("slot_event_maps_pager");
    public static final SlotKey<Object> SLOT_TIME = SlotKey.of("slot_event_time");

    static final String FIELD_NAME = "field_event_name";
    static final String FIELD_DESCRIPTION = "field_event_description";
    static final String FIELD_MAP_QUERY = "field_event_map";

    private static final float STRIPE = 4f;
    /** The least room for the likes and the dislikes, right of a row. */
    private static final float VALUE_WIDTH = 52f;
    private static final float GLYPH = 24f;
    /** Edge of the square the map is drawn in; it fits a card of the smallest screen. */
    private static final float PREVIEW_SIZE = 200f;
    private static final String RUNNING = Accent.GREEN.color();
    /** What an event without a description holds in storage. */
    private static final String NO_DESCRIPTION = "Unknown";

    private final EventUiBackend backend;
    private final Async async;
    private final Session session;
    private final Clock clock;
    /** Counts what this dialog asked of storage; only the latest answer may change it. */
    private long loadGeneration;
    /** A save is on its way: a second press must not store the event twice. */
    private boolean saving;

    public EventUiController(EventUiBackend backend, Async async, Session session, Clock clock) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.async = async;
        this.session = session;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    // =========================================================================
    // Opening
    // =========================================================================

    /** What {@code session} may do, as the buttons are drawn by. */
    public static Viewer viewer(Session session) {
        if (session == null || session.data == null) {
            return Viewer.GUEST;
        }
        return new Viewer(session.data.id,
                session.has(PermissionNodes.EVENTS_STOP),
                session.has(PermissionNodes.VOTES_CANCEL),
                session.has(PermissionNodes.EVENTS_FORCE_VOTE),
                session.has(PermissionNodes.EVENTS_CREATE_MAJOR),
                session.has(PermissionNodes.EVENTS_EDIT_OTHERS));
    }

    /** Blocking. The page the menu opens on. */
    public Catalog fetch(Tab tab, int page, Viewer viewer) {
        return backend.catalog(tab, page, viewer);
    }

    /** The menu on a page of the list. */
    public EventUiModel model(Viewer viewer, Catalog catalog) {
        return EventUiModel.opening(viewer, catalog.tab()).withCatalog(catalog).withVote(backend.votes());
    }

    /**
     * The menu on the editor of a new event.
     *
     * @param map the map the event is for; {@code null} leaves it to be picked
     */
    public EventUiModel creating(Viewer viewer, @Nullable MapChoice map) {
        Draft draft = newDraft(map);
        remember(draft);
        return EventUiModel.opening(viewer, Tab.UPCOMING)
                .withVote(backend.votes())
                .withEditing(Editing.of(draft, backend.maps()))
                .withView(View.EDITOR);
    }

    /** A new event: the one the player was writing when the dialog was closed, if there is one. */
    private Draft newDraft(@Nullable MapChoice map) {
        Draft kept = keptDraft();
        Draft draft = kept != null ? kept : Draft.blank(null);
        return map != null ? draft.withMap(map) : draft;
    }

    private @Nullable Draft keptDraft() {
        if (session == null || !session.hasDraft(Draft.class)) {
            return null;
        }
        Draft kept = session.getDraft(Draft.class);
        return kept != null && kept.isNew() ? kept : null;
    }

    /** Keeps a new event with the player, so that closing the dialog by accident loses nothing. */
    private void remember(Draft draft) {
        if (session != null && draft.isNew()) {
            session.setDraft(Draft.class, draft);
        }
    }

    private void forget() {
        if (session != null) {
            session.clearDraft(Draft.class);
        }
    }

    // =========================================================================
    // Loading
    // =========================================================================

    @SuppressWarnings("unchecked")
    private @Nullable UiSession<EventUiModel, EventUiEvent> dialog() {
        return session == null ? null : (UiSession<EventUiModel, EventUiEvent>) session.activeUiSession();
    }

    /** Hands {@code event} to the dialog that asked for it, unless it has asked for something newer or is gone. */
    private void deliver(@Nullable UiSession<EventUiModel, EventUiEvent> dialog, long generation, EventUiEvent event) {
        if (dialog == null || generation != loadGeneration || session.activeUiSession() != dialog) {
            return;
        }
        dialog.dispatch(event);
    }

    /** The player went to another screen: what is still loading was asked for by the one they left. */
    private void abandonLoads() {
        loadGeneration++;
    }

    private void requestList(Tab tab, int page, Viewer viewer) {
        long generation = ++loadGeneration;
        var dialog = dialog();
        async.supply(() -> backend.catalog(tab, page, viewer)).thenMain((catalog, error) -> {
            if (error != null) {
                Log.err("Failed to load the events of tab " + tab, error);
            }
            deliver(dialog, generation, error == null && catalog != null
                    ? new EventUiEvent.ListLoaded(catalog) : new EventUiEvent.LoadFailed());
        });
    }

    private UpdateResult<EventUiModel> loadList(EventUiModel model, Tab tab, int page) {
        requestList(tab, page, model.viewer());
        return UpdateResult.of(model);
    }

    private UpdateResult<EventUiModel> loadDetails(EventUiModel model, @Nullable ObjectId id, String notice) {
        if (id == null) {
            return UpdateResult.of(model);
        }
        long generation = ++loadGeneration;
        var dialog = dialog();
        async.supply(() -> backend.event(id)).thenMain((stored, error) -> {
            if (error != null) {
                Log.err("Failed to load event " + id, error);
            }
            deliver(dialog, generation, error == null && stored != null
                    ? new EventUiEvent.DetailsLoaded(details(stored), notice) : new EventUiEvent.LoadFailed());
        });
        return UpdateResult.of(model);
    }

    /** {@code stored} as the viewer sees it: with their own vote, and the map's picture if the client has it. */
    public Details details(EventUiBackend.Stored stored) {
        EventData event = stored.event();
        Boolean own = session != null && session.data != null && session.data.eventVotes != null && event.id != null
                ? session.data.eventVotes.get(event.id.toString()) : null;
        String region = stored.map() != null ? backend.cachedPreview(stored.map()) : null;
        return new Details(event, stored.map(), stored.author(), own, region,
                stored.map() != null && region == null, Confirm.NONE);
    }

    /** Asks for the picture of the event's map; it arrives as an event of its own. */
    private void requestPreview(Details details) {
        var dialog = dialog();
        if (dialog == null || session.player == null || details.map() == null) {
            return;
        }
        String id = details.id();
        backend.requestPreview(session.player, details.map(), (region, error) -> async.main(() -> {
            if (session.activeUiSession() != dialog) {
                return;
            }
            dialog.dispatch(region != null ? new EventUiEvent.PreviewReady(id, region)
                    : new EventUiEvent.PreviewFailed(id));
        }));
    }

    // =========================================================================
    // Events
    // =========================================================================

    @Override
    public EventUiModel initialModel(Object context) {
        return EventUiModel.opening(viewer(session), Tab.UPCOMING);
    }

    @Override
    public UpdateResult<EventUiModel> update(EventUiModel model, EventUiEvent event, ControllerContext ctx) {
        return switch (event) {
            // ---------------------------------------------------------- list
            case EventUiEvent.SelectTab(Tab tab) -> {
                if (model.view() != View.LIST || tab == null || tab == model.catalog().tab()) {
                    yield UpdateResult.of(model);
                }
                yield loadList(model, tab, 1);
            }
            case EventUiEvent.NextPage() -> {
                Catalog catalog = model.catalog();
                if (model.view() != View.LIST || catalog.page() >= catalog.totalPages()) {
                    yield UpdateResult.of(model);
                }
                yield loadList(model, catalog.tab(), catalog.page() + 1);
            }
            case EventUiEvent.PrevPage() -> {
                Catalog catalog = model.catalog();
                if (model.view() != View.LIST || catalog.page() <= 1) {
                    yield UpdateResult.of(model);
                }
                yield loadList(model, catalog.tab(), catalog.page() - 1);
            }
            case EventUiEvent.Refresh() -> model.view() == View.LIST
                    ? loadList(model, model.catalog().tab(), model.catalog().page())
                    : UpdateResult.of(model);
            case EventUiEvent.OpenEvent(int index) -> {
                List<EventData> events = model.catalog().events();
                if (model.view() != View.LIST || index < 0 || index >= events.size()) {
                    yield UpdateResult.of(model);
                }
                yield loadDetails(model, events.get(index).id, "");
            }
            case EventUiEvent.OpenActive() -> {
                EventData active = model.catalog().active();
                if (model.view() != View.LIST || active == null) {
                    yield UpdateResult.of(model);
                }
                yield loadDetails(model, active.id, "");
            }
            case EventUiEvent.OpenVoted() -> {
                if (model.view() != View.LIST) {
                    yield UpdateResult.of(model);
                }
                EventData voted = backend.votedEvent();
                if (voted == null || voted.id == null) {
                    // The vote ended while the list was open.
                    yield UpdateResult.patch(model.withVote(backend.votes()), Screen.slots(SLOT_NOW));
                }
                yield loadDetails(model, voted.id, "");
            }
            case EventUiEvent.Create() -> {
                if (model.view() != View.LIST) {
                    yield UpdateResult.of(model);
                }
                abandonLoads();
                boolean resumed = keptDraft() != null;
                EventUiModel next = model.withEditing(Editing.of(newDraft(null), backend.maps())).withView(View.EDITOR);
                yield UpdateResult.rerender(resumed ? done(next, "event-ui-draft-restored") : next);
            }
            case EventUiEvent.ListLoaded(Catalog catalog) -> {
                EventUiModel next = model.withCatalog(catalog).withVote(backend.votes()).withoutFeedback();
                if (model.view() != View.LIST) {
                    yield UpdateResult.of(model.withCatalog(catalog));
                }
                // A turned page changes the list only; another tab changes the line under the tabs as well.
                boolean pageOnly = catalog.tab() == model.catalog().tab() && model.feedback().isEmpty();
                yield pageOnly
                        ? UpdateResult.patch(next, Screen.slots(SLOT_NOW, SLOT_LIST, SLOT_PAGER))
                        : UpdateResult.rerender(next);
            }
            case EventUiEvent.LoadFailed() -> UpdateResult.rerender(refused(model, "event-ui-load-failed"));

            // ---------------------------------------------------------- event
            case EventUiEvent.DetailsLoaded(Details details, String notice) -> {
                EventUiModel next = model.withDetails(details).withEditing(null).withVote(backend.votes())
                        .withView(View.DETAILS);
                if (details.previewLoading()) {
                    requestPreview(details);
                }
                yield UpdateResult.rerender(notice == null || notice.isEmpty() ? next : done(next, notice));
            }
            case EventUiEvent.PreviewReady(String eventId, String region) -> preview(model, eventId, region);
            case EventUiEvent.PreviewFailed(String eventId) -> preview(model, eventId, null);
            case EventUiEvent.BackToList() -> {
                if (model.view() != View.DETAILS) {
                    yield UpdateResult.of(model);
                }
                EventUiModel next = model.withDetails(null).withView(View.LIST);
                // The list is shown as it was and brought up to date: the event may have changed it.
                requestList(next.catalog().tab(), next.catalog().page(), next.viewer());
                yield UpdateResult.rerender(next);
            }
            case EventUiEvent.Rate(boolean like) -> {
                Details details = model.details();
                if (model.view() != View.DETAILS || details == null || player() == null) {
                    yield UpdateResult.of(model);
                }
                if (!backend.rate(session.player, like, details.event())) {
                    yield UpdateResult.of(model);
                }
                yield UpdateResult.patch(model.withDetails(details.withOwnVote(like)), Screen.slots(SLOT_RATING));
            }
            case EventUiEvent.StartVote() -> {
                Details details = model.details();
                if (model.view() != View.DETAILS || details == null || player() == null || details.event().isActive) {
                    yield UpdateResult.of(model);
                }
                VoteState votes = backend.votes();
                if (votes.running()) {
                    yield UpdateResult.rerender(refused(model.withVote(votes), "event-ui-vote-busy"));
                }
                backend.startVote(session.player, details.event(), false);
                // The vote goes on in the chat, which the dialog covers.
                yield UpdateResult.close(model);
            }
            case EventUiEvent.ForceVote() -> {
                Details details = model.details();
                if (model.view() != View.DETAILS || details == null || player() == null || details.event().isActive
                        || !session.allowed(PermissionNodes.EVENTS_FORCE_VOTE)) {
                    yield UpdateResult.of(model);
                }
                VoteState votes = backend.votes();
                if (votes.running()) {
                    yield UpdateResult.rerender(refused(
                            model.withVote(votes).withDetails(details.withConfirm(Confirm.NONE)), "event-ui-vote-busy"));
                }
                if (details.confirm() != Confirm.FORCE) {
                    yield UpdateResult.patch(model.withDetails(details.withConfirm(Confirm.FORCE)),
                            Screen.slots(SLOT_LAUNCH));
                }
                backend.startVote(session.player, details.event(), true);
                yield UpdateResult.close(model);
            }
            case EventUiEvent.StopEvent() -> {
                Details details = model.details();
                if (model.view() != View.DETAILS || details == null || !details.event().isActive
                        || session == null || !session.allowed(PermissionNodes.EVENTS_STOP)) {
                    yield UpdateResult.of(model);
                }
                if (details.confirm() != Confirm.STOP) {
                    yield UpdateResult.patch(model.withDetails(details.withConfirm(Confirm.STOP)),
                            Screen.slots(SLOT_LAUNCH));
                }
                yield stopEvent(model);
            }
            case EventUiEvent.Returned(Catalog catalog, String notice) -> UpdateResult.rerender(done(
                    model.withDetails(null).withEditing(null).withCatalog(catalog).withVote(backend.votes())
                            .withView(View.LIST),
                    notice));
            case EventUiEvent.StopVote() -> {
                if (model.view() != View.DETAILS || model.details() == null
                        || session == null || !session.allowed(PermissionNodes.VOTES_CANCEL)) {
                    yield UpdateResult.of(model);
                }
                backend.stopVote();
                yield UpdateResult.rerender(done(model.withVote(backend.votes()), "event-ui-vote-stopped"));
            }
            case EventUiEvent.CancelConfirm() -> {
                Details details = model.details();
                if (model.view() != View.DETAILS || details == null || details.confirm() == Confirm.NONE) {
                    yield UpdateResult.of(model);
                }
                yield UpdateResult.patch(model.withDetails(details.withConfirm(Confirm.NONE)),
                        Screen.slots(SLOT_LAUNCH));
            }
            case EventUiEvent.OpenMap() -> {
                Details details = model.details();
                if (model.view() != View.DETAILS || details == null || details.map() == null || session == null) {
                    yield UpdateResult.of(model);
                }
                MapData map = details.map();
                post(ctx, () -> backend.openMap(session, map));
                yield UpdateResult.of(model);
            }
            case EventUiEvent.Edit() -> {
                Details details = model.details();
                if (model.view() != View.DETAILS || details == null) {
                    yield UpdateResult.of(model);
                }
                if (!EventEditorService.canEdit(details.event(), editor())) {
                    yield UpdateResult.rerender(refused(model, Problem.NOT_ALLOWED.key()));
                }
                abandonLoads();
                yield UpdateResult.rerender(model
                        .withEditing(Editing.of(Draft.of(details.event(), details.map()), backend.maps()))
                        .withView(View.EDITOR));
            }

            // ---------------------------------------------------------- editor
            case EventUiEvent.EditorPress(Fields fields, EditorAction action) -> {
                Editing editing = model.editing();
                if (model.view() != View.EDITOR || editing == null || action == null) {
                    yield UpdateResult.of(model);
                }
                yield press(model, editing.withDraft(typed(editing.draft(), fields)), action);
            }
            case EventUiEvent.CancelEdit() -> {
                Editing editing = model.editing();
                if (model.view() != View.EDITOR || editing == null) {
                    yield UpdateResult.of(model);
                }
                if (editing.draft().isNew()) {
                    forget();
                }
                yield leaveEditor(model);
            }
            case EventUiEvent.Saved(SaveResult result) -> {
                Editing editing = model.editing();
                if (!result.saved() || result.event() == null) {
                    Problem problem = result.problem() != null ? result.problem() : Problem.FAILED;
                    yield UpdateResult.rerender(refused(model, problem.key()));
                }
                if (editing != null && editing.draft().isNew()) {
                    forget();
                }
                // The editor stays on screen until the stored event is read; it has nothing left to save.
                EventUiModel left = model.withEditing(null).withView(View.LIST);
                if (result.event().id == null) {
                    // Nothing to read it back by: the list it is now in shows it.
                    yield returnToList(left, () -> {
                    }, "event-ui-saved");
                }
                yield loadDetails(left, result.event().id, "event-ui-saved");
            }

            // ---------------------------------------------------------- maps
            case EventUiEvent.SearchMaps(String query) -> {
                Editing editing = model.editing();
                if (model.view() != View.MAPS || editing == null) {
                    yield UpdateResult.of(model);
                }
                yield UpdateResult.patch(model.withEditing(editing.withMaps(query, 1)),
                        Screen.slots(SLOT_MAPS, SLOT_MAPS_PAGER));
            }
            case EventUiEvent.MapsPage(int delta) -> {
                Editing editing = model.editing();
                if (model.view() != View.MAPS || editing == null) {
                    yield UpdateResult.of(model);
                }
                int page = Math.clamp(mapsPage(editing) + (long) delta, 1, mapsPages(editing));
                yield UpdateResult.patch(model.withEditing(editing.withMaps(editing.mapsQuery(), page)),
                        Screen.slots(SLOT_MAPS, SLOT_MAPS_PAGER));
            }
            case EventUiEvent.ChooseMap(int index) -> {
                Editing editing = model.editing();
                List<MapChoice> found = editing == null ? List.of() : editing.found();
                if (model.view() != View.MAPS || index < 0 || index >= found.size()) {
                    yield UpdateResult.of(model);
                }
                Draft draft = editing.draft().withMap(found.get(index));
                remember(draft);
                yield UpdateResult.rerender(model.withEditing(editing.withDraft(draft)).withView(View.EDITOR));
            }
            case EventUiEvent.BackToEditor() -> {
                if (model.editing() == null || (model.view() != View.MAPS && model.view() != View.TIME)) {
                    yield UpdateResult.of(model);
                }
                yield UpdateResult.rerender(model.withView(View.EDITOR));
            }

            // ---------------------------------------------------------- time
            case EventUiEvent.TimeStep(EventTime.Step step) -> {
                Editing editing = model.editing();
                if (model.view() != View.TIME || editing == null || step == null) {
                    yield UpdateResult.of(model);
                }
                yield picked(model, editing, EventTime.step(editing.picked(), step, clock));
            }
            case EventUiEvent.TimeShortcut(EventTime.Shortcut shortcut) -> {
                Editing editing = model.editing();
                if (model.view() != View.TIME || editing == null || shortcut == null) {
                    yield UpdateResult.of(model);
                }
                // The end of an event is counted from its start.
                long anchor = editing.field() == TimeField.END ? editing.draft().plannedStart() : 0L;
                yield picked(model, editing, EventTime.shortcut(shortcut, anchor, clock));
            }
            case EventUiEvent.ClearTime() -> {
                Editing editing = model.editing();
                if (model.view() != View.TIME || editing == null) {
                    yield UpdateResult.of(model);
                }
                yield picked(model, editing, 0L);
            }
            case EventUiEvent.ApplyTime() -> {
                Editing editing = model.editing();
                if (model.view() != View.TIME || editing == null) {
                    yield UpdateResult.of(model);
                }
                Draft draft = editing.field() == TimeField.START
                        ? editing.draft().withPlannedStart(editing.picked())
                        : editing.draft().withPlannedEnd(editing.picked());
                remember(draft);
                EventUiModel next = model.withEditing(editing.withDraft(draft)).withView(View.EDITOR);
                // Said at once: the two times are set on a screen that shows only one of them.
                yield UpdateResult.rerender(EventEditorService.endsBeforeStart(draft)
                        ? refused(next, Problem.END_BEFORE_START.key()) : next);
            }

            // ---------------------------------------------------------- window
            case EventUiEvent.Back() -> {
                if (session == null || !session.canGoBack() || session.menuService == null) {
                    yield UpdateResult.of(model);
                }
                post(ctx, () -> session.menuService.goBack(session));
                yield UpdateResult.close(model);
            }
            case EventUiEvent.Close() -> UpdateResult.close(model);
        };
    }

    private static void post(ControllerContext ctx, Runnable action) {
        if (ctx != null) {
            ctx.post(action);
        } else {
            action.run();
        }
    }

    private @Nullable Player player() {
        return session == null ? null : session.player;
    }

    /** What the player may do now, which may be less than when the menu was drawn. */
    private Editor editor() {
        return viewer(session).editor();
    }

    private EventUiModel refused(EventUiModel model, String key) {
        return model.withFeedback("[scarlet]" + Iconc.warning + "[] " + Strings.stripColors(t(locale(session), key)), false);
    }

    private EventUiModel done(EventUiModel model, String key) {
        return model.withFeedback("[lime]" + Iconc.ok + "[] " + Strings.stripColors(t(locale(session), key)), true);
    }

    private UpdateResult<EventUiModel> preview(EventUiModel model, String eventId, @Nullable String region) {
        Details details = model.details();
        if (details == null || !details.id().equals(eventId)) {
            return UpdateResult.of(model);
        }
        EventUiModel next = model.withDetails(details.withPreview(region, false));
        return model.view() == View.DETAILS
                ? UpdateResult.patch(next, Screen.slots(SLOT_PREVIEW))
                : UpdateResult.of(next);
    }

    private UpdateResult<EventUiModel> stopEvent(EventUiModel model) {
        return returnToList(model, backend::finishActiveEvent, "event-ui-stopped");
    }

    /**
     * Does {@code work} off the game thread and then shows the list as it is after it, with
     * {@code notice} above. Until then the dialog keeps showing what it had.
     */
    private UpdateResult<EventUiModel> returnToList(EventUiModel model, Runnable work, String notice) {
        Tab tab = model.catalog().tab();
        Viewer viewer = model.viewer();
        long generation = ++loadGeneration;
        var dialog = dialog();
        async.supply(() -> {
            work.run();
            return backend.catalog(tab, 1, viewer);
        }).thenMain((catalog, error) -> {
            if (error != null) {
                Log.err("Failed to return to the events of tab " + tab, error);
            }
            deliver(dialog, generation, error == null && catalog != null
                    ? new EventUiEvent.Returned(catalog, notice) : new EventUiEvent.LoadFailed());
        });
        return UpdateResult.of(model);
    }

    /** {@code draft} with what the text fields held; a press that came without them changes nothing. */
    private static Draft typed(Draft draft, @Nullable Fields fields) {
        if (fields == null) {
            return draft;
        }
        return draft.withTexts(fields.name() != null ? fields.name() : draft.name(),
                fields.description() != null ? fields.description() : draft.description());
    }

    private UpdateResult<EventUiModel> press(EventUiModel model, Editing editing, EditorAction action) {
        Draft draft = editing.draft();
        remember(draft);
        return switch (action) {
            case PICK_MAP -> UpdateResult.rerender(model.withEditing(editing.withMaps("", 1)).withView(View.MAPS));
            case PICK_START -> UpdateResult.rerender(model
                    .withEditing(editing.withPicked(TimeField.START, draft.plannedStart())).withView(View.TIME));
            case PICK_END -> UpdateResult.rerender(model
                    .withEditing(editing.withPicked(TimeField.END, draft.plannedEnd())).withView(View.TIME));
            case TOGGLE_TEMPORARY -> flags(model, editing.withDraft(draft.withTemporary(!draft.temporary())));
            case TOGGLE_MAJOR -> session != null && session.allowed(PermissionNodes.EVENTS_CREATE_MAJOR)
                    ? flags(model, editing.withDraft(draft.withMajor(!draft.major())))
                    : UpdateResult.of(model.withEditing(editing));
            case SAVE -> save(model.withEditing(editing));
        };
    }

    private UpdateResult<EventUiModel> flags(EventUiModel model, Editing editing) {
        remember(editing.draft());
        EventUiModel next = model.withEditing(editing);
        // What the last press was refused for may no longer hold, so the band above goes too.
        return model.feedback().isEmpty()
                ? UpdateResult.patch(next, Screen.slots(SLOT_FLAGS))
                : UpdateResult.rerender(next.withoutFeedback());
    }

    private UpdateResult<EventUiModel> save(EventUiModel model) {
        if (saving) {
            return UpdateResult.of(model);
        }
        Draft draft = model.editing().draft();
        Problem problem = EventEditorService.validate(draft);
        if (problem != null) {
            return UpdateResult.rerender(refused(model, problem.key()));
        }
        saving = true;
        Editor editor = editor();
        String fallback = defaultName(locale(session));
        long generation = ++loadGeneration;
        var dialog = dialog();
        async.supply(() -> backend.save(draft, editor, fallback)).thenMain((result, error) -> {
            saving = false;
            if (error != null) {
                Log.err("Failed to save an event", error);
            }
            deliver(dialog, generation, new EventUiEvent.Saved(error == null && result != null
                    ? result : new SaveResult(null, Problem.FAILED)));
        });
        return UpdateResult.of(model);
    }

    /** Out of the editor: to the event that was being changed, or to the list. */
    private UpdateResult<EventUiModel> leaveEditor(EventUiModel model) {
        abandonLoads();
        if (model.details() != null) {
            return UpdateResult.rerender(model.withEditing(null).withView(View.DETAILS));
        }
        EventUiModel next = model.withEditing(null).withView(View.LIST);
        requestList(next.catalog().tab(), next.catalog().page(), next.viewer());
        return UpdateResult.rerender(next);
    }

    private UpdateResult<EventUiModel> picked(EventUiModel model, Editing editing, long time) {
        return UpdateResult.patch(model.withEditing(editing.withPicked(editing.field(), time)),
                Screen.slots(SLOT_TIME));
    }

    private int mapsPages(Editing editing) {
        int perPage = Math.max(1, backend.mapsPerPage());
        return Math.max(1, (editing.found().size() + perPage - 1) / perPage);
    }

    private int mapsPage(Editing editing) {
        return Math.clamp(editing.mapsPage(), 1, mapsPages(editing));
    }

    // =========================================================================
    // Presses
    // =========================================================================

    @Override
    public EventUiEvent parseEvent(MenuResult result) {
        if (result == null || result.wasCancelled() || result.result == null) {
            return new EventUiEvent.Close();
        }
        String res = result.result.trim();
        return switch (res) {
            case "action:page:next" -> new EventUiEvent.NextPage();
            case "action:page:prev" -> new EventUiEvent.PrevPage();
            case "action:refresh" -> new EventUiEvent.Refresh();
            case "action:active" -> new EventUiEvent.OpenActive();
            case "action:voted" -> new EventUiEvent.OpenVoted();
            case "action:create" -> new EventUiEvent.Create();
            case "action:list" -> new EventUiEvent.BackToList();
            case "action:like" -> new EventUiEvent.Rate(true);
            case "action:dislike" -> new EventUiEvent.Rate(false);
            case "action:vote" -> new EventUiEvent.StartVote();
            case "action:force" -> new EventUiEvent.ForceVote();
            case "action:stop" -> new EventUiEvent.StopEvent();
            case "action:stop_vote" -> new EventUiEvent.StopVote();
            case "action:confirm:cancel" -> new EventUiEvent.CancelConfirm();
            case "action:map" -> new EventUiEvent.OpenMap();
            case "action:edit" -> new EventUiEvent.Edit();
            case "action:edit:cancel" -> new EventUiEvent.CancelEdit();
            case "action:maps:search" -> new EventUiEvent.SearchMaps(Objects.requireNonNullElse(
                    value(result, FIELD_MAP_QUERY), ""));
            case "action:maps:prev" -> new EventUiEvent.MapsPage(-1);
            case "action:maps:next" -> new EventUiEvent.MapsPage(1);
            case "action:editor" -> new EventUiEvent.BackToEditor();
            case "action:time:clear" -> new EventUiEvent.ClearTime();
            case "action:time:apply" -> new EventUiEvent.ApplyTime();
            case "action:back" -> new EventUiEvent.Back();
            default -> parseNamed(res, result);
        };
    }

    /** A press that names what it is about; {@code null} for one that is not of this menu. */
    private static @Nullable EventUiEvent parseNamed(String res, MenuResult result) {
        if (res.startsWith("action:tab:")) {
            Tab tab = Tab.of(res.substring("action:tab:".length()));
            return tab == null ? null : new EventUiEvent.SelectTab(tab);
        }
        if (res.startsWith("action:event:")) {
            Integer index = number(res, "action:event:");
            return index == null ? null : new EventUiEvent.OpenEvent(index);
        }
        if (res.startsWith("action:maps:pick:")) {
            Integer index = number(res, "action:maps:pick:");
            return index == null ? null : new EventUiEvent.ChooseMap(index);
        }
        if (res.startsWith("action:edit:")) {
            EditorAction action = switch (res.substring("action:edit:".length())) {
                case "map" -> EditorAction.PICK_MAP;
                case "start" -> EditorAction.PICK_START;
                case "end" -> EditorAction.PICK_END;
                case "temporary" -> EditorAction.TOGGLE_TEMPORARY;
                case "major" -> EditorAction.TOGGLE_MAJOR;
                case "save" -> EditorAction.SAVE;
                default -> null;
            };
            return action == null ? null : new EventUiEvent.EditorPress(fields(result), action);
        }
        if (res.startsWith("action:time:set:")) {
            EventTime.Shortcut shortcut = EventTime.Shortcut.of(res.substring("action:time:set:".length()));
            return shortcut == null ? null : new EventUiEvent.TimeShortcut(shortcut);
        }
        if (res.startsWith("action:time:")) {
            EventTime.Step step = EventTime.Step.of(res.substring("action:time:".length()));
            return step == null ? null : new EventUiEvent.TimeStep(step);
        }
        return null;
    }

    private static @Nullable Integer number(String res, String prefix) {
        try {
            return Integer.parseInt(res.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static @Nullable String value(MenuResult result, String field) {
        return result.values != null && result.values.get(field) instanceof String text ? text : null;
    }

    /** The texts of the editor as the client sent them along with the press. */
    private static @Nullable Fields fields(MenuResult result) {
        String name = value(result, FIELD_NAME);
        String description = value(result, FIELD_DESCRIPTION);
        return name == null && description == null ? null : new Fields(name, description);
    }

    // =========================================================================
    // View
    // =========================================================================

    @Override
    public VNode render(EventUiModel model) {
        // The time screen is a few buttons: two columns would only pull them apart.
        return model.view() == View.TIME && model.editing() != null
                ? Screen.compact(screen -> window(model, screen))
                : Screen.each(screen -> window(model, screen));
    }

    /** The menu laid out for one class of screens. */
    VNode window(EventUiModel model, Screen screen) {
        Localization local = locale(session);
        Editing editing = model.editing();
        return switch (model.view()) {
            case DETAILS -> model.details() != null
                    ? detailsWindow(model, model.details(), screen, local) : listWindow(model, screen, local);
            case EDITOR -> editing != null ? editorWindow(model, editing, screen, local) : listWindow(model, screen, local);
            case MAPS -> editing != null ? mapsWindow(editing, screen, local) : listWindow(model, screen, local);
            case TIME -> editing != null ? timeWindow(editing, screen, local) : listWindow(model, screen, local);
            case LIST -> listWindow(model, screen, local);
        };
    }

    private ZoneId zone() {
        return clock.getZone();
    }

    /** "Server time: UTC+03:00": every time of the menu is the server's, and a player may live elsewhere. */
    private String serverTime(float width, Localization local) {
        return TextWidth.fit("[lightgray]" + t(local, "event-ui-server-time", args("zone", EventTime.zone(clock)))
                + "[]", width);
    }

    private static void feedback(Ui.TableBuilder window, EventUiModel model, float width) {
        if (!model.feedback().isBlank()) {
            window.add(Kit.feedback(width, model.feedback(), model.success())).row();
        }
    }

    /** The buttons under a window. */
    private static void bar(Ui.TableBuilder window, float width, List<Kit.Action> actions) {
        window.add(Ui.table(bar -> {
            bar.layout(l -> l.padTop(GAP));
            bar.add(Kit.actions(width, actions));
        })).row();
    }

    // ------------------------------------------------------------------ list

    private VNode listWindow(EventUiModel model, Screen screen, Localization local) {
        float width = screen.width();
        float inner = width - 2f * Kit.MARGIN;
        Catalog catalog = model.catalog();
        return Kit.window(window -> {
            window.add(Kit.header(width, TextWidth.fit("[accent]" + Iconc.star + "[] [white]"
                    + t(local, "event-ui-title") + "[]", inner) + "\n" + serverTime(inner, local))).row();

            window.slot(screen.slot(SLOT_NOW).path(), slot -> now(slot, model, width, local)).row();

            List<Kit.Tab> tabs = new ArrayList<>();
            for (Tab tab : Tab.values()) {
                tabs.add(new Kit.Tab(tab.glyph(), t(local, tab.key()), "action:tab:" + tab.id(), tab.accent(),
                        tab == catalog.tab()));
            }
            window.add(Kit.tabs(width, "event_tabs", tabs)).row();
            window.add(Kit.line(width, catalog.tab().accent())).row();
            feedback(window, model, width);

            // The parts a turned page changes are slots, so the rest of the window stays as it is.
            window.slot(screen.slot(SLOT_LIST).path(), slot ->
                    slot.add(Kit.pane(screen, list -> rows(list, catalog, screen, local)))).row();
            window.slot(screen.slot(SLOT_PAGER).path(), slot -> slot.add(Kit.pager(width,
                    catalog.page() + " / " + Math.max(catalog.page(), catalog.totalPages()),
                    catalog.page() > 1 ? "action:page:prev" : null,
                    catalog.page() < catalog.totalPages() ? "action:page:next" : null,
                    "action:refresh"))).row();

            List<Kit.Action> actions = new ArrayList<>();
            if (session != null && session.canGoBack()) {
                actions.add(new Kit.Action("[lightgray]" + Iconc.left + "[] " + t(local, "back"), "action:back"));
            }
            actions.add(new Kit.Action("[accent]" + Iconc.add + "[] " + t(local, "event-ui-create"), "action:create"));
            bar(window, width, actions);
        });
    }

    /** What is going on right now, above the tabs: the event that runs and the vote for another. */
    private void now(Ui.TableBuilder slot, EventUiModel model, float width, Localization local) {
        EventData active = model.catalog().active();
        VoteState vote = model.vote();
        if (active != null) {
            slot.add(nowRow("action:active", width, RUNNING,
                    "[#" + RUNNING + "]" + Iconc.play + " " + t(local, "event-ui-now-running") + "[]",
                    name(active, local))).row();
        }
        if (vote.forEvent()) {
            slot.add(nowRow("action:voted", width, Accent.GOLD.color(),
                    "[accent]" + Iconc.players + " " + t(local, "event-ui-now-vote") + " "
                            + vote.votes() + " / " + vote.required() + "[]",
                    "[white]" + vote.eventName() + "[]")).row();
        }
    }

    private static VNode nowRow(String action, float width, String color, String label, String name) {
        return Kit.row(action, width, false, true, (row, inner) -> {
            float rest = inner - STRIPE - GAP - GAP - GLYPH;
            row.image("whiteui", l -> l.width(STRIPE).growY().padRight(GAP).color(color));
            row.add(Ui.labelWrap(Text.raw(TextWidth.fit(label, rest) + "\n" + TextWidth.fit(name, rest)),
                    l -> l.width(rest).padRight(GAP)));
            row.label(Text.raw("[lightgray]" + Iconc.right + "[]"), l -> l.width(GLYPH));
        });
    }

    private void rows(Ui.TableBuilder list, Catalog catalog, Screen screen, Localization local) {
        float width = screen.cards();
        if (!catalog.loaded()) {
            list.add(Kit.note(width, Strings.stripColors(t(local, "map-ui-loading")))).row();
            return;
        }
        if (catalog.events().isEmpty()) {
            list.add(Kit.note(width, t(local, "event-ui-empty-" + catalog.tab().id()))).row();
            return;
        }
        for (int i = 0; i < catalog.events().size(); i++) {
            list.add(row(i, catalog.events().get(i), screen, local)).row();
        }
    }

    /** An event of the list; pressing it opens the event. */
    private VNode row(int index, EventData event, Screen screen, Localization local) {
        String title = (event.isMajor ? "[#" + Accent.GOLD.color() + "]" + Iconc.star + "[] " : "")
                + name(event, local);
        String votes = event.like > 0 || event.dislike > 0
                ? "[green]+" + event.like + "[]\n[scarlet]-" + event.dislike + "[]" : "";
        // Wide enough for the numbers it holds: an event a thousand players liked is not cut to "+1…".
        float value = Math.max(VALUE_WIDTH, (float) Math.ceil(TextWidth.of(votes)));
        return Kit.row("action:event:" + index, screen.cards(), false, true, (row, inner) -> {
            float rest = inner - STRIPE - GAP - GAP - value;
            row.image("whiteui", l -> l.width(STRIPE).growY().padRight(GAP).color(accent(event).color()));
            row.add(Ui.labelWrap(Text.raw(TextWidth.fit(title, rest) + "\n" + TextWidth.fit(state(event, local), rest)),
                    l -> l.width(rest).padRight(GAP)));
            row.add(Kit.right(votes, value));
        });
    }

    // ------------------------------------------------------------------ event

    private VNode detailsWindow(EventUiModel model, Details details, Screen screen, Localization local) {
        float width = screen.width();
        float inner = width - 2f * Kit.MARGIN;
        EventData event = details.event();
        return Kit.window(window -> {
            List<String> marks = new ArrayList<>();
            marks.add(state(event, local));
            if (event.isMajor) {
                marks.add("[#" + Accent.GOLD.color() + "]" + Iconc.star + " " + t(local, "event-ui-major") + "[]");
            }
            if (event.isTemporary) {
                marks.add("[lightgray]" + t(local, "event-ui-temporary") + "[]");
            }
            window.add(Kit.header(width, TextWidth.fit(name(event, local), inner) + "\n"
                    + TextWidth.fit(String.join(" [gray]·[] ", marks), inner))).row();
            window.add(Kit.line(width, accent(event))).row();
            feedback(window, model, width);

            Kit.body(window, screen, body -> body.add(Kit.columns(screen,
                    List.of(aboutCard(details, screen.card(), local), mapCard(details, screen, local)),
                    List.of(ratingCard(details, screen, local), launchCard(model, details, screen, local)))).row());

            List<Kit.Action> actions = new ArrayList<>();
            actions.add(new Kit.Action("[lightgray]" + Iconc.left + "[] " + t(local, "event-ui-to-list"), "action:list"));
            if (EventEditorService.canEdit(event, model.viewer().editor())) {
                actions.add(new Kit.Action("[accent]" + Iconc.pencil + "[] " + t(local, "event-ui-edit"), "action:edit"));
            }
            bar(window, width, actions);
        });
    }

    private VNode aboutCard(Details details, float width, Localization local) {
        EventData event = details.event();
        return Kit.card(width, Accent.BLUE, Iconc.info + " " + t(local, "event-ui-about"), (content, inner) -> {
            String description = description(event.description);
            content.add(Kit.text(description.isEmpty()
                    ? "[gray]" + t(local, "event-ui-no-description") + "[]"
                    : "[white]" + TextWidth.breakWords(description, inner) + "[]", inner)).row();

            List<String> facts = new ArrayList<>();
            if (!details.author().isBlank()) {
                facts.add(t(local, "event-ui-author", args("author", "[white]" + details.author() + "[]")));
            }
            if (event.createdModelTime > 0) {
                facts.add(t(local, "event-ui-created", args("date", EventTime.date(event.createdModelTime, zone()))));
            }
            if (!facts.isEmpty()) {
                content.add(Ui.labelWrap(Text.raw("[lightgray]" + TextWidth.breakWords(String.join("\n", facts), inner) + "[]"),
                        l -> l.width(inner).padTop(GAP))).row();
            }
        });
    }

    private VNode mapCard(Details details, Screen screen, Localization local) {
        EventData event = details.event();
        MapData map = details.map();
        return Kit.card(screen.card(), Accent.GOLD, Iconc.map + " " + t(local, "event-ui-map-schedule"), (content, inner) -> {
            content.slot(screen.slot(SLOT_PREVIEW).path(), slot -> preview(slot, details, inner, local)).row();

            String name = map == null || blank(map.name)
                    ? "[gray]" + t(local, "event-ui-map-none") + "[]"
                    : "[white]" + TextWidth.escape(map.name) + "[]";
            String author = map == null || blank(map.author) ? ""
                    : "\n" + TextWidth.fit("[lightgray]" + TextWidth.escape(Strings.stripColors(map.author)) + "[]", inner);
            content.add(Ui.labelWrap(Text.raw(TextWidth.fit(name, inner) + author),
                    l -> l.width(inner).padTop(map == null ? 0f : GAP))).row();

            List<String> times = new ArrayList<>();
            times.add(t(local, "event-ui-planned-start", args("time", time(event.plannedStartTime, local))));
            times.add(t(local, "event-ui-planned-end", args("time", time(event.plannedEndTime, local))));
            if (event.startTime > 0) {
                times.add(t(local, "event-ui-started", args("time", time(event.startTime, local))));
            }
            if (event.isFinished && event.endTime > 0) {
                times.add(t(local, "event-ui-ended", args("time", time(event.endTime, local))));
            }
            content.add(Ui.labelWrap(Text.raw("[lightgray]" + String.join("\n", times) + "[]"),
                    l -> l.width(inner).padTop(GAP))).row();

            if (map != null) {
                content.add(Ui.table(open -> {
                    open.layout(l -> l.padTop(GAP));
                    open.add(Kit.button(new Kit.Action("[accent]" + Iconc.map + "[] " + t(local, "event-ui-open-map"),
                            "action:map"), inner));
                })).row();
            }
        });
    }

    private static void preview(Ui.TableBuilder slot, Details details, float inner, Localization local) {
        slot.layout(l -> l.width(inner));
        if (details.map() == null) {
            return;
        }
        if (details.preview() != null) {
            // A map is rarely square: the picture keeps its shape inside the square.
            slot.add(new VImage(null, Ui.layout().width(PREVIEW_SIZE).height(PREVIEW_SIZE).build(),
                    details.preview(), null, VImage.SCALING_FIT));
            return;
        }
        String note = Strings.stripColors(t(local, details.previewLoading() ? "map-ui-loading" : "map-ui-no-preview"));
        slot.add(Ui.table(empty -> {
            empty.background("whiteui");
            empty.layout(l -> l.width(PREVIEW_SIZE).height(PREVIEW_SIZE).color(Kit.INSET));
            empty.add(Kit.centered("[gray]" + Iconc.image + "\n" + note + "[]", PREVIEW_SIZE - 2f * Kit.PAD));
        }));
    }

    private VNode ratingCard(Details details, Screen screen, Localization local) {
        EventData event = details.event();
        return Kit.card(screen.card(), Accent.GREEN, Iconc.up + " " + t(local, "event-ui-rating"), (content, inner) ->
                content.slot(screen.slot(SLOT_RATING).path(), slot -> {
                    slot.layout(l -> l.width(inner));
                    boolean liked = Boolean.TRUE.equals(details.ownVote());
                    boolean disliked = Boolean.FALSE.equals(details.ownVote());
                    slot.add(Kit.actions(inner, List.of(
                            new Kit.Action((liked ? "[green]" + Iconc.ok : "[lightgray]" + Iconc.up) + " "
                                    + t(local, "map-ui-btn-like", args("count", event.like)) + "[]", "action:like"),
                            new Kit.Action((disliked ? "[scarlet]" + Iconc.ok : "[lightgray]" + Iconc.down) + " "
                                    + t(local, "map-ui-btn-dislike", args("count", event.dislike)) + "[]",
                                    "action:dislike")))).row();
                }).row());
    }

    private VNode launchCard(EventUiModel model, Details details, Screen screen, Localization local) {
        return Kit.card(screen.card(), Accent.ORANGE, Iconc.play + " " + t(local, "event-ui-launch"), (content, inner) ->
                content.slot(screen.slot(SLOT_LAUNCH).path(), slot -> {
                    slot.layout(l -> l.width(inner));
                    launch(slot, model, details, inner, local);
                }).row());
    }

    /** How the event stands with being started, and what the viewer can do about it. */
    private void launch(Ui.TableBuilder slot, EventUiModel model, Details details, float inner, Localization local) {
        EventData event = details.event();
        VoteState vote = model.vote();
        Viewer viewer = model.viewer();
        Kit.Action no = new Kit.Action(t(local, "cancel"), "action:confirm:cancel");
        List<Kit.Action> actions = new ArrayList<>();
        String status;
        if (event.isActive) {
            status = "[#" + RUNNING + "]" + t(local, "event-ui-launch-active") + "[]";
            if (viewer.stopEvents() && details.confirm() == Confirm.STOP) {
                actions.add(new Kit.Action("[scarlet]" + Iconc.warning + " " + t(local, "event-ui-stop-confirm") + "[]",
                        "action:stop"));
                actions.add(no);
            } else if (viewer.stopEvents()) {
                actions.add(new Kit.Action("[scarlet]" + Iconc.cancel + "[] " + t(local, "event-ui-stop"), "action:stop"));
            }
        } else if (vote.isFor(event)) {
            status = "[accent]" + t(local, "event-ui-launch-voting",
                    args("votes", vote.votes(), "required", vote.required())) + "[]";
            if (viewer.cancelVotes()) {
                actions.add(new Kit.Action("[scarlet]" + Iconc.cancel + "[] " + t(local, "event-ui-stop-vote"),
                        "action:stop_vote"));
            }
        } else if (vote.running()) {
            status = "[lightgray]" + t(local, "event-ui-launch-busy") + "[]";
        } else {
            status = "[lightgray]" + t(local, event.isFinished ? "event-ui-launch-again" : "event-ui-launch-hint") + "[]";
            if (viewer.forceVotes() && details.confirm() == Confirm.FORCE) {
                actions.add(new Kit.Action("[scarlet]" + Iconc.warning + " " + t(local, "event-ui-force-confirm") + "[]",
                        "action:force"));
                actions.add(no);
            } else {
                actions.add(new Kit.Action("[accent]" + Iconc.play + "[] " + t(local, "event-ui-vote"), "action:vote"));
                if (viewer.forceVotes()) {
                    actions.add(new Kit.Action("[scarlet]" + Iconc.admin + "[] " + t(local, "event-ui-force"),
                            "action:force"));
                }
            }
        }
        slot.add(Kit.text(status, inner)).row();
        if (!actions.isEmpty()) {
            slot.add(Ui.table(buttons -> {
                buttons.layout(l -> l.padTop(GAP));
                buttons.add(Kit.actions(inner, actions));
            })).row();
        }
    }

    // ------------------------------------------------------------------ editor

    private VNode editorWindow(EventUiModel model, Editing editing, Screen screen, Localization local) {
        float width = screen.width();
        float inner = width - 2f * Kit.MARGIN;
        Draft draft = editing.draft();
        return Kit.window(window -> {
            window.add(Kit.header(width, TextWidth.fit("[accent]" + Iconc.pencil + "[] [white]"
                    + t(local, draft.isNew() ? "event-ui-editor-new" : "event-ui-editor-edit") + "[]", inner)
                    + "\n" + serverTime(inner, local))).row();
            window.add(Kit.line(width, Accent.PURPLE)).row();
            feedback(window, model, width);

            Kit.body(window, screen, body -> body.add(Kit.columns(screen,
                    List.of(textsCard(draft, screen.card(), local)),
                    List.of(mapChoiceCard(draft, screen.card(), local), scheduleCard(model, draft, screen, local)))).row());

            bar(window, width, List.of(
                    new Kit.Action("[lime]" + Iconc.save + "[] " + t(local, "event-ui-save"), "action:edit:save"),
                    new Kit.Action("[lightgray]" + Iconc.cancel + "[] " + t(local, "cancel"), "action:edit:cancel")));
        });
    }

    /** The name and the description. The client sends what they hold with every press of the dialog. */
    private VNode textsCard(Draft draft, float width, Localization local) {
        return Kit.card(width, Accent.BLUE, Iconc.pencil + " " + t(local, "event-ui-texts"), (content, inner) -> {
            content.labelWrap(Text.raw("[lightgray]" + t(local, "event-ui-name") + "[]"),
                    l -> l.width(inner).padBottom(2f)).row();
            content.field(FIELD_NAME, f -> f
                    .value(draft.name())
                    .hint(defaultName(local))
                    .maxLength(EventEditorService.NAME_LENGTH)
                    .layout(l -> l.width(inner).height(Kit.FIELD_HEIGHT).padBottom(GAP))).row();
            content.labelWrap(Text.raw("[lightgray]" + t(local, "event-ui-description") + "[]"),
                    l -> l.width(inner).padBottom(2f)).row();
            content.field(FIELD_DESCRIPTION, f -> f
                    .value(draft.description())
                    .hint(Strings.stripColors(t(local, "event-ui-description-hint")))
                    .maxLength(EventEditorService.DESCRIPTION_LENGTH)
                    .layout(l -> l.width(inner).height(Kit.FIELD_HEIGHT))).row();
        });
    }

    private VNode mapChoiceCard(Draft draft, float width, Localization local) {
        return Kit.card(width, Accent.GOLD, Iconc.map + " " + t(local, "event-ui-map"), (content, inner) ->
                content.add(choice("action:edit:map", inner, mapName(draft.map(), local),
                        draft.map() == null || blank(draft.map().author()) ? ""
                                : "[lightgray]" + TextWidth.escape(Strings.stripColors(draft.map().author())) + "[]",
                        0f)).row());
    }

    private VNode scheduleCard(EventUiModel model, Draft draft, Screen screen, Localization local) {
        return Kit.card(screen.card(), Accent.ORANGE, Iconc.play + " " + t(local, "event-ui-schedule"), (content, inner) -> {
            content.add(choice("action:edit:start", inner, "[lightgray]" + t(local, "event-ui-start") + "[]",
                    time(draft.plannedStart(), local), Kit.TAB_GAP)).row();
            content.add(choice("action:edit:end", inner, "[lightgray]" + t(local, "event-ui-end") + "[]",
                    time(draft.plannedEnd(), local), GAP)).row();
            content.slot(screen.slot(SLOT_FLAGS).path(), slot -> {
                slot.layout(l -> l.width(inner));
                boolean major = model.viewer().createMajor();
                slot.add(toggle("action:edit:temporary", draft.temporary(), t(local, "event-ui-temporary"),
                        t(local, "event-ui-temporary-hint"), inner, major ? Kit.TAB_GAP : 0f)).row();
                if (major) {
                    slot.add(toggle("action:edit:major", draft.major(), t(local, "event-ui-major"),
                            t(local, "event-ui-major-hint"), inner, 0f)).row();
                }
            }).row();
        });
    }

    /** A row that opens the screen where its value is picked: what it is, the value, and an arrow. */
    private static VNode choice(String action, float width, String first, String second, float padBottom) {
        return Ui.buttonTable(action, row -> {
            row.style("flatTogglet").margin(Kit.MARGIN);
            row.layout(l -> l.width(width).padBottom(padBottom));
            float text = width - 2f * Kit.MARGIN - GAP - GLYPH;
            row.add(Ui.labelWrap(Text.raw(TextWidth.fit(first, text)
                            + (second.isEmpty() ? "" : "\n" + TextWidth.fit(second, text))),
                    l -> l.width(text).padRight(GAP)));
            row.label(Text.raw("[accent]" + Iconc.right + "[]"), l -> l.width(GLYPH));
        });
    }

    /** A yes or no of the event, as a row that is pressed as a whole. */
    private static VNode toggle(String action, boolean on, String title, String hint, float width, float padBottom) {
        float text = width - 2f * Kit.MARGIN - GLYPH - GAP;
        return Ui.buttonTable(action, row -> {
            row.style("flatTogglet").checked(on).margin(Kit.MARGIN);
            row.layout(l -> l.width(width).padBottom(padBottom));
            row.label(Text.raw(on ? "[lime]" + Iconc.ok + "[]" : "[gray]" + Iconc.cancel + "[]"),
                    l -> l.width(GLYPH).padRight(GAP));
            row.add(Ui.labelWrap(Text.raw((on ? "[white]" : "[lightgray]") + title + "[]\n[gray]" + hint + "[]"),
                    l -> l.width(text)));
        });
    }

    // ------------------------------------------------------------------ maps

    private VNode mapsWindow(Editing editing, Screen screen, Localization local) {
        float width = screen.width();
        float inner = width - 2f * Kit.MARGIN;
        return Kit.window(window -> {
            window.add(Kit.header(width, TextWidth.fit("[accent]" + Iconc.map + "[] [white]"
                    + t(local, "event-ui-maps-title") + "[]", inner))).row();
            window.add(Kit.line(width, Accent.GOLD)).row();

            // The field and a button beside it: a phone's keyboard has no key that sends the search.
            float field = width - Kit.GLYPH_BUTTON - Kit.TAB_GAP;
            window.add(Ui.table(search -> {
                search.layout(l -> l.padBottom(GAP));
                search.field(FIELD_MAP_QUERY, f -> f
                        .value(editing.mapsQuery())
                        .hint(Strings.stripColors(t(local, "map-ui-search-hint")))
                        .enter("action:maps:search")
                        .layout(l -> l.width(field).height(Kit.FIELD_HEIGHT).padRight(Kit.TAB_GAP)));
                search.button(Text.raw("[accent]" + Iconc.zoom + "[]"), "action:maps:search", b -> b
                        .style("flatBordert")
                        .layout(l -> l.width(Kit.GLYPH_BUTTON).height(Kit.FIELD_HEIGHT)));
            })).row();

            int pages = mapsPages(editing);
            int page = mapsPage(editing);
            window.slot(screen.slot(SLOT_MAPS).path(), slot ->
                    slot.add(Kit.pane(screen, list -> mapRows(list, editing, page, screen, local)))).row();
            window.slot(screen.slot(SLOT_MAPS_PAGER).path(), slot -> slot.add(Kit.pager(width, page + " / " + pages,
                    page > 1 ? "action:maps:prev" : null,
                    page < pages ? "action:maps:next" : null, null))).row();

            bar(window, width, List.of(new Kit.Action(
                    "[lightgray]" + Iconc.left + "[] " + t(local, "event-ui-to-editor"), "action:editor")));
        });
    }

    private void mapRows(Ui.TableBuilder list, Editing editing, int page, Screen screen, Localization local) {
        List<MapChoice> found = editing.found();
        if (found.isEmpty()) {
            list.add(Kit.note(screen.cards(), Strings.stripColors(t(local, "map-ui-no-maps-found")))).row();
            return;
        }
        int perPage = Math.max(1, backend.mapsPerPage());
        int from = (page - 1) * perPage;
        int to = Math.min(found.size(), from + perPage);
        MapChoice current = editing.draft().map();
        for (int i = from; i < to; i++) {
            MapChoice map = found.get(i);
            String author = blank(map.author()) ? ""
                    : "[lightgray]" + TextWidth.escape(Strings.stripColors(map.author())) + "[]";
            list.add(Kit.row("action:maps:pick:" + i, screen.cards(), same(map, current), true, (row, inner) ->
                    row.add(Ui.labelWrap(Text.raw(TextWidth.fit(mapName(map, local), inner)
                                    + (author.isEmpty() ? "" : "\n" + TextWidth.fit(author, inner))),
                            l -> l.width(inner))))).row();
        }
    }

    /** Whether the two are the same map of the server: by the file, or by the name where a file is not known. */
    private static boolean same(MapChoice map, @Nullable MapChoice other) {
        if (other == null) {
            return false;
        }
        if (!blank(map.fileName()) && !blank(other.fileName())) {
            return map.fileName().equalsIgnoreCase(other.fileName());
        }
        return Objects.equals(map.name(), other.name());
    }

    // ------------------------------------------------------------------ time

    private VNode timeWindow(Editing editing, Screen screen, Localization local) {
        float width = screen.width();
        float inner = width - 2f * Kit.MARGIN;
        boolean start = editing.field() == TimeField.START;
        return Kit.window(window -> {
            window.add(Kit.header(width, TextWidth.fit("[accent]" + Iconc.play + "[] [white]"
                    + t(local, start ? "event-ui-time-start" : "event-ui-time-end") + "[]", inner)
                    + "\n" + serverTime(inner, local))).row();
            window.add(Kit.line(width, Accent.ORANGE)).row();

            window.slot(screen.slot(SLOT_TIME).path(), slot ->
                    slot.add(Kit.pane(screen, body -> time(body, editing, width, local)))).row();

            bar(window, width, List.of(
                    new Kit.Action("[lime]" + Iconc.ok + "[] " + t(local, "event-ui-time-apply"), "action:time:apply"),
                    new Kit.Action("[lightgray]" + Iconc.left + "[] " + t(local, "back"), "action:editor")));
        });
    }

    /** The time as it stands, an arrow either way for the day, the hour and the minutes, and the shortcuts. */
    private void time(Ui.TableBuilder body, Editing editing, float width, Localization local) {
        long picked = editing.picked();
        boolean set = picked > 0;
        ZoneId zone = zone();
        Locale language = local != null && local.getLocale() != null ? local.getLocale() : Locale.ENGLISH;

        body.add(Kit.card(width, (content, inner) -> content.add(Kit.centered(set
                ? "[lightgray]" + EventTime.day(picked, zone, language) + "[]\n[accent]" + hourMinute(picked) + "[]"
                : "[lightgray]" + t(local, "event-ui-time-unset") + "[]\n[gray]" + t(local, "event-ui-time-unset-hint")
                + "[]", inner)).row())).row();

        String none = "[gray]—[]";
        body.add(Kit.pager(width, set ? EventTime.date(picked, zone) : none,
                "action:time:" + EventTime.Step.DAY_BACK.id(), "action:time:" + EventTime.Step.DAY_FORTH.id(), null)).row();
        body.add(Kit.pager(width, set ? t(local, "event-ui-time-hours", args("value", twoDigits(EventTime.hour(picked, zone)))) : none,
                "action:time:" + EventTime.Step.HOUR_BACK.id(), "action:time:" + EventTime.Step.HOUR_FORTH.id(), null)).row();
        body.add(Kit.pager(width, set ? t(local, "event-ui-time-minutes", args("value", twoDigits(EventTime.minute(picked, zone)))) : none,
                "action:time:" + EventTime.Step.MINUTE_BACK.id(), "action:time:" + EventTime.Step.MINUTE_FORTH.id(), null)).row();

        List<Kit.Action> shortcuts = new ArrayList<>();
        for (EventTime.Shortcut shortcut : editing.field() == TimeField.START
                ? EventTime.Shortcut.forStart() : EventTime.Shortcut.forEnd()) {
            shortcuts.add(new Kit.Action(t(local, shortcut.key()), "action:time:set:" + shortcut.id()));
        }
        shortcuts.add(new Kit.Action("[lightgray]" + Iconc.cancel + "[] " + t(local, "event-ui-time-clear"),
                "action:time:clear", set));
        body.add(Ui.table(buttons -> {
            buttons.layout(l -> l.padTop(GAP));
            buttons.add(Kit.actions(width, shortcuts));
        })).row();
    }

    private String hourMinute(long time) {
        return twoDigits(EventTime.hour(time, zone())) + ":" + twoDigits(EventTime.minute(time, zone()));
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    // ------------------------------------------------------------------ pieces

    private static boolean blank(@Nullable String text) {
        return text == null || text.isBlank();
    }

    /** The name of an event as its author wrote it, colours and all. */
    private static String name(EventData event, Localization local) {
        String name = event.name == null ? "" : event.name.replace('\n', ' ').strip();
        return "[white]" + (name.isEmpty() ? t(local, "event-menu-event") : name) + "[]";
    }

    private static String description(@Nullable String description) {
        String text = description == null ? "" : description.strip();
        return NO_DESCRIPTION.equals(text) ? "" : text;
    }

    private String mapName(@Nullable MapChoice map, Localization local) {
        if (map == null) {
            return "[scarlet]" + t(local, "event-ui-map-pick") + "[]";
        }
        return blank(map.name())
                ? "[gray]" + t(local, "event-ui-map-none") + "[]"
                : "[white]" + TextWidth.escape(map.name()) + "[]";
    }

    /** The name an event gets when its author leaves the field empty. */
    private String defaultName(Localization local) {
        String player = session != null && session.data != null && session.data.nickname != null
                ? Strings.stripColors(session.data.nickname).strip() : "";
        return Strings.stripColors(t(local, "event-menu-create-start-default", args("playerName", player)));
    }

    private static Accent accent(EventData event) {
        if (event.isActive) return Accent.GREEN;
        if (event.isFinished) return Accent.GRAY;
        return event.isMajor ? Accent.GOLD : Accent.BLUE;
    }

    /** Where the event stands, in a few words: "Running until 16.10 21:00", "16.10 18:00", "Finished 02.10.2026". */
    private String state(EventData event, Localization local) {
        if (event.isActive) {
            return "[#" + RUNNING + "]" + (event.isTemporary && event.plannedEndTime > 0
                    ? t(local, "event-ui-state-active-until", args("time", EventTime.brief(event.plannedEndTime, zone())))
                    : t(local, "event-ui-state-active")) + "[]";
        }
        if (event.isFinished) {
            return "[gray]" + (event.endTime > 0
                    ? t(local, "event-ui-state-finished-at", args("date", EventTime.date(event.endTime, zone())))
                    : t(local, "event-ui-state-finished")) + "[]";
        }
        return event.plannedStartTime > 0
                ? "[lightgray]" + EventTime.brief(event.plannedStartTime, zone()) + "[]"
                : "[gray]" + t(local, "event-ui-state-unscheduled") + "[]";
    }

    /** A time in full, or that there is none. */
    private String time(long time, Localization local) {
        return time > 0
                ? "[white]" + EventTime.full(time, zone()) + "[]"
                : "[gray]" + t(local, "event-ui-not-set") + "[]";
    }
}
