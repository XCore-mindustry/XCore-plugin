package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.maps.Map;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.common.SeqStream;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.EventEditorService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuPromptContext;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

final class EventDraftFlows {

    static final String ROUTE_CREATE_START = "event.create-start";
    static final String ROUTE_EDIT = "event.edit";
    static final String ROUTE_MAP_SELECTION = "event.map-selection";

    private static final String PROMPT_CREATE_NAME = "event-create-name";
    private static final String PROMPT_EDIT_NAME = "event-edit-name";
    private static final String PROMPT_EDIT_DESCRIPTION = "event-edit-description";
    private EventDraftFlows() {
    }

    static final class CreateStartFlow extends BaseMenuFlow<CreateStartState> {
        private final EventMenu menu;
        private final EventEditorService eventEditorService;

        CreateStartFlow(EventMenu menu, EventEditorService eventEditorService) {
            super(ROUTE_CREATE_START, CreateStartState.class);
            this.menu = menu;
            this.eventEditorService = eventEditorService;

            onPrompt(PROMPT_CREATE_NAME, ctx -> {
                Session session = ctx.renderContext().session();
                EventData draft = session.getDraft(EventData.class);
                eventEditorService.updateName(draft, ctx.text());
                menu.edit(menu.getUuid(session));
            }, ctx -> {
                Session session = ctx.session();
                eventEditorService.cancelDraft(session);
                menu.main(menu.getUuid(session));
            });
        }

        @Override
        public CreateStartState createState(Session session, MenuRoute route, CreateStartState currentState) {
            return currentState == null ? new CreateStartState() : currentState;
        }

        @Override
        public MenuScreen render(MenuRenderContext<CreateStartState> context) {
            Session session = context.session();
            if (!session.hasDraft(EventData.class)) {
                return placeholderScreen();
            }

            context.openPrompt(new MenuPrompt(
                    PROMPT_CREATE_NAME,
                    session.locale().t("event-menu-create-start-title"),
                    session.locale().t("event-menu-create-start-message"),
                    20,
                    session.locale().t("event-menu-create-start-default", args("playerName", session.player.name)),
                    false
            ));
            return placeholderScreen();
        }
    }

    static final class EditFlow extends BaseMenuFlow<EditState> {
        private final EventMenu menu;
        private final EventEditorService eventEditorService;

        EditFlow(EventMenu menu, EventEditorService eventEditorService) {
            super(ROUTE_EDIT, EditState.class);
            this.menu = menu;
            this.eventEditorService = eventEditorService;

            action("edit-name", ctx -> {
                Session session = ctx.session();
                EventData draft = session.getDraft(EventData.class);
                ctx.openPrompt(new MenuPrompt(
                        PROMPT_EDIT_NAME,
                        session.locale().t("event-menu-edit-name-title"),
                        "",
                        24,
                        draft.name,
                        false
                ));
            });
            action("edit-name-reset", ctx -> {
                eventEditorService.resetName(ctx.session().getDraft(EventData.class));
                ctx.render();
            });
            action("edit-description", ctx -> {
                Session session = ctx.session();
                EventData draft = session.getDraft(EventData.class);
                ctx.openPrompt(new MenuPrompt(
                        PROMPT_EDIT_DESCRIPTION,
                        session.locale().t("event-menu-edit-description-title"),
                        "",
                        1000,
                        draft.description,
                        false
                ));
            });
            action("edit-map", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_MAP_SELECTION).withParam("page", "1")));
            action("toggle-temporary", ctx -> {
                eventEditorService.toggleTemporary(ctx.session().getDraft(EventData.class));
                ctx.render();
            });
            action("edit-planned-start", ctx -> {
                Session session = ctx.session();
                EventData draft = session.getDraft(EventData.class);
                session.setDraft(DateTimePickerFlows.PickerState.class, DateTimePickerFlows.state(
                        "event-menu-edit-planned-start",
                        draft.plannedStartTime,
                        value -> eventEditorService.updatePlannedStartTime(session.getDraft(EventData.class), value)
                ));
                ctx.openRoute(MenuRoute.of(DateTimePickerFlows.ROUTE_PICKER));
            });
            action("edit-planned-end", ctx -> {
                Session session = ctx.session();
                EventData draft = session.getDraft(EventData.class);
                session.setDraft(DateTimePickerFlows.PickerState.class, DateTimePickerFlows.state(
                        "event-menu-edit-planned-end",
                        draft.plannedEndTime,
                        value -> eventEditorService.updatePlannedEndTime(session.getDraft(EventData.class), value)
                ));
                ctx.openRoute(MenuRoute.of(DateTimePickerFlows.ROUTE_PICKER));
            });
            action("save", ctx -> {
                if (eventEditorService.saveDraft(ctx.session())) {
                    menu.events(menu.getUuid(ctx.session()), 1);
                }
            });
            action("cancel-edit", ctx -> {
                eventEditorService.cancelDraft(ctx.session());
                menu.main(menu.getUuid(ctx.session()));
            });
            action("toggle-major", ctx -> {
                eventEditorService.toggleMajor(ctx.session().getDraft(EventData.class));
                ctx.render();
            });

