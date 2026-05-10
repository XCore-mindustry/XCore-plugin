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
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

final class EventDraftFlows {

    static final String ROUTE_CREATE_START = "event.create-start";
    static final String ROUTE_EDIT = "event.edit";
    static final String ROUTE_MAP_SELECTION = "event.map-selection";

    private static final String ACTION_EDIT_NAME = "edit-name";
    private static final String ACTION_EDIT_NAME_RESET = "edit-name-reset";
    private static final String ACTION_EDIT_DESCRIPTION = "edit-description";
    private static final String ACTION_EDIT_MAP = "edit-map";
    private static final String ACTION_TOGGLE_TEMPORARY = "toggle-temporary";
    private static final String ACTION_EDIT_PLANNED_START = "edit-planned-start";
    private static final String ACTION_EDIT_PLANNED_END = "edit-planned-end";
    private static final String ACTION_SAVE = "save";
    private static final String ACTION_CANCEL_EDIT = "cancel-edit";
    private static final String ACTION_TOGGLE_MAJOR = "toggle-major";
    private static final String ACTION_PREVIOUS = "previous";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_MAP_PREFIX = "map:";

    private static final String PROMPT_CREATE_NAME = "event-create-name";
    private static final String PROMPT_EDIT_NAME = "event-edit-name";
    private static final String PROMPT_EDIT_DESCRIPTION = "event-edit-description";
    private static final String PROMPT_EDIT_PLANNED_START = "event-edit-planned-start";
    private static final String PROMPT_EDIT_PLANNED_END = "event-edit-planned-end";

    private EventDraftFlows() {
    }

    static final class CreateStartFlow implements RoutedMenuFlow<CreateStartState> {
        private final EventMenu menu;
        private final EventEditorService eventEditorService;

        CreateStartFlow(EventMenu menu, EventEditorService eventEditorService) {
            this.menu = menu;
            this.eventEditorService = eventEditorService;
        }

        @Override
        public String routeId() {
            return ROUTE_CREATE_START;
        }

        @Override
        public CreateStartState createState(Session session, MenuRoute route, CreateStartState currentState) {
            return currentState == null ? new CreateStartState() : currentState;
        }

        @Override
        public Class<CreateStartState> stateType() {
            return CreateStartState.class;
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

        @Override
        public void onPromptSubmit(MenuRenderContext<CreateStartState> context, String promptId, String text) {
            if (!PROMPT_CREATE_NAME.equals(promptId)) {
                return;
            }

            Session session = context.session();
            EventData draft = session.getDraft(EventData.class);
            eventEditorService.updateName(draft, text);
            menu.edit(menu.getUuid(session));
        }

        @Override
        public void onPromptCancel(MenuRenderContext<CreateStartState> context, String promptId) {
            if (!PROMPT_CREATE_NAME.equals(promptId)) {
                return;
            }

            Session session = context.session();
            eventEditorService.cancelDraft(session);
            menu.main(menu.getUuid(session));
        }
    }

    static final class EditFlow implements RoutedMenuFlow<EditState> {
        private final EventMenu menu;
        private final EventEditorService eventEditorService;

        EditFlow(EventMenu menu, EventEditorService eventEditorService) {
            this.menu = menu;
            this.eventEditorService = eventEditorService;
        }

        @Override
        public String routeId() {
            return ROUTE_EDIT;
        }

        @Override
        public EditState createState(Session session, MenuRoute route, EditState currentState) {
            return currentState == null ? new EditState() : currentState;
        }

