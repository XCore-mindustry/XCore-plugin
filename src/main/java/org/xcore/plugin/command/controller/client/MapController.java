package org.xcore.plugin.command.controller.client;

import arc.Core;
import arc.struct.Seq;
import arc.util.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.common.SeqStream;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.*;
import org.xcore.plugin.ui.MenuSession;
import org.xcore.plugin.vote.VoteRtv;
import org.xcore.plugin.vote.VoteRtvFactory;
import org.xcore.plugin.vote.VoteService;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;

@Singleton
public class MapController implements CloudClientController {

    private final EventDataRepository eventDataRepository;
    private final MapDataRepository mapDataRepository;
    private final PlayerDataRepository playerDataRepository;
    private final PlayerSessionService playerSessionService;
    private final Config config;
    private final GlobalConfig globalConfig;
    private final BundleService bundle;
    private final VoteService voteService;
    private final VoteRtvFactory voteRtvFactory;
    private final MapService mapService;
    private final GameStateService gameStateService;
    private final Provider<EventController> eventController;
    private final MenuService menuService;

    @Inject
    public MapController(
            EventDataRepository eventDataRepository,
            MapDataRepository mapDataRepository,
            PlayerDataRepository playerDataRepository,
            PlayerSessionService playerSessionService,
            Config config,
            GlobalConfig globalConfig,
            BundleService bundle,
            VoteService voteService,
            VoteRtvFactory voteRtvFactory,
            MapService mapService,
            Provider<EventController> eventController,
            GameStateService gameStateService,
            MenuService menuService
    ) {
        this.eventDataRepository = eventDataRepository;
        this.mapDataRepository = mapDataRepository;
        this.playerDataRepository = playerDataRepository;
        this.playerSessionService = playerSessionService;
        this.config = config;
        this.globalConfig = globalConfig;
        this.bundle = bundle;
        this.voteService = voteService;
        this.voteRtvFactory = voteRtvFactory;
        this.mapService = mapService;
        this.gameStateService = gameStateService;
        this.eventController = eventController;
        this.menuService = menuService;
    }

    @Command("map|map-stats|map-statistics")
    public void map(XCoreSender sender) {
        Map map = Vars.state.map;
        if (map == null) {
            sender.send("error-map-not-found", args());
            return;
        }

        MapData m = mapDataRepository.findOrCreate(
                map.plainName(),
                map.file.name(),
                map.author(),
                Vars.state.rules.mode().name()
        );

        handleMap(sender.player(), m);
    }

    @Command("map|map-stats|map-statistics <map>")
    public void map(
            XCoreSender sender,
            @Argument("map") Map map
    ) {
        if (map == null) {
            sender.send("error-map-not-found", args());
            return;
        }

        MapData m = mapDataRepository.findOrCreate(
                map.plainName(),
                map.file.name(),
                map.author(),
                Vars.state.rules.mode().name()
        );

        handleMap(sender.player(), m);
    }

    @Command("maps|map-ui [page]")
    public void maps(
            XCoreSender sender,
            @Argument("page") @Default("1") int page
    ) {
        handleMaps(sender.player(), page);
    }

    @Command("maps-text [page]")
    public void mapsText(
            XCoreSender sender,
            @Argument("page") @Default("1") int page
    ) {
        Seq<Map> maps = mapService.getAvailableMaps();
        var pagination = CustomGatherers.calculatePagination(maps.size, globalConfig.mapsPerPage);

        if (pagination.totalPages() == 0) {
            sender.send("empty", args());
            return;
        }

        if (!pagination.isValidPage(page)) {
            sender.send("error-page-between", args("totalPages", pagination.totalPages()));
            return;
        }

        StringBuilder builder = new StringBuilder(
                sender.format(
                        "commands-maps-text-start-content",
                        args(
                                "name", Vars.state.map.name(),
                                "page", page,
                                "total", pagination.totalPages()
                        )
                )
        );

        long now = System.currentTimeMillis();
        String gameMode = Vars.state.rules.mode().name();

        SeqStream.of(maps)
                .gather(CustomGatherers.indexedPage(globalConfig.mapsPerPage, page))
                .forEach(indexed -> {
                    Map map = indexed.value();
                    MapData mapData = mapDataRepository.findOrCreate(
                            map.plainName(),
                            map.file.name(),
                            map.author(),
                            gameMode
                    );

                    String lastPlayed = mapData.playedTimes == 0
                            ? sender.format("never", args())
                            : (now - mapData.lastPlayedTime) / 60000 + "m";

                    builder.append(
                            sender.format(
                                    "commands-maps-text-content",
                                    args(
                                            "index", indexed.index(),
                                            "name", mapData.name,
                                            "author", mapData.author,
                                            "width", map.width,
                                            "height", map.height,
                                            "reputation", mapData.reputation,
                                            "lastPlayed", lastPlayed
                                    )
                            )
                    );
                });

        sender.player().sendMessage(builder.toString());
    }

