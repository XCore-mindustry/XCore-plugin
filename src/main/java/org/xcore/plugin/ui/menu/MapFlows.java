package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import mindustry.maps.Map;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.common.SeqStream;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.enums.Feature;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;

final class MapFlows {

    static final String ROUTE_MAP = "map.details";
    static final String ROUTE_MAPS = "map.maps";

    private static final String ACTION_LIKE = "like";
    private static final String ACTION_DISLIKE = "dislike";
    private static final String ACTION_RTV = "rtv";
    private static final String ACTION_ADMIN_RTV = "admin-rtv";
    private static final String ACTION_MAPS = "maps";
    private static final String ACTION_CREATE_START = "create-start";
    private static final String ACTION_PREVIOUS = "previous";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_MAP_PREFIX = "map-";

    private MapFlows() {
    }

    static final class MapsFlow implements RoutedMenuFlow<MapsState> {
        private final MapMenu menu;
        private final MapDataRepository mapDataRepository;
        private final MapService mapService;

        MapsFlow(MapMenu menu, MapDataRepository mapDataRepository, MapService mapService) {
            this.menu = menu;
            this.mapDataRepository = mapDataRepository;
            this.mapService = mapService;
        }

        @Override
        public String routeId() {
            return ROUTE_MAPS;
        }

        @Override
        public MapsState createState(Session session, MenuRoute route, MapsState currentState) {
            return currentState == null ? new MapsState() : currentState;
        }

