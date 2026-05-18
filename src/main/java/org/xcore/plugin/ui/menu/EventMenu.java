package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.EventEditorService;
import org.xcore.plugin.service.EventService;
import org.xcore.plugin.service.EventViewService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.vote.VoteService;

@Singleton
public class EventMenu extends Menu {

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
        menuService.registerRoute(new DateTimePickerFlows.PickerFlow());
        menuService.registerRoute(new EventFlows.MainFlow(this, eventViewService, voteService, eventService));
        menuService.registerRoute(new EventDraftFlows.CreateStartFlow(this, eventEditorService));
        menuService.registerRoute(new EventFlows.EventsFlow(this, eventViewService));
        menuService.registerRoute(new EventFlows.EventFlow(this, eventViewService, voteService, eventService));
        menuService.registerRoute(new EventDraftFlows.EditFlow(this, eventEditorService));
        menuService.registerRoute(new EventDraftFlows.MapSelectionFlow(this, eventEditorService, mapService));
    }

    public void main(String uuid) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(EventFlows.ROUTE_MAIN));
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
        session.menuService.renderRoute(session, MenuRoute.of(EventFlows.ROUTE_EVENT).withParam("eventId", eventId));
    }

    public void events(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(EventFlows.ROUTE_EVENTS)
                .withParam("page", String.valueOf(page)));
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