    @Command("rtv")
    public void rtv(XCoreSender sender) {
        Map target = Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);
        startRtvSession(sender.player(), target, false, false);
    }

    @Command("rtv <map>")
    public void rtv(XCoreSender sender,
                    @Argument("map") Map map) {
        startRtvSession(sender.player(), map, true, false);
    }

    @Permission("admin")
    @Command("artv")
    public void artv(XCoreSender sender) {
        Map map = Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);
        startRtvSession(sender.player(), map, false, true);
    }

    @Permission("admin")
    @Command("artv <map>")
    public void artv(XCoreSender sender,
                     @Argument("map") Map map) {
        startRtvSession(sender.player(), map, true, true);
    }

    @Command("like|+")
    public void like(XCoreSender sender) {
        handleReputation(sender.player(), true);
    }

    @Command("dislike|-")
    public void dislike(XCoreSender sender) {
        handleReputation(sender.player(), false);
    }

    private void startRtvSession(Player player, Map target, boolean isManual, boolean forced) {
        if (voteService.isVoting() && !(voteService.getCurrentSession() instanceof VoteRtv)) {
            bundle.send(player, "error-vote-in-progress", args());
            return;
        } else if (voteService.isVoting() && !forced) {
            bundle.send("error-vote-in-progress", args());
            return;
        } else if (voteService.isVoting() && forced) {
            voteService.endVote();
        }

        if (target == null) {
            bundle.send("error-map-not-found", args());
            return;
        }

        if (config.isEvent()) {
            EventData event = eventDataRepository.findActive().orElse(null);

            if (event != null && event.isActive) {
                MapData mapData = mapDataRepository.findOrCreate(target.name(), target.file.name(), target.author(), state.rules.mode().name());

                if (!event.map.equals(mapData.id)) {
                    bundle.send(player, "error-map-not-event", args());
                    return;
                }
            }
        }

        if (forced) {
            Timer.schedule(() -> gameStateService.reloadWorld(() -> {
                Gamemode mode = Gamemode.valueOf(Core.settings.getString("lastServerMode"));
                Vars.world.loadMap(target, target.applyRules(mode));
            }), globalConfig.mapSwitchDelaySeconds);

            bundle.send("commands-artv-map-skipped", args(
                    "name", target.name(),
                    "nickname", player.coloredName()
            ));
        } else {
            var vote = voteRtvFactory.create(target, isManual);
            voteService.startVote(vote);
            vote.vote(player, 1);
        }

    }

    public void handleMap(Player player, MapData m) {
        Map map = mapService.findMap(m.name);

        String last = m.playedTimes == 0
                ? bundle.format(bundle.locale(player), "never", args())
                : (System.currentTimeMillis() - m.lastPlayedTime) / 60000 + "m";

        String menuTitle = bundle.format(bundle.locale(player), "commands-map-title", args());
        String menuContent = bundle.format(bundle.locale(player), "commands-map-content", args(
                "name", m.name,
                "author", m.author,
                "description", (map == null || map.description().isEmpty()) ? bundle.format(bundle.locale(player), "no-description", args()) : map.description(),
                "width", (map == null) ? "" : map.width,
                "height", (map == null) ? "" : map.height,
                "reputation", m.reputation,
                "popularity", String.format("%.1f", m.popularity),
                "interest", String.format("%.1f", m.interest),
                "played", m.playedTimes,
                "playedYear", m.playedTimesYear,
                "lastPlayed", last,
                "like", m.like,
                "dislike", m.dislike,
                "min", m.minimumGameTime / 60000,
                "avg", m.averageGameTime / 60000,
                "max", m.maximumGameTime / 60000
        ));

        PlayerData pData = playerSessionService.get(player.uuid());
        Boolean currentVote = pData.mapVotes.get(m.id.toString());

        String likeButtonText = Boolean.TRUE.equals(currentVote)
                ? bundle.format(bundle.locale(player), "map-vote-like-selected", args())
                : bundle.format(bundle.locale(player), "map-vote-like", args());
        String dislikeButtonText = Boolean.FALSE.equals(currentVote)
                ? bundle.format(bundle.locale(player), "map-vote-dislike-selected", args())
                : bundle.format(bundle.locale(player), "map-vote-dislike", args());

        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();
        List<List<String>> rows = new ArrayList<>();

        List<String> row1 = new ArrayList<>();
        row1.add(session.add(likeButtonText, () -> handleReputation(player, true, m)));
        row1.add(session.add(dislikeButtonText, () -> handleReputation(player, false, m)));
        rows.add(row1);

        EventData event = eventDataRepository.findActive().orElse(null);
        if (!config.isEvent() || (event == null || !event.isActive) || event.map.equals(m.id)) {
            List<String> row2 = new ArrayList<>();
            row2.add(session.add(bundle.format(bundle.locale(player), "map-rtv", args()),
                    () -> startRtvSession(player, map, true, false)));
            if (player.admin) {
                row2.add(session.add(bundle.format(bundle.locale(player), "map-artv", args()),
                        () -> startRtvSession(player, map, true, true)));
            }
            rows.add(row2);
        }

        menuService.addNavigationRow(player, session, rows);

        List<String> row3 = new ArrayList<>();
        row3.add(session.add(bundle.format(bundle.locale(player), "map-maps", args()), () -> {
            session.clearHistory();
            handleMaps(player, 1);
        }));
        rows.add(row3);

        if (config.isEvent()) {
            List<String> row4 = new ArrayList<>();
            row4.add(session.add(bundle.format(bundle.locale(player), "event-menu-create-start-map", args()), () -> {
                session.pushHistory(() -> handleMap(player, m));
                eventController.get().handleCreateStart(player, m);
            }));
            rows.add(row4);
        }

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }

    public void handleMaps(Player player, int page) {
        Seq<Map> maps = mapService.getAvailableMaps();
        var pagination = CustomGatherers.calculatePagination(maps.size, globalConfig.mapsPerPage);

        if (pagination.totalPages() == 0) {
            bundle.send(player, "empty", args());
            return;
        }

        int validPage = pagination.clampPage(page);
        String menuTitle = bundle.format(bundle.locale(player), "commands-maps-title", args());
        String menuContent = bundle.format(bundle.locale(player), "commands-maps-content", args(
                "page", validPage,
                "total", pagination.totalPages()
        ));
        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();
        List<List<String>> rows = new ArrayList<>();

        List<String> navRow = new ArrayList<>();
        if (validPage > 1) {
            final int prevPage = validPage - 1;
            navRow.add(session.add(bundle.format(bundle.locale(player), "previous", args()),
                    () -> handleMaps(player, prevPage)
            ));
        }
        if (validPage < pagination.totalPages()) {
            final int nextPage = validPage + 1;
            navRow.add(session.add(bundle.format(bundle.locale(player), "next", args()),
                    () -> handleMaps(player, nextPage)
            ));
        }
        if (!navRow.isEmpty()) {
            rows.add(navRow);
        }
        String gameMode = Vars.state.rules.mode().name();

        SeqStream.of(maps)
                .gather(CustomGatherers.page(globalConfig.mapsPerPage, validPage))
                .flatMap(List::stream)
                .forEach(map -> rows.add(List.of(
                        session.add(map.name(), () -> {
                            session.pushHistory(() -> handleMaps(player, validPage));
                            MapData data = mapDataRepository.findOrCreate(map.plainName(), map.file.name(), map.author(), gameMode);
                            handleMap(player, data);
                        })
                )));

        menuService.addNavigationRow(player, session, rows);
        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }

    private void handleReputation(Player player, boolean like) {
        Map map = Vars.state.map;
        if (map == null) return;
        MapData m = mapDataRepository.findOrCreate(map.plainName(), map.file.name(), map.author(), Vars.state.rules.mode().name());
        handleReputation(player, like, m);
    }

    public void handleReputation(Player player, boolean like, MapData map) {
        PlayerData p = playerSessionService.get(player.uuid());
        Boolean prev = p.mapVotes.get(map.id.toString());

        if (Boolean.valueOf(like).equals(prev)) {
            bundle.send("error-already-voted", args());
            return;
        }

        int mod = (prev == null) ? 1 : 2;
        if (like) {
            map.reputation += mod;
            map.popularity += (mod * 2.0);
            map.like += 1;
            if (prev != null) {
                map.dislike -= 1;
                bundle.send("like-map-changed", args());
            } else {
                bundle.send("like-map-success", args());
            }
        } else {
            map.reputation -= mod;
            map.popularity -= (mod * 2.0);
            map.dislike += 1;
            if (prev != null) {
                map.like -= 1;
                bundle.send("dislike-map-changed", args());
            } else {
                bundle.send("dislike-map-success", args());
            }
        }

        p.mapVotes.put(map.id.toString(), like);

        playerDataRepository.save(p);
        mapDataRepository.save(map);
    }
}