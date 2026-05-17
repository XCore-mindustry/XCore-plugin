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
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;

final class MapFlows {

    static final String ROUTE_MAP = "map.details";
    static final String ROUTE_MAPS = "map.maps";

    private MapFlows() {
    }

    static final class MapsFlow extends BaseMenuFlow<MapsState> {
        private final MapMenu menu;
        private final MapDataRepository mapDataRepository;
        private final MapService mapService;

        MapsFlow(MapMenu menu, MapDataRepository mapDataRepository, MapService mapService) {
            super(ROUTE_MAPS, MapsState.class);
            this.menu = menu;
            this.mapDataRepository = mapDataRepository;
            this.mapService = mapService;

            action("previous", ctx -> {
                Session session = ctx.session();
                int page = ctx.route().intParam("page", 1);
                session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_MAPS).withParam("page", String.valueOf(page - 1)));
            });
            action("next", ctx -> {
                Session session = ctx.session();
                int page = ctx.route().intParam("page", 1);
                session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_MAPS).withParam("page", String.valueOf(page + 1)));
            });
            actionPrefix("map:", (ctx, indexStr) -> {
                int index = Integer.parseInt(indexStr);
                Session session = ctx.session();
                int page = ctx.route().intParam("page", 1);
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
                        ctx.openRoute(MenuRoute.of(ROUTE_MAP).withParam("mapId", data.id.toHexString()));
                    }
                }
            });
        }

        @Override
        public MapsState createState(Session session, MenuRoute route, MapsState currentState) {
            return currentState == null ? new MapsState() : currentState;
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
                        new MenuGrid().row(MenuButton.of(session.locale().t("close"), "close")).build()
                );
            }

            int validPage = pagination.clampPage(page);
            List<Map> pageMaps = SeqStream.of(availableMaps)
                    .gather(CustomGatherers.page(menu.globalConfig.mapsPerPage, validPage))
                    .flatMap(List::stream)
                    .toList();

            var local = context.locale();
            var grid = new MenuGrid();

            for (int i = 0; i < pageMaps.size(); i++) {
                Map map = pageMaps.get(i);
                grid.row(MenuButton.of(formatMapButton(local, map), "map:" + i));
            }

            List<MenuButton> paginationRow = new ArrayList<>();
            if (validPage > 1) {
                paginationRow.add(MenuButton.of(local.t("previous"), "previous"));
            }
            if (validPage < pagination.totalPages()) {
                paginationRow.add(MenuButton.of(local.t("next"), "next"));
            }
            if (!paginationRow.isEmpty()) {
                grid.row(paginationRow.toArray(new MenuButton[0]));
            }

            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("commands-maps-title"),
                    local.t("commands-maps-content", args(
                            "current", state.map == null ? local.t("never") : state.map.name(),
                            "page", validPage,
                            "total", pagination.totalPages()
                    )),
                    grid.build()
            );
        }

        private String formatMapButton(org.xcore.plugin.localization.Localization local, Map map) {
            boolean current = state.map != null && Objects.equals(mapIdentity(map), mapIdentity(state.map));
            String name = map.name();
            return current ? local.t("commands-maps-current-row", args("name", name)) : name;
        }

        private String mapIdentity(Map map) {
            if (map == null) {
                return "";
            }
            return map.file == null ? map.name() : map.file.name();
        }
    }

    static final class MapFlow extends BaseMenuFlow<MapState> {
        private final MapMenu menu;
        private final Config config;
        private final EventDataRepository eventDataRepository;
        private final MapService mapService;

        MapFlow(MapMenu menu, Config config, EventDataRepository eventDataRepository, MapService mapService) {
            super(ROUTE_MAP, MapState.class);
            this.menu = menu;
            this.config = config;
            this.eventDataRepository = eventDataRepository;
            this.mapService = mapService;

            action("like", ctx -> {
                MapData mapData = menu.resolveMap(ctx.state().mapId);
                if (mapData != null) {
                    mapService.handleReputation(ctx.session().player, true, mapData);
                    ctx.render();
                }
            });
            action("dislike", ctx -> {
                MapData mapData = menu.resolveMap(ctx.state().mapId);
                if (mapData != null) {
                    mapService.handleReputation(ctx.session().player, false, mapData);
                    ctx.render();
                }
            });
            action("rtv", ctx -> {
                MapData mapData = menu.resolveMap(ctx.state().mapId);
                if (mapData != null) {
                    Map mindustryMap = mapService.findPersistedMap(mapData);
                    mapService.startRtvSession(ctx.session().player, mindustryMap, true, false);
                }
            });
            action("admin-rtv", ctx -> {
                MapData mapData = menu.resolveMap(ctx.state().mapId);
                if (mapData != null) {
                    Map mindustryMap = mapService.findPersistedMap(mapData);
                    mapService.startRtvSession(ctx.session().player, mindustryMap, true, true);
                }
            });
            action("maps", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_MAPS).withParam("page", "1")));
            action("create-start", ctx -> {
                MapData mapData = menu.resolveMap(ctx.state().mapId);
                if (mapData != null) {
                    menu.eventMenu.get().createStart(menu.getUuid(ctx.session()), mapData);
                }
            });
        }

        @Override
        public MapState createState(Session session, MenuRoute route, MapState currentState) {
            MapState state = currentState == null ? new MapState() : currentState;
            String mapId = route.param("mapId");
            state.mapId = mapId == null ? "" : mapId;
            return state;
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
                    : menu.formatPlayTime((int) ((System.currentTimeMillis() - mapData.lastPlayedTime) / 60000), session.locale());
            String desc = (mindustryMap == null || mindustryMap.description().isEmpty())
                    ? session.locale().t("no-description")
                    : mindustryMap.description();
            String minDuration = menu.formatPlayTime((int) (mapData.minimumGameTime / 60000), session.locale());
            String averageDuration = menu.formatPlayTime((int) (mapData.averageGameTime / 60000), session.locale());
            String maxDuration = menu.formatPlayTime((int) (mapData.maximumGameTime / 60000), session.locale());
            Boolean currentVote = session.data.mapVotes.get(mapData.id.toString());
            String likeTxt = Boolean.TRUE.equals(currentVote) ? session.locale().t("map-vote-like-selected") : session.locale().t("map-vote-like");
            String dislikeTxt = Boolean.FALSE.equals(currentVote) ? session.locale().t("map-vote-dislike-selected") : session.locale().t("map-vote-dislike");

            var grid = new MenuGrid();
            grid.row(
                    MenuButton.of(likeTxt, "like"),
                    MenuButton.of(dislikeTxt, "dislike")
            );

            EventData activeEvent = eventDataRepository.findActive().orElse(null);
            boolean rtvEnabled = !config.isFeatureDisabled(Feature.RTV);
            if (rtvEnabled && (!config.isEvent() || (activeEvent == null || !activeEvent.isActive) || activeEvent.map.equals(mapData.id))) {
                List<MenuButton> rtvRow = new ArrayList<>();
                rtvRow.add(MenuButton.of(session.locale().t("map-rtv"), "rtv"));
                if (session.player.admin) {
                    rtvRow.add(MenuButton.of(session.locale().t("map-artv"), "admin-rtv"));
                }
                grid.row(rtvRow.toArray(new MenuButton[0]));
            }

            grid.row(MenuButton.of(session.locale().t("map-maps-back"), "maps"));

            if (config.isEvent()) {
                grid.row(MenuButton.of(session.locale().t("event-menu-create-start-map"), "create-start"));
            }

            grid.defaultNavigation(session, session.locale());

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
                            "min", minDuration, "avg", averageDuration, "max", maxDuration
                    )),
                    grid.build()
            );
        }
    }

    static final class MapsState {
    }

    static final class MapState {
        String mapId = "";
    }
}