            onPrompt(PROMPT_EDIT_NAME, ctx -> {
                eventEditorService.updateName(ctx.renderContext().session().getDraft(EventData.class), ctx.text());
                ctx.renderContext().render();
            }, ctx -> ctx.render());
            onPrompt(PROMPT_EDIT_DESCRIPTION, ctx -> {
                eventEditorService.updateDescription(ctx.renderContext().session().getDraft(EventData.class), ctx.text());
                ctx.renderContext().render();
            }, ctx -> ctx.render());
        }

        @Override
        public EditState createState(Session session, MenuRoute route, EditState currentState) {
            return currentState == null ? new EditState() : currentState;
        }

        @Override
        public MenuScreen render(MenuRenderContext<EditState> context) {
            Session session = context.session();
            EventData draft = session.getDraft(EventData.class);
            MapData mapData = eventEditorService.findMapForDraft(draft);
            PlayerData authorData = eventEditorService.findAuthorForDraft(draft);

            String yes = session.locale().t("yes");
            String no = session.locale().t("no");

            var grid = new MenuGrid();
            grid.row(
                    MenuButton.of(session.locale().t("event-menu-edit-name"), "edit-name"),
                    MenuButton.of(session.locale().t("event-menu-edit-description"), "edit-description")
            );
            grid.row(
                    MenuButton.of(session.locale().t("event-menu-edit-map"), "edit-map"),
                    MenuButton.of(session.locale().t("event-menu-edit-planned-start"), "edit-planned-start"),
                    MenuButton.of(session.locale().t("event-menu-edit-planned-end"), "edit-planned-end")
            );
            List<MenuButton> flagsRow = new ArrayList<>();
            flagsRow.add(MenuButton.of(session.locale().t(draft.isTemporary ? "event-menu-edit-temporary-active" : "event-menu-edit-temporary-inactive"), "toggle-temporary"));
            if (session.player.admin) {
                flagsRow.add(MenuButton.of(
                        session.locale().t(draft.isMajor ? "event-menu-edit-major-active" : "event-menu-edit-major-inactive"),
                        "toggle-major"
                ));
            }
            grid.row(flagsRow.toArray(new MenuButton[0]));
            grid.row(MenuButton.of(session.locale().t("event-menu-edit-name-reset"), "edit-name-reset"));
            grid.row(
                    MenuButton.of("[green]" + session.locale().t("save"), "save"),
                    MenuButton.of("[red]" + session.locale().t("cancel"), "cancel-edit")
            );

            return MenuScreen.normal(
                    session.locale().t("event-menu-edit-title"),
                    session.locale().t("event-menu-edit-content", args(
                            "name", displayText(session, draft.name, "none"),
                            "description", displayText(session, draft.description, "no-description"),
                            "author", authorData == null ? session.locale().t("none") : displayText(session, authorData.nickname, "none"),
                            "mapName", mapData == null ? session.locale().t("none") : displayText(session, mapData.name, "none"),
                            "eventType", session.locale().t(draft.isMajor ? "event-menu-type-major" : "event-menu-type-regular"),
                            "isTemporary", draft.isTemporary ? yes : no,
                            "plannedStartTime", menu.formatTime(draft.plannedStartTime, session),
                            "plannedEndTime", menu.formatTime(draft.plannedEndTime, session)
                    )),
                    grid.build()
            );
        }

        private String displayText(Session session, String value, String fallbackKey) {
            return value == null || value.isBlank() ? session.locale().t(fallbackKey) : value;
        }
    }

    static final class MapSelectionFlow extends BaseMenuFlow<MapSelectionState> {
        private final EventMenu menu;
        private final EventEditorService eventEditorService;
        private final MapService mapService;

        MapSelectionFlow(EventMenu menu, EventEditorService eventEditorService, MapService mapService) {
            super(ROUTE_MAP_SELECTION, MapSelectionState.class);
            this.menu = menu;
            this.eventEditorService = eventEditorService;
            this.mapService = mapService;

            action("prev", ctx -> {
                int currentPage = Math.max(1, ctx.state().page);
                ctx.session().menuService.renderRoute(ctx.session(),
                        MenuRoute.of(ROUTE_MAP_SELECTION).withParam("page", String.valueOf(currentPage - 1)));
            });
            action("next", ctx -> {
                int currentPage = Math.max(1, ctx.state().page);
                ctx.session().menuService.renderRoute(ctx.session(),
                        MenuRoute.of(ROUTE_MAP_SELECTION).withParam("page", String.valueOf(currentPage + 1)));
            });
            actionPrefix("map:", (ctx, indexStr) -> {
                int index;
                try {
                    index = Integer.parseInt(indexStr);
                } catch (NumberFormatException ignored) {
                    return;
                }
                Session session = ctx.session();
                int currentPage = Math.max(1, ctx.state().page);
                List<Map> pagedMaps = SeqStream.of(mapService.getAvailableMaps())
                        .gather(CustomGatherers.page(menu.globalConfig.mapsPerPage, currentPage))
                        .flatMap(List::stream)
                        .toList();
                if (index < 0 || index >= pagedMaps.size()) {
                    return;
                }
                Map map = pagedMaps.get(index);
                eventEditorService.selectMapForDraft(session, map.plainName(), map.file.name(), map.author(), Vars.state.rules.mode().name());
                menu.edit(menu.getUuid(session));
            });
        }

        @Override
        public MapSelectionState createState(Session session, MenuRoute route, MapSelectionState currentState) {
            MapSelectionState state = currentState == null ? new MapSelectionState() : currentState;
            state.page = route.intParam("page", 1);
            return state;
        }

        @Override
        public MenuScreen render(MenuRenderContext<MapSelectionState> context) {
            Session session = context.session();
            Seq<Map> maps = mapService.getAvailableMaps();
            var pagination = CustomGatherers.calculatePagination(maps.size, menu.globalConfig.mapsPerPage);

            int validPage = pagination.clampPage(Math.max(1, context.state().page));
            List<Map> pagedMaps = SeqStream.of(maps)
                    .gather(CustomGatherers.page(menu.globalConfig.mapsPerPage, validPage))
                    .flatMap(List::stream)
                    .toList();

            var grid = new MenuGrid();

            List<MenuButton> paginationRow = new ArrayList<>();
            if (validPage > 1) {
                paginationRow.add(MenuButton.of(session.locale().t("previous"), "prev"));
            }
            if (validPage < pagination.totalPages()) {
                paginationRow.add(MenuButton.of(session.locale().t("next"), "next"));
            }
            if (!paginationRow.isEmpty()) {
                grid.row(paginationRow.toArray(new MenuButton[0]));
            }

            for (int i = 0; i < pagedMaps.size(); i++) {
                Map map = pagedMaps.get(i);
                grid.row(MenuButton.of(map.name(), "map:" + i));
            }

            grid.defaultNavigation(session, session.locale());

            return MenuScreen.normal(
                    session.locale().t("event-menu-maps-title"),
                    session.locale().t("event-menu-maps-content", args("page", validPage, "total", pagination.totalPages())),
                    grid.build()
            );
        }
    }

    static final class CreateStartState {
    }

    static final class EditState {
    }

    static final class MapSelectionState {
        public int page = 1;
    }

    private static MenuScreen placeholderScreen() {
        return MenuScreen.normal("", "", List.of());
    }
}
