package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import mindustry.gen.Groups;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class MapMenu extends Menu {

    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";

    private final MapDataRepository mapDataRepository;
    private final MapService mapService;
    final Provider<EventMenu> eventMenu;
    private final MenuService menuService;
    private final TomlXcoreConfig config;
    private final EventDataRepository eventDataRepository;
    private final org.xcore.plugin.service.map.MapPreviewService mapPreviewService;
    private final org.xcore.plugin.service.map.MapVoteObserverService mapVoteObserverService;

    @Inject
    public MapMenu(TomlXcoreConfig config, TomlSecretsConfig secretsConfig, SessionService sessionService,
                   MapDataRepository mapDataRepository, EventDataRepository eventDataRepository,
                   MapService mapService, Provider<EventMenu> eventMenu, MenuService menuService,
                   org.xcore.plugin.service.map.MapPreviewService mapPreviewService,
                   org.xcore.plugin.service.map.MapVoteObserverService mapVoteObserverService) {
        super(secretsConfig, sessionService);
        this.config = config;
        this.mapDataRepository = mapDataRepository;
        this.eventDataRepository = eventDataRepository;
        this.mapService = mapService;
        this.eventMenu = eventMenu;
        this.menuService = menuService;
        this.mapPreviewService = mapPreviewService;
        this.mapVoteObserverService = mapVoteObserverService;
    }

    public MapMenu(TomlXcoreConfig config, TomlSecretsConfig secretsConfig, SessionService sessionService,
                   MapDataRepository mapDataRepository, EventDataRepository eventDataRepository,
                   MapService mapService, Provider<EventMenu> eventMenu, MenuService menuService) {
        this(config, secretsConfig, sessionService, mapDataRepository, eventDataRepository, mapService, eventMenu, menuService, null, null);
    }

    @PostConstruct
    public void init() {
        menuService.registerRoute(new MapFlows.MapFlow(this, config, eventDataRepository, mapService));
        menuService.registerRoute(new MapFlows.MapsFlow(this, mapDataRepository, mapService));
    }

    public void map(String uuid, MapData m) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        if (session.menuService != null && session.menuService.hasMenuBuilder() && session.player != null && session.player.con != null) {
            openMapDetailsUi(session, m);
            return;
        }

        String mapId = m != null && m.id != null ? m.id.toHexString() : "";
        session.menuService.renderRoute(session, MenuRoute.of(MapFlows.ROUTE_MAP).withParam("mapId", mapId));
    }

    public void maps(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        var availableMaps = mapService.getAvailableMaps();
        if (availableMaps.isEmpty()) {
            session.locale().send("empty");
            return;
        }

        if (session.menuService != null && session.menuService.hasMenuBuilder() && session.player != null && session.player.con != null) {
            openMapBrowserUi(session, page);
            return;
        }

        session.menuService.renderRoute(session, MenuRoute.of(MapFlows.ROUTE_MAPS).withParam("page", String.valueOf(page)));
    }

    public void openMapBrowserUi(Session session, int page) {
        if (session == null || session.player == null) return;
        session.clear();

        var controller = new org.xcore.plugin.ui.menu.map.MapUiController(mapService, mapDataRepository, mapPreviewService, mapVoteObserverService, session);
        var initialModel = controller.createInitialBrowserModel(session, page);
        menuService.openUi(session, controller, initialModel);
    }

    public void openMapDetailsUi(Session session, MapData m) {
        if (session == null || session.player == null) return;
        session.clear();

        var controller = new org.xcore.plugin.ui.menu.map.MapUiController(mapService, mapDataRepository, mapPreviewService, mapVoteObserverService, session);
        var initialModel = controller.createInitialDetailsModel(session, m);
        menuService.openUi(session, controller, initialModel);
        if (mapVoteObserverService != null) {
            mapVoteObserverService.registerViewing(session.player.uuid(), initialModel.selectedMapId());
        }
        controller.requestPreviewAsync(session, initialModel.selectedMapId());
    }

    MapData resolveMap(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return null;
        }
        try {
            return mapDataRepository.findById(new org.bson.types.ObjectId(mapId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    MenuScreen mapNotFoundScreen(Session session) {
        List<MenuButton> navigation = new ArrayList<>();
        if (session.canGoBack()) {
            navigation.add(MenuButton.of(session.locale().t("back"), ACTION_BACK));
        }
        navigation.add(MenuButton.of(session.locale().t("close"), ACTION_CLOSE));
        return MenuScreen.normal(
                session.locale().t("commands-map-title"),
                session.locale().t("error-map-not-found"),
                List.of(navigation)
        );
    }

    public void showGameOverMenu(MapData current, MapData next, Team winner) {
        String nextName = next != null ? next.name : "Unknown";
        String nextAuthor = next != null ? next.author : "Unknown";

        Groups.player.each(player -> {
            Session session = sessionService.get(player.uuid());
            if (session == null || session.data == null) return;
            session.clear();

            Boolean currentVote = session.data.mapVotes.get(current.id.toString());
            String likeButtonText = Boolean.TRUE.equals(currentVote)
                    ? session.locale().format("map-vote-like-selected", args())
                    : session.locale().format("map-vote-like", args());
            String dislikeButtonText = Boolean.FALSE.equals(currentVote)
                    ? session.locale().format("map-vote-dislike-selected", args())
                    : session.locale().format("map-vote-dislike", args());

            session.builder().title("map-vote-title")
                    .content("map-vote-content", args(
                            "mapName", nextName,
                            "author", nextAuthor,
                            "seconds", 10
                    ))
                    .addButtonText(likeButtonText, () -> mapService.handleReputation(player, true, current))
                    .addButtonText(dislikeButtonText, () -> mapService.handleReputation(player, false, current))
                    .end()
                    .addButtonKey("current-map", () -> {
                        map(player.uuid(), current);
                    })
                    .addButtonKey("next-map", () -> {
                        map(player.uuid(), next);
                    })
                    .addNavigationRow().show();
        });
    }
}
