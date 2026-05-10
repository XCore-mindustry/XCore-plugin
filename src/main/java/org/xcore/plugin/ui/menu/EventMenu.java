package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import mindustry.maps.Map;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.bson.types.ObjectId;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.EventEditorService;
import org.xcore.plugin.service.EventService;
import org.xcore.plugin.service.EventViewService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;
import org.xcore.plugin.vote.VoteEvent;
import org.xcore.plugin.vote.VoteService;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class EventMenu extends Menu {

    private static final String ROUTE_MAIN = "event.main";
    private static final String ROUTE_EVENTS = "event.events";
    private static final String ROUTE_EVENT = "event.details";
    private static final String ACTION_CREATE_START = "create-start";
    private static final String ACTION_EVENTS = "events";
    private static final String ACTION_THIS_EVENT = "this-event";
    private static final String ACTION_VOTE_STOP = "vote-stop";
    private static final String ACTION_STOP = "stop";
    private static final String ACTION_LIKE = "like";
    private static final String ACTION_DISLIKE = "dislike";
    private static final String ACTION_VOTE = "vote";
    private static final String ACTION_ADMIN_VOTE = "admin-vote";
    private static final String ACTION_EDIT = "edit";
    private static final String ACTION_EVENT_MAP = "event-map";
    private static final String ACTION_PREVIOUS = "previous";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_MAIN = "main";
    private static final String ACTION_STATUS_PREFIX = "status:";
    private static final String ACTION_EVENT_PREFIX = "event:";

    private final MapService mapService;
    private final EventService eventService;
    private final EventEditorService eventEditorService;
    private final EventViewService eventViewService;
    private final VoteService voteService;
    private final Provider<MapMenu> mapMenu;
    private final MenuService menuService;

    @Inject
    public EventMenu(Config config, GlobalConfig globalConfig, SessionService sessionService,
                     MapService mapService, EventService eventService, EventEditorService eventEditorService,
                     EventViewService eventViewService, VoteService voteService, Provider<MapMenu> mapMenu,
                     MenuService menuService) {
        super(config, globalConfig, sessionService);
        this.mapService = mapService;
        this.eventService = eventService;
        this.eventEditorService = eventEditorService;
        this.eventViewService = eventViewService;
        this.voteService = voteService;
        this.mapMenu = mapMenu;
        this.menuService = menuService;
    }

    @PostConstruct
    public void init() {
        menuService.registerRoute(new MainFlow());
        menuService.registerRoute(new EventDraftFlows.CreateStartFlow(this, eventEditorService));
        menuService.registerRoute(new EventsFlow());
        menuService.registerRoute(new EventFlow());
        menuService.registerRoute(new EventDraftFlows.EditFlow(this, eventEditorService));
        menuService.registerRoute(new EventDraftFlows.MapSelectionFlow(this, eventEditorService, mapService));
    }

    public void main(String uuid) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_MAIN));
    }

    private final class MainFlow implements RoutedMenuFlow<MainState> {
        @Override
        public String routeId() {
            return ROUTE_MAIN;
        }

        @Override
        public MainState createState(Session session, MenuRoute route, MainState currentState) {
            return currentState == null ? new MainState() : currentState;
        }

        @Override
        public Class<MainState> stateType() {
            return MainState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<MainState> context) {
            Session session = context.session();
            var local = context.locale();
            EventData active = eventViewService.activeEvent();

            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    MenuButton.of(local.t("event-menu-create-start"), ACTION_CREATE_START),
                    MenuButton.of(local.t("event-menu-events"), ACTION_EVENTS)
            ));

            if (active != null) {
                rows.add(List.of(MenuButton.of(local.t("event-menu-this-event"), ACTION_THIS_EVENT)));
            }

            if (session.player.admin) {
                List<MenuButton> adminRow = new ArrayList<>();
                if (voteService.getCurrentSession() instanceof VoteEvent) {
                    adminRow.add(MenuButton.of(local.t("event-menu-vote-stop"), ACTION_VOTE_STOP));
                }
                if (active != null && active.isActive) {
                    adminRow.add(MenuButton.of(local.t("event-menu-stop"), ACTION_STOP));
                }
                if (!adminRow.isEmpty()) {
                    rows.add(adminRow);
                }
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.hasHistory() || session.hasRouteHistory()) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("event-menu-main-title"),
                    local.t("event-menu-main-content"),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<MainState> context, String actionId) {
            Session session = context.session();
            String uuid = getUuid(session);
            EventData active = eventViewService.activeEvent();

            switch (actionId) {
                case ACTION_CREATE_START -> createStart(uuid, null);
                case ACTION_EVENTS -> context.openRoute(MenuRoute.of(ROUTE_EVENTS).withParam("page", "1"));
                case ACTION_THIS_EVENT -> {
                    if (active != null && active.id != null) {
                        context.openRoute(MenuRoute.of(ROUTE_EVENT).withParam("eventId", active.id.toHexString()));
                    }
                }
                case ACTION_VOTE_STOP -> voteService.endVote();
                case ACTION_STOP -> eventService.finishActiveEvent();
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }
    }

    public void createStart(String uuid, MapData map) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        eventEditorService.initializeDraft(session, map);
        session.menuService.renderRoute(session, MenuRoute.of(EventDraftFlows.ROUTE_CREATE_START));
    }

    public void edit(String uuid) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        if (!session.hasDraft(EventData.class)) { main(uuid); return; }
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(EventDraftFlows.ROUTE_EDIT));
    }

    public void event(String uuid, EventData event) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        String eventId = event != null && event.id != null ? event.id.toHexString() : "";
        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_EVENT).withParam("eventId", eventId));
    }

    public void events(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_EVENTS)
                .withParam("page", String.valueOf(page)));
    }

    private final class EventsFlow implements RoutedMenuFlow<EventsState> {
        @Override
        public String routeId() {
            return ROUTE_EVENTS;
        }

        @Override
        public EventsState createState(Session session, MenuRoute route, EventsState currentState) {
            EventsState state = currentState == null ? new EventsState() : currentState;
            state.page = route.intParam("page", 1);
            return state;
        }

        @Override
        public Class<EventsState> stateType() {
            return EventsState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<EventsState> context) {
            Session session = context.session();
            String uuid = getUuid(session);
            int requestedPage = Math.max(1, context.state().page);
            int perPage = globalConfig.eventsPerPage;
            EventViewService.EventPage eventPage = eventViewService.page(requestedPage, perPage, session.sortStatus);

            String menuContent;
            if (eventPage.isEmpty()) {
                menuContent = session.locale().t("event-menu-events-empty");
            } else {
                menuContent = session.locale().t("event-menu-events-content", args(
                        "page", eventPage.page(),
                        "total", eventPage.totalPages()
                ));
            }

            List<List<MenuButton>> rows = new ArrayList<>();

            rows.add(List.of(MenuButton.of(
                    session.locale().t("finished-" + session.sortStatus.getOrDefault("finished", StatusEnum.Neutral).name().toLowerCase()),
                    ACTION_STATUS_PREFIX + "finished")));
            rows.add(List.of(MenuButton.of(
                    session.locale().t("major-" + session.sortStatus.getOrDefault("major", StatusEnum.Neutral).name().toLowerCase()),
                    ACTION_STATUS_PREFIX + "major")));
            rows.add(List.of(MenuButton.of(
                    session.locale().t("active-" + session.sortStatus.getOrDefault("active", StatusEnum.Neutral).name().toLowerCase()),
                    ACTION_STATUS_PREFIX + "active")));

            List<MenuButton> paginationRow = new ArrayList<>();
            if (eventPage.hasPrevious()) {
                paginationRow.add(MenuButton.of(session.locale().t("previous"), ACTION_PREVIOUS));
            }
            if (eventPage.hasNext()) {
                paginationRow.add(MenuButton.of(session.locale().t("next"), ACTION_NEXT));
            }
            if (!paginationRow.isEmpty()) {
                rows.add(paginationRow);
            }

            for (EventData e : eventPage.events()) {
                String text = e.isActive ? session.locale().t("event-menu-events-selected", args("name", e.name)) : e.name;
                rows.add(List.of(MenuButton.of(text, ACTION_EVENT_PREFIX + e.id.toHexString())));
            }

            rows.add(List.of(MenuButton.of(session.locale().t("event-menu-main"), ACTION_MAIN)));

            List<MenuButton> navigation = new ArrayList<>();
            if (session.hasHistory() || session.hasRouteHistory()) {
                navigation.add(MenuButton.of(session.locale().t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(session.locale().t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    session.locale().t("event-menu-events-title"),
                    menuContent,
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<EventsState> context, String actionId) {
            Session session = context.session();
            String uuid = getUuid(session);
            int currentPage = Math.max(1, context.state().page);

            switch (actionId) {
                case ACTION_PREVIOUS -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_EVENTS).withParam("page", String.valueOf(currentPage - 1)));
                case ACTION_NEXT -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_EVENTS).withParam("page", String.valueOf(currentPage + 1)));
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                case ACTION_MAIN -> {
                    session.clearHistory();
                    main(uuid);
                }
                default -> {
                    if (actionId.startsWith(ACTION_STATUS_PREFIX)) {
                        String statusKey = actionId.substring(ACTION_STATUS_PREFIX.length());
                        session.setNextStatus(statusKey);
                        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_EVENTS).withParam("page", "1"));
                    } else if (actionId.startsWith(ACTION_EVENT_PREFIX)) {
                        String eventId = actionId.substring(ACTION_EVENT_PREFIX.length());
                        EventData event = eventViewService.findById(new ObjectId(eventId));
                        if (event != null) {
                            context.openRoute(MenuRoute.of(ROUTE_EVENT).withParam("eventId", eventId));
                        }
                    }
                }
            }
        }
    }

    private final class EventFlow implements RoutedMenuFlow<EventState> {
        @Override
        public String routeId() {
            return ROUTE_EVENT;
        }

        @Override
        public EventState createState(Session session, MenuRoute route, EventState currentState) {
            EventState state = currentState == null ? new EventState() : currentState;
            String eventId = route.param("eventId");
            state.eventId = eventId == null ? "" : eventId;
            return state;
        }

        @Override
        public Class<EventState> stateType() {
            return EventState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<EventState> context) {
            Session session = context.session();
            EventViewService.EventDetails details = resolveEventDetails(context.state().eventId);
            if (details == null || details.event() == null) {
                return eventNotFoundScreen(session);
            }

            EventData event = details.event();
            MapData mapData = details.map();
            PlayerData authorData = details.author();

            String yes = session.locale().t("yes");
            String no = session.locale().t("no");

            List<List<MenuButton>> rows = new ArrayList<>();
            Boolean currentVote = session.data.eventVotes.get(event.id.toString());
            String likeTxt = Boolean.TRUE.equals(currentVote) ? session.locale().t("map-vote-like-selected") : session.locale().t("map-vote-like");
            String dislikeTxt = Boolean.FALSE.equals(currentVote) ? session.locale().t("map-vote-dislike-selected") : session.locale().t("map-vote-dislike");
            rows.add(List.of(
                    MenuButton.of(likeTxt, ACTION_LIKE),
                    MenuButton.of(dislikeTxt, ACTION_DISLIKE)
            ));

            if (!voteService.isVoting()) {
                List<MenuButton> voteRow = new ArrayList<>();
                voteRow.add(MenuButton.of(session.locale().t("event-vote"), ACTION_VOTE));
                if (session.player.admin) {
                    voteRow.add(MenuButton.of(session.locale().t("event-avote"), ACTION_ADMIN_VOTE));
                }
                rows.add(voteRow);
            }

            boolean isOwner = session.data.id != null && session.data.id.equals(event.author);
            if (!event.isFinished && !event.isActive && (!event.isMajor || session.player.admin) && (isOwner || session.player.admin)) {
                rows.add(List.of(MenuButton.of(session.locale().t("event-menu-edit"), ACTION_EDIT)));
            }

            rows.add(List.of(MenuButton.of(session.locale().t("event-menu-events"), ACTION_EVENTS)));

            if (mapData != null) {
                rows.add(List.of(MenuButton.of(session.locale().t("event-menu-event-map"), ACTION_EVENT_MAP)));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.hasHistory() || session.hasRouteHistory()) {
                navigation.add(MenuButton.of(session.locale().t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(session.locale().t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    session.locale().t("event-menu-event-title"),
                    session.locale().t("event-menu-event-content", args(
                            "name", event.name,
                            "description", event.description,
                            "author", (authorData == null) ? "Unknown" : authorData.nickname,
                            "mapName", (mapData == null) ? "" : mapData.name,
                            "isMajor", event.isMajor ? yes : no,
                            "isConducted", event.isFinished ? yes : no,
                            "isActive", event.isActive ? yes : no,
                            "isTemporary", event.isTemporary ? yes : no,
                            "createdEventTime", formatTime(event.createdModelTime, session),
                            "plannedStartTime", formatTime(event.plannedStartTime, session),
                            "plannedEndTime", formatTime(event.plannedEndTime, session),
                            "like", event.like,
                            "dislike", event.dislike
                    )),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<EventState> context, String actionId) {
            Session session = context.session();
            EventViewService.EventDetails details = resolveEventDetails(context.state().eventId);
            if (details == null || details.event() == null) {
                switch (actionId) {
                    case ACTION_BACK -> context.goBack();
                    case ACTION_CLOSE -> context.close();
                    default -> {
                    }
                }
                return;
            }

            EventData event = details.event();
            MapData mapData = details.map();
            String uuid = getUuid(session);

            switch (actionId) {
                case ACTION_LIKE -> {
                    eventService.handleReputation(session.player, true, event);
                    context.render();
                }
                case ACTION_DISLIKE -> {
                    eventService.handleReputation(session.player, false, event);
                    context.render();
                }
                case ACTION_VOTE -> eventService.startVoteSession(session.player, event, false);
                case ACTION_ADMIN_VOTE -> eventService.startVoteSession(session.player, event, true);
                case ACTION_EDIT -> {
                    session.setDraft(event);
                    edit(uuid);
                }
                case ACTION_EVENTS -> context.openRoute(MenuRoute.of(ROUTE_EVENTS).withParam("page", "1"));
                case ACTION_EVENT_MAP -> {
                    if (mapData != null && mapData.id != null) {
                        context.openRoute(MenuRoute.of("map.details").withParam("mapId", mapData.id.toHexString()));
                    }
                }
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }
    }

    private EventViewService.EventDetails resolveEventDetails(String eventId) {
        try {
            ObjectId id = new ObjectId(eventId);
            return eventViewService.details(eventViewService.findById(id));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private MenuScreen eventNotFoundScreen(Session session) {
        List<MenuButton> navigation = new ArrayList<>();
        if (session.hasHistory() || session.hasRouteHistory()) {
            navigation.add(MenuButton.of(session.locale().t("back"), ACTION_BACK));
        }
        navigation.add(MenuButton.of(session.locale().t("close"), ACTION_CLOSE));

        return MenuScreen.normal(
                session.locale().t("event-menu-event-title"),
                session.locale().t("error-internal"),
                List.of(navigation)
        );
    }

    public static final class EventsState {
        public int page = 1;
    }

    public static final class MainState {
    }

    public static final class CreateStartState {
    }

    public static final class EventState {
        public String eventId = "";
    }

    public void mapSelection(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        if (!session.hasDraft(EventData.class)) { main(uuid); return; }
        session.clear();
        session.menuService.renderRoute(session, MenuRoute.of(EventDraftFlows.ROUTE_MAP_SELECTION)
                .withParam("page", String.valueOf(page)));
    }
}