        @Override
        public Class<MapsState> stateType() {
            return MapsState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<MapsState> context) {
            Session session = context.session();
            int page = context.route().intParam("page", 1);
            Seq<Map> availableMaps = mapService.getAvailableMaps();
            var pagination = CustomGatherers.calculatePagination(availableMaps.size, menu.globalConfig.mapsPerPage);

            if (availableMaps.isEmpty()) {
                session.locale().send("empty");
                return MenuScreen.normal(
                        session.locale().t("commands-maps-title"),
                        "",
                        List.of(List.of(MenuButton.of(session.locale().t("close"), ACTION_CLOSE)))
                );
            }

            int validPage = pagination.clampPage(page);
            List<Map> pageMaps = SeqStream.of(availableMaps)
                    .gather(CustomGatherers.page(menu.globalConfig.mapsPerPage, validPage))
                    .flatMap(List::stream)
                    .toList();

            var local = context.locale();
            List<List<MenuButton>> rows = new ArrayList<>();

            List<MenuButton> paginationRow = new ArrayList<>();
            if (validPage > 1) {
                paginationRow.add(MenuButton.of(local.t("previous"), ACTION_PREVIOUS));
            }
            if (validPage < pagination.totalPages()) {
                paginationRow.add(MenuButton.of(local.t("next"), ACTION_NEXT));
            }
            if (!paginationRow.isEmpty()) {
                rows.add(paginationRow);
            }

            for (int i = 0; i < pageMaps.size(); i++) {
                Map map = pageMaps.get(i);
                rows.add(List.of(MenuButton.of(map.name(), ACTION_MAP_PREFIX + i)));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.hasHistory() || session.hasRouteHistory()) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("commands-maps-title"),
                    local.t("commands-maps-content", args("page", validPage, "total", pagination.totalPages())),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<MapsState> context, String actionId) {
            Session session = context.session();
            int page = context.route().intParam("page", 1);

            switch (actionId) {
                case ACTION_PREVIOUS -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_MAPS).withParam("page", String.valueOf(page - 1)));
                case ACTION_NEXT -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_MAPS).withParam("page", String.valueOf(page + 1)));
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                    if (actionId.startsWith(ACTION_MAP_PREFIX)) {
                        int index = Integer.parseInt(actionId.substring(ACTION_MAP_PREFIX.length()));
                        Seq<Map> availableMaps = mapService.getAvailableMaps();
                        var pagination = CustomGatherers.calculatePagination(availableMaps.size, menu.globalConfig.mapsPerPage);
                        int validPage = pagination.clampPage(page);
                        String gameMode = state.rules.mode().name();
                        List<Map> pageMaps = SeqStream.of(availableMaps)
                                .gather(CustomGatherers.page(menu.globalConfig.mapsPerPage, validPage))
                                .flatMap(List::stream)
                                .toList();
                        if (index >= 0 && index < pageMaps.size()) {
                            Map map = pageMaps.get(index);
                            MapData data = mapDataRepository.findOrCreate(map.plainName(), map.file.name(), map.author(), gameMode);
                            if (data != null && data.id != null) {
                                context.openRoute(MenuRoute.of(ROUTE_MAP).withParam("mapId", data.id.toHexString()));
                            }
                        }
                    }
                }
            }
        }
    }

    static final class MapFlow implements RoutedMenuFlow<MapState> {
        private final MapMenu menu;
        private final Config config;
        private final EventDataRepository eventDataRepository;
        private final MapService mapService;

        MapFlow(MapMenu menu, Config config, EventDataRepository eventDataRepository, MapService mapService) {
            this.menu = menu;
            this.config = config;
            this.eventDataRepository = eventDataRepository;
            this.mapService = mapService;
        }

        @Override
        public String routeId() {
            return ROUTE_MAP;
        }

        @Override
        public MapState createState(Session session, MenuRoute route, MapState currentState) {
            MapState state = currentState == null ? new MapState() : currentState;
            String mapId = route.param("mapId");
            state.mapId = mapId == null ? "" : mapId;
            return state;
        }

        @Override
        public Class<MapState> stateType() {
            return MapState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<MapState> context) {
            Session session = context.session();
            MapData mapData = menu.resolveMap(context.state().mapId);
            if (mapData == null) {
                return menu.mapNotFoundScreen(session);
            }

            Map mindustryMap = mapService.findPersistedMap(mapData);
            String last = mapData.playedTimes == 0
                    ? session.locale().t("never")
                    : (System.currentTimeMillis() - mapData.lastPlayedTime) / 60000 + "m";
            String desc = (mindustryMap == null || mindustryMap.description().isEmpty())
                    ? session.locale().t("no-description")
                    : mindustryMap.description();
            Boolean currentVote = session.data.mapVotes.get(mapData.id.toString());
            String likeTxt = Boolean.TRUE.equals(currentVote) ? session.locale().t("map-vote-like-selected") : session.locale().t("map-vote-like");
            String dislikeTxt = Boolean.FALSE.equals(currentVote) ? session.locale().t("map-vote-dislike-selected") : session.locale().t("map-vote-dislike");

            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    MenuButton.of(likeTxt, ACTION_LIKE),
                    MenuButton.of(dislikeTxt, ACTION_DISLIKE)
            ));

            EventData activeEvent = eventDataRepository.findActive().orElse(null);
            boolean rtvEnabled = !config.isFeatureDisabled(Feature.RTV);
            if (rtvEnabled && (!config.isEvent() || (activeEvent == null || !activeEvent.isActive) || activeEvent.map.equals(mapData.id))) {
                List<MenuButton> rtvRow = new ArrayList<>();
                rtvRow.add(MenuButton.of(session.locale().t("map-rtv"), ACTION_RTV));
                if (session.player.admin) {
                    rtvRow.add(MenuButton.of(session.locale().t("map-artv"), ACTION_ADMIN_RTV));
                }
                rows.add(rtvRow);
            }

            rows.add(List.of(MenuButton.of(session.locale().t("map-maps"), ACTION_MAPS)));

            if (config.isEvent()) {
                rows.add(List.of(MenuButton.of(session.locale().t("event-menu-create-start-map"), ACTION_CREATE_START)));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.hasHistory() || session.hasRouteHistory()) {
                navigation.add(MenuButton.of(session.locale().t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(session.locale().t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    session.locale().t("commands-map-title"),
                    session.locale().t("commands-map-content", args(
                            "name", mapData.name, "author", mapData.author, "description", desc,
                            "width", (mindustryMap == null) ? "?" : mindustryMap.width,
                            "height", (mindustryMap == null) ? "?" : mindustryMap.height,
                            "reputation", mapData.reputation, "popularity", String.format("%.1f", mapData.popularity),
                            "interest", String.format("%.1f", mapData.interest), "played", mapData.playedTimes,
                            "playedYear", mapData.playedTimesYear, "lastPlayed", last,
                            "like", mapData.like, "dislike", mapData.dislike,
                            "min", mapData.minimumGameTime / 60000, "avg", mapData.averageGameTime / 60000, "max", mapData.maximumGameTime / 60000
                    )),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<MapState> context, String actionId) {
            Session session = context.session();
            MapData mapData = menu.resolveMap(context.state().mapId);
            if (mapData == null) {
                switch (actionId) {
                    case ACTION_BACK -> context.goBack();
                    case ACTION_CLOSE -> context.close();
                    default -> {
                    }
                }
                return;
            }

            Map mindustryMap = mapService.findPersistedMap(mapData);
            String uuid = menu.getUuid(session);
            switch (actionId) {
                case ACTION_LIKE -> {
                    mapService.handleReputation(session.player, true, mapData);
                    context.render();
                }
                case ACTION_DISLIKE -> {
                    mapService.handleReputation(session.player, false, mapData);
                    context.render();
                }
                case ACTION_RTV -> mapService.startRtvSession(session.player, mindustryMap, true, false);
                case ACTION_ADMIN_RTV -> mapService.startRtvSession(session.player, mindustryMap, true, true);
                case ACTION_MAPS -> context.openRoute(MenuRoute.of(ROUTE_MAPS).withParam("page", "1"));
                case ACTION_CREATE_START -> menu.eventMenu.get().createStart(uuid, mapData);
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }
    }

    static final class MapsState {
    }

    static final class MapState {
        String mapId = "";
    }
}