        @Override
        public Class<EditState> stateType() {
            return EditState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<EditState> context) {
            Session session = context.session();
            EventData draft = session.getDraft(EventData.class);
            MapData mapData = eventEditorService.findMapForDraft(draft);
            PlayerData authorData = eventEditorService.findAuthorForDraft(draft);

            String yes = session.locale().t("yes");
            String no = session.locale().t("no");

            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    MenuButton.of(session.locale().t("event-menu-edit-name"), ACTION_EDIT_NAME),
                    MenuButton.of(session.locale().t("event-menu-edit-name-reset"), ACTION_EDIT_NAME_RESET),
                    MenuButton.of(session.locale().t("event-menu-edit-description"), ACTION_EDIT_DESCRIPTION)
            ));
            rows.add(List.of(
                    MenuButton.of(session.locale().t("event-menu-edit-map"), ACTION_EDIT_MAP),
                    MenuButton.of(session.locale().t(draft.isTemporary ? "event-menu-edit-temporary-active" : "event-menu-edit-temporary-inactive"), ACTION_TOGGLE_TEMPORARY)
            ));
            rows.add(List.of(
                    MenuButton.of(session.locale().t("event-menu-edit-planned-start"), ACTION_EDIT_PLANNED_START),
                    MenuButton.of(session.locale().t("event-menu-edit-planned-end"), ACTION_EDIT_PLANNED_END)
            ));
            rows.add(List.of(
                    MenuButton.of("[green]" + session.locale().t("save"), ACTION_SAVE),
                    MenuButton.of("[red]" + session.locale().t("cancel"), ACTION_CANCEL_EDIT)
            ));
            if (session.player.admin) {
                rows.add(List.of(MenuButton.of(
                        session.locale().t(draft.isMajor ? "event-menu-edit-major-active" : "event-menu-edit-major-inactive"),
                        ACTION_TOGGLE_MAJOR
                )));
            }

            return MenuScreen.normal(
                    session.locale().t("event-menu-edit-title"),
                    session.locale().t("event-menu-edit-content", args(
                            "name", draft.name,
                            "description", draft.description,
                            "author", (authorData == null) ? "Unknown" : authorData.nickname,
                            "mapName", (mapData == null) ? "" : mapData.name,
                            "isMajor", draft.isMajor ? yes : no,
                            "isTemporary", draft.isTemporary ? yes : no,
                            "plannedStartTime", menu.formatTime(draft.plannedStartTime, session),
                            "plannedEndTime", menu.formatTime(draft.plannedEndTime, session)
                    )),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<EditState> context, String actionId) {
            Session session = context.session();
            EventData draft = session.getDraft(EventData.class);
            String uuid = menu.getUuid(session);

            switch (actionId) {
                case ACTION_EDIT_NAME -> context.openPrompt(new MenuPrompt(
                        PROMPT_EDIT_NAME,
                        session.locale().t("event-menu-edit-name-title"),
                        "",
                        24,
                        draft.name,
                        false
                ));
                case ACTION_EDIT_NAME_RESET -> {
                    eventEditorService.resetName(draft);
                    context.render();
                }
                case ACTION_EDIT_DESCRIPTION -> context.openPrompt(new MenuPrompt(
                        PROMPT_EDIT_DESCRIPTION,
                        session.locale().t("event-menu-edit-description-title"),
                        "",
                        1000,
                        draft.description,
                        false
                ));
                case ACTION_EDIT_MAP -> context.openRoute(MenuRoute.of(ROUTE_MAP_SELECTION).withParam("page", "1"));
                case ACTION_TOGGLE_TEMPORARY -> {
                    eventEditorService.toggleTemporary(draft);
                    context.render();
                }
                case ACTION_EDIT_PLANNED_START -> context.openPrompt(new MenuPrompt(
                        PROMPT_EDIT_PLANNED_START,
                        session.locale().t("event-menu-edit-planned-start-title"),
                        "",
                        64,
                        "",
                        false
                ));
                case ACTION_EDIT_PLANNED_END -> context.openPrompt(new MenuPrompt(
                        PROMPT_EDIT_PLANNED_END,
                        session.locale().t("event-menu-edit-planned-end-title"),
                        "",
                        10,
                        "",
                        false
                ));
                case ACTION_SAVE -> {
                    if (eventEditorService.saveDraft(session)) {
                        menu.events(uuid, 1);
                    }
                }
                case ACTION_CANCEL_EDIT -> {
                    eventEditorService.cancelDraft(session);
                    menu.main(uuid);
                }
                case ACTION_TOGGLE_MAJOR -> {
                    eventEditorService.toggleMajor(draft);
                    context.render();
                }
                default -> {
                }
            }
        }

