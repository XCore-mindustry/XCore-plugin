package org.xcore.plugin.ui.menu;

import org.bson.types.ObjectId;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.EventService;
import org.xcore.plugin.service.EventViewService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.vote.VoteEvent;
import org.xcore.plugin.vote.VoteService;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

final class EventFlows {

    static final String ROUTE_MAIN = "event.main";
    static final String ROUTE_EVENTS = "event.events";
    static final String ROUTE_EVENT = "event.details";

    private EventFlows() {
    }

    static final class MainFlow extends BaseMenuFlow<MainState> {
        private final EventMenu menu;
        private final EventViewService eventViewService;
        private final VoteService voteService;
        private final EventService eventService;

        MainFlow(EventMenu menu, EventViewService eventViewService, VoteService voteService, EventService eventService) {
            super(ROUTE_MAIN, MainState.class);
            this.menu = menu;
            this.eventViewService = eventViewService;
            this.voteService = voteService;
            this.eventService = eventService;

            action("create-start", ctx -> menu.createStart(menu.getUuid(ctx.session()), null));
            action("events", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_EVENTS).withParam("page", "1")));
            action("this-event", ctx -> {
                EventData active = eventViewService.activeEvent();
                if (active != null && active.id != null) {
                    ctx.openRoute(MenuRoute.of(ROUTE_EVENT).withParam("eventId", active.id.toHexString()));
                }
            });
            action("vote-stop", ctx -> voteService.endVote());
            action("stop", ctx -> eventService.finishActiveEvent());
        }

        @Override
        public MainState createState(Session session, MenuRoute route, MainState currentState) {
            return currentState == null ? new MainState() : currentState;
        }

        @Override
        public MenuScreen render(MenuRenderContext<MainState> context) {
            Session session = context.session();
            var local = context.locale();
            EventData active = eventViewService.activeEvent();
            String voteStatus = local.t(voteService.getCurrentSession() instanceof VoteEvent
                    ? "event-menu-vote-status-running"
                    : "event-menu-vote-status-idle");

            var grid = new MenuGrid();
            grid.row(
                    MenuButton.of(local.t("event-menu-events"), "events"),
                    MenuButton.of(local.t("event-menu-create-start"), "create-start")
            );

            grid.rowIf(active != null, MenuButton.of(local.t("event-menu-this-event"), "this-event"));

            if (session.player.admin) {
                List<MenuButton> adminRow = new ArrayList<>();
                if (voteService.getCurrentSession() instanceof VoteEvent) {
                    adminRow.add(MenuButton.of(local.t("event-menu-vote-stop"), "vote-stop"));
                }
                if (active != null && active.isActive) {
                    adminRow.add(MenuButton.of(local.t("event-menu-stop"), "stop"));
                }
                if (!adminRow.isEmpty()) {
                    grid.row(adminRow.toArray(new MenuButton[0]));
                }
            }

            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("event-menu-main-title"),
                    local.t("event-menu-main-content", args(
                            "currentEventState", eventStateText(session, active),
                            "currentEventName", active == null ? local.t("none") : displayText(session, active.name, "none"),
                            "voteStatus", voteStatus
                    )),
                    grid.build()
            );
        }
    }

    static final class EventsFlow extends BaseMenuFlow<EventsState> {
        private final EventMenu menu;
        private final EventViewService eventViewService;

        EventsFlow(EventMenu menu, EventViewService eventViewService) {
            super(ROUTE_EVENTS, EventsState.class);
            this.menu = menu;
            this.eventViewService = eventViewService;

            action("prev", ctx -> {
                int currentPage = Math.max(1, ctx.state().page);
                ctx.session().menuService.renderRoute(ctx.session(),
                        MenuRoute.of(ROUTE_EVENTS).withParam("page", String.valueOf(currentPage - 1)));
            });
            action("next", ctx -> {
                int currentPage = Math.max(1, ctx.state().page);
                ctx.session().menuService.renderRoute(ctx.session(),
                        MenuRoute.of(ROUTE_EVENTS).withParam("page", String.valueOf(currentPage + 1)));
            });
            action("main", ctx -> menu.main(menu.getUuid(ctx.session())));
            actionPrefix("status:", (ctx, key) -> {
                ctx.session().setNextStatus(key);
                ctx.session().menuService.renderRoute(ctx.session(),
                        MenuRoute.of(ROUTE_EVENTS).withParam("page", "1"));
            });
            actionPrefix("event:", (ctx, eventId) -> {
                EventData event = eventViewService.findById(new ObjectId(eventId));
                if (event != null) {
                    ctx.openRoute(MenuRoute.of(ROUTE_EVENT).withParam("eventId", eventId));
                }
            });
        }

        @Override
        public EventsState createState(Session session, MenuRoute route, EventsState currentState) {
            EventsState state = currentState == null ? new EventsState() : currentState;
            state.page = route.intParam("page", 1);
            return state;
        }

        @Override
        public MenuScreen render(MenuRenderContext<EventsState> context) {
            Session session = context.session();
            int requestedPage = Math.max(1, context.state().page);
            int perPage = menu.secretsConfig.pagination.eventsPerPage;
            EventViewService.EventPage eventPage = eventViewService.page(requestedPage, perPage, session.sortStatus);
            String finishedFilter = session.locale().t("finished-" + session.sortStatus.getOrDefault("finished", StatusEnum.Neutral).name().toLowerCase());
            String majorFilter = session.locale().t("major-" + session.sortStatus.getOrDefault("major", StatusEnum.Neutral).name().toLowerCase());
            String activeFilter = session.locale().t("active-" + session.sortStatus.getOrDefault("active", StatusEnum.Neutral).name().toLowerCase());
            int displayTotalPages = Math.max(1, eventPage.totalPages());

            String menuContent;
            if (eventPage.isEmpty()) {
                menuContent = session.locale().t("event-menu-events-empty", args(
                        "finished", finishedFilter,
                        "major", majorFilter,
                        "active", activeFilter
                ));
            } else {
                menuContent = session.locale().t("event-menu-events-content", args(
                        "page", eventPage.page(),
                        "total", displayTotalPages,
                        "count", eventPage.total(),
                        "finished", finishedFilter,
                        "major", majorFilter,
                        "active", activeFilter
                ));
            }

            var grid = new MenuGrid();

            grid.row(
                    MenuButton.of(finishedFilter, "status:finished"),
                    MenuButton.of(majorFilter, "status:major"),
                    MenuButton.of(activeFilter, "status:active")
            );

            List<MenuButton> paginationRow = new ArrayList<>();
            if (eventPage.hasPrevious()) {
                paginationRow.add(MenuButton.of(session.locale().t("previous"), "prev"));
            }
            if (eventPage.hasNext()) {
                paginationRow.add(MenuButton.of(session.locale().t("next"), "next"));
            }
            if (!paginationRow.isEmpty()) {
                grid.row(paginationRow.toArray(new MenuButton[0]));
            }

            for (EventData e : eventPage.events()) {
                String text = session.locale().t(
                        e.isActive ? "event-menu-events-selected" : "event-menu-events-row",
                        args(
                                "state", eventStateText(session, e),
                                "type", eventTypeText(session, e),
                                "name", displayText(session, e.name, "none")
                        )
                );
                grid.row(MenuButton.of(text, "event:" + e.id.toHexString()));
            }

            grid.row(MenuButton.of(session.locale().t("event-menu-main"), "main"));
            grid.defaultNavigation(session, session.locale());

            return MenuScreen.normal(
                    session.locale().t("event-menu-events-title"),
                    menuContent,
                    grid.build()
            );
        }
    }

    static final class EventFlow extends BaseMenuFlow<EventState> {
        private final EventMenu menu;
        private final EventViewService eventViewService;
        private final VoteService voteService;
        private final EventService eventService;

        EventFlow(EventMenu menu, EventViewService eventViewService, VoteService voteService, EventService eventService) {
            super(ROUTE_EVENT, EventState.class);
            this.menu = menu;
            this.eventViewService = eventViewService;
            this.voteService = voteService;
            this.eventService = eventService;

            action("like", ctx -> {
                EventViewService.EventDetails details = resolveEventDetails(eventViewService, ctx.state().eventId);
                if (details != null && details.event() != null) {
                    eventService.handleReputation(ctx.session().player, true, details.event());
                    ctx.render();
                }
            });
            action("dislike", ctx -> {
                EventViewService.EventDetails details = resolveEventDetails(eventViewService, ctx.state().eventId);
                if (details != null && details.event() != null) {
                    eventService.handleReputation(ctx.session().player, false, details.event());
                    ctx.render();
                }
            });
            action("vote", ctx -> {
                EventViewService.EventDetails details = resolveEventDetails(eventViewService, ctx.state().eventId);
                if (details != null && details.event() != null) {
                    eventService.startVoteSession(ctx.session().player, details.event(), false);
                }
            });
            action("admin-vote", ctx -> {
                EventViewService.EventDetails details = resolveEventDetails(eventViewService, ctx.state().eventId);
                if (details != null && details.event() != null) {
                    eventService.startVoteSession(ctx.session().player, details.event(), true);
                }
            });
            action("edit", ctx -> {
                EventViewService.EventDetails details = resolveEventDetails(eventViewService, ctx.state().eventId);
                if (details != null && details.event() != null) {
                    ctx.session().setDraft(EventData.class, details.event());
                    menu.edit(menu.getUuid(ctx.session()));
                }
            });
            action("events", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_EVENTS).withParam("page", "1")));
            action("event-map", ctx -> {
                EventViewService.EventDetails details = resolveEventDetails(eventViewService, ctx.state().eventId);
                if (details != null && details.map() != null && details.map().id != null) {
                    ctx.openRoute(MenuRoute.of("map.details").withParam("mapId", details.map().id.toHexString()));
                }
            });
        }

        @Override
        public EventState createState(Session session, MenuRoute route, EventState currentState) {
            EventState state = currentState == null ? new EventState() : currentState;
            String eventId = route.param("eventId");
            state.eventId = eventId == null ? "" : eventId;
            return state;
        }

        @Override
        public MenuScreen render(MenuRenderContext<EventState> context) {
            Session session = context.session();
            EventViewService.EventDetails details = resolveEventDetails(eventViewService, context.state().eventId);
            if (details == null || details.event() == null) {
                return eventNotFoundScreen(session);
            }

            EventData event = details.event();
            MapData mapData = details.map();
            PlayerData authorData = details.author();

            String yes = session.locale().t("yes");
            String no = session.locale().t("no");

            Boolean currentVote = session.data.eventVotes.get(event.id.toString());
            String likeTxt = Boolean.TRUE.equals(currentVote) ? session.locale().t("map-vote-like-selected") : session.locale().t("map-vote-like");
            String dislikeTxt = Boolean.FALSE.equals(currentVote) ? session.locale().t("map-vote-dislike-selected") : session.locale().t("map-vote-dislike");

            var grid = new MenuGrid();
            grid.row(
                    MenuButton.of(likeTxt, "like"),
                    MenuButton.of(dislikeTxt, "dislike")
            );

            if (!voteService.isVoting()) {
                List<MenuButton> voteRow = new ArrayList<>();
                voteRow.add(MenuButton.of(session.locale().t("event-vote"), "vote"));
                if (session.player.admin) {
                    voteRow.add(MenuButton.of(session.locale().t("event-avote"), "admin-vote"));
                }
                grid.row(voteRow.toArray(new MenuButton[0]));
            }

            boolean isOwner = session.data.id != null && session.data.id.equals(event.author);
            boolean canEdit = !event.isFinished && !event.isActive && (!event.isMajor || session.player.admin) && (isOwner || session.player.admin);

            if (mapData != null) {
                grid.row(MenuButton.of(session.locale().t("event-menu-event-map"), "event-map"));
            }

            if (canEdit) {
                grid.row(MenuButton.of(session.locale().t("event-menu-edit"), "edit"));
            }

            grid.row(MenuButton.of(session.locale().t("event-menu-events"), "events"));

            grid.defaultNavigation(session, session.locale());

            return MenuScreen.normal(
                    session.locale().t("event-menu-event-title"),
                    session.locale().t("event-menu-event-content", args(
                            "name", displayText(session, event.name, "none"),
                            "description", displayText(session, event.description, "no-description"),
                            "author", authorData == null ? session.locale().t("none") : displayText(session, authorData.nickname, "none"),
                            "mapName", mapData == null ? session.locale().t("none") : displayText(session, mapData.name, "none"),
                            "eventType", eventTypeText(session, event),
                            "eventState", eventStateText(session, event),
                            "isTemporary", event.isTemporary ? yes : no,
                            "createdEventTime", menu.formatTime(event.createdModelTime, session),
                            "plannedStartTime", menu.formatTime(event.plannedStartTime, session),
                            "plannedEndTime", menu.formatTime(event.plannedEndTime, session),
                            "like", event.like,
                            "dislike", event.dislike
                    )),
                    grid.build()
            );
        }

        @Override
        public void onAction(MenuRenderContext<EventState> context, String actionId) {
            EventViewService.EventDetails details = resolveEventDetails(eventViewService, context.state().eventId);
            if (details == null || details.event() == null) {
                if ("back".equals(actionId)) {
                    context.goBack();
                } else if ("close".equals(actionId)) {
                    context.close();
                }
                return;
            }
            super.onAction(context, actionId);
        }
    }

    static EventViewService.EventDetails resolveEventDetails(EventViewService eventViewService, String eventId) {
        try {
            ObjectId id = new ObjectId(eventId);
            return eventViewService.details(eventViewService.findById(id));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static MenuScreen eventNotFoundScreen(Session session) {
        var grid = new MenuGrid();
        grid.defaultNavigation(session, session.locale());
        return MenuScreen.normal(
                session.locale().t("event-menu-event-title"),
                session.locale().t("error-internal"),
                grid.build()
        );
    }

    static final class MainState {
    }

    static final class EventsState {
        public int page = 1;
    }

    static final class EventState {
        public String eventId = "";
    }

    private static String eventTypeText(Session session, EventData event) {
        return session.locale().t(event != null && event.isMajor ? "event-menu-type-major" : "event-menu-type-regular");
    }

    private static String eventStateText(Session session, EventData event) {
        if (event == null) {
            return session.locale().t("event-menu-state-none");
        }
        if (event.isFinished) {
            return session.locale().t("event-menu-state-finished");
        }
        if (event.isActive) {
            return session.locale().t("event-menu-state-active");
        }
        return session.locale().t("event-menu-state-planned");
    }

    private static String displayText(Session session, String value, String fallbackKey) {
        return value == null || value.isBlank() ? session.locale().t(fallbackKey) : value;
    }
}