        @Override
        public void onPromptSubmit(MenuRenderContext<EditState> context, String promptId, String text) {
            Session session = context.session();
            EventData draft = session.getDraft(EventData.class);

            switch (promptId) {
                case PROMPT_EDIT_NAME -> eventEditorService.updateName(draft, text);
                case PROMPT_EDIT_DESCRIPTION -> eventEditorService.updateDescription(draft, text);
                case PROMPT_EDIT_PLANNED_START -> eventEditorService.updatePlannedStartTime(draft, text);
                case PROMPT_EDIT_PLANNED_END -> eventEditorService.updatePlannedEndTime(draft, text);
                default -> {
                    return;
                }
            }

            context.render();
        }

        @Override
        public void onPromptCancel(MenuRenderContext<EditState> context, String promptId) {
            switch (promptId) {
                case PROMPT_EDIT_NAME, PROMPT_EDIT_DESCRIPTION, PROMPT_EDIT_PLANNED_START, PROMPT_EDIT_PLANNED_END -> context.render();
                default -> {
                }
            }
        }
    }

    static final class MapSelectionFlow implements RoutedMenuFlow<MapSelectionState> {
        private final EventMenu menu;
        private final EventEditorService eventEditorService;
        private final MapService mapService;

        MapSelectionFlow(EventMenu menu, EventEditorService eventEditorService, MapService mapService) {
            this.menu = menu;
            this.eventEditorService = eventEditorService;
            this.mapService = mapService;
        }

        @Override
        public String routeId() {
            return ROUTE_MAP_SELECTION;
        }

        @Override
        public MapSelectionState createState(Session session, MenuRoute route, MapSelectionState currentState) {
            MapSelectionState state = currentState == null ? new MapSelectionState() : currentState;
            state.page = route.intParam("page", 1);
            return state;
        }

        @Override
        public Class<MapSelectionState> stateType() {
            return MapSelectionState.class;
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

            List<List<MenuButton>> rows = new ArrayList<>();

            List<MenuButton> paginationRow = new ArrayList<>();
            if (validPage > 1) {
                paginationRow.add(MenuButton.of(session.locale().t("previous"), ACTION_PREVIOUS));
            }
            if (validPage < pagination.totalPages()) {
                paginationRow.add(MenuButton.of(session.locale().t("next"), ACTION_NEXT));
            }
            if (!paginationRow.isEmpty()) {
                rows.add(paginationRow);
            }

            for (int i = 0; i < pagedMaps.size(); i++) {
                Map map = pagedMaps.get(i);
                rows.add(List.of(MenuButton.of(map.name(), ACTION_MAP_PREFIX + i)));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.hasHistory() || session.hasRouteHistory()) {
                navigation.add(MenuButton.of(session.locale().t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(session.locale().t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    session.locale().t("event-menu-maps-title"),
                    session.locale().t("event-menu-maps-content", args("page", validPage, "total", pagination.totalPages())),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<MapSelectionState> context, String actionId) {
            Session session = context.session();
            String uuid = menu.getUuid(session);
            int currentPage = Math.max(1, context.state().page);

            switch (actionId) {
                case ACTION_PREVIOUS -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_MAP_SELECTION).withParam("page", String.valueOf(currentPage - 1)));
                case ACTION_NEXT -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_MAP_SELECTION).withParam("page", String.valueOf(currentPage + 1)));
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                    if (!actionId.startsWith(ACTION_MAP_PREFIX)) {
                        return;
                    }

                    int index;
                    try {
                        index = Integer.parseInt(actionId.substring(ACTION_MAP_PREFIX.length()));
                    } catch (NumberFormatException ignored) {
                        return;
                    }

                    List<Map> pagedMaps = SeqStream.of(mapService.getAvailableMaps())
                            .gather(CustomGatherers.page(menu.globalConfig.mapsPerPage, currentPage))
                            .flatMap(List::stream)
                            .toList();
                    if (index < 0 || index >= pagedMaps.size()) {
                        return;
                    }

                    Map map = pagedMaps.get(index);
                    eventEditorService.selectMapForDraft(session, map.plainName(), map.file.name(), map.author(), Vars.state.rules.mode().name());
                    menu.edit(uuid);
                }
            }
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
