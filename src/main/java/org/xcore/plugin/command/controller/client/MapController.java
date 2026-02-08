package org.xcore.plugin.command.controller.client;

import arc.Core;
import arc.struct.Seq;
import arc.util.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.xcore.plugin.command.core.ClientController;
import org.xcore.plugin.command.core.annotation.AdminOnly;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.common.SeqStream;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.GameStateService;
import org.xcore.plugin.service.MenuService;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.ui.MenuSession;
import org.xcore.plugin.vote.VoteRtv;
import org.xcore.plugin.vote.VoteRtvFactory;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class MapController implements ClientController {

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
    private final MenuService menuService;

    @Inject
    public MapController(EventDataRepository eventDataRepository, MapDataRepository mapDataRepository,
                         PlayerDataRepository playerDataRepository,
                         PlayerSessionService playerSessionService, Config config,
                         GlobalConfig globalConfig,
                         BundleService bundle,
                         VoteService voteService,
                         VoteRtvFactory voteRtvFactory,
                         MapService mapService,
                         GameStateService gameStateService, MenuService menuService) {
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
        this.menuService = menuService;
    }

    @Override
    public int priority() {
        return 80;
    }

    @Command(name = "map", params = "[map-name]", aliases = {"map-stats", "map-statistics"})
    public void map(ClientContext ctx) {
        Map map = ctx.args().length > 0 ? mapService.findMap(ctx.arg(0)) : Vars.state.map;
        if (map == null) {
            ctx.send("error-map-not-found", args());
            return;
        }

        MapData m = mapDataRepository.findOrCreate(map.plainName(), map.file.name(), map.author(), Vars.state.rules.mode().name());
        handleMap(ctx.player(), m);
    }

    @Command(name = "maps", params = "[page]", aliases = {"map-ui"})
    public void maps(ClientContext ctx) {
        handleMaps(ctx.player(), ctx.argInt(0, 1));
    }

    @Command(name = "maps-text", params = "[page]")
    public void mapsText(ClientContext ctx) {
        Seq<Map> maps = mapService.getAvailableMaps();
        var pagination = CustomGatherers.calculatePagination(maps.size, globalConfig.mapsPerPage);

        if (pagination.totalPages() == 0) {
            ctx.send("empty", args());
            return;
        }

        int requestedPage = ctx.argInt(0, 1);

        if (!pagination.isValidPage(requestedPage)) {
            ctx.send("error-page-between", args("totalPages", pagination.totalPages()));
            return;
        }
        var builder = new StringBuilder(ctx.format("commands-maps-text-start-content", args(
                "name", Vars.state.map.name(),
                "page", requestedPage,
                "total", pagination.totalPages()
        )));

        long now = System.currentTimeMillis();
        String gameMode = Vars.state.rules.mode().name();

        SeqStream.of(maps)
                .gather(CustomGatherers.indexedPage(globalConfig.mapsPerPage, requestedPage))
                .forEach(indexed -> {
                    Map map = indexed.value();
                    MapData mapData = mapDataRepository
                            .findOrCreate(map.plainName(), map.file.name(), map.author(), gameMode);
                    String lastPlayed = mapData.playedTimes == 0
                            ? ctx.format("never", args())
                            : (now - mapData.lastPlayedTime) / 60000 + "m";
                    builder.append(ctx.format("commands-maps-text-content", args(
                            "index", indexed.index(),
                            "name", mapData.name,
                            "author", mapData.author,
                            "width", map.width,
                            "height", map.height,
                            "reputation", mapData.reputation,
                            "lastPlayed", lastPlayed
                    )));
                });
        ctx.player().sendMessage(builder.toString());
    }


    @Command(name = "rtv", params = "[map...]")
    public void rtv(ClientContext ctx) {
        boolean isManual = ctx.args().length > 0;
        Map target = isManual
                ? mapService.findMap(ctx.arg(0))
                : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);
        startRtvSession(ctx.player(), target, isManual, false);
    }

    @AdminOnly
    @Command(name = "artv", params = "[map...]")
    public void adminRtv(ClientContext ctx) {
        Map map = ctx.args().length > 0
                ? mapService.findMap(ctx.arg(0))
                : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);

        startRtvSession(ctx.player(), map, false, true);
    }

    @Command(name = "like", aliases = {"+"})
    public void like(ClientContext ctx) {
        handleReputation(ctx.player(), true);
    }

    @Command(name = "dislike", aliases = {"-"})
    public void dislike(ClientContext ctx) {
        handleReputation(ctx.player(), false);
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

        String menuTitle = bundle.format(bundle.locale(player),"commands-map-title", args());
        String menuContent = bundle.format(bundle.locale(player), "commands-map-content", args(
                "name", m.name,
                "author", m.author,
                "description", (map == null || map.description().isEmpty()) ? bundle.format(bundle.locale(player),"no-description", args()) : map.description(),
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

        List<String> row3 = new ArrayList<>();
        row3.add(session.add(bundle.format(bundle.locale(player), "close", args()), () -> {}));
        row3.add(session.add(bundle.format(bundle.locale(player), "map-maps", args()),
                 () -> handleMaps(player, 1)));
        rows.add(row3);

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, convertListToArray(rows));
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
            navRow.add(session.add(
                    bundle.format(bundle.locale(player), "previous", args()),
                    () -> handleMaps(player, prevPage)
            ));
        }
        if (validPage < pagination.totalPages()) {
            final int nextPage = validPage + 1;
            navRow.add(session.add(
                    bundle.format(bundle.locale(player), "next", args()),
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
                            MapData data = mapDataRepository.findOrCreate(map.plainName(), map.file.name(), map.author(), gameMode);
                            handleMap(player, data);
                        })
                )));

        rows.add(List.of(
                session.add(bundle.format(bundle.locale(player), "close", args()), () -> {})
        ));
        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, convertListToArray(rows));
    }

    private void handleReputation(Player player, boolean like) {
        Map map = Vars.state.map;
        if (map == null) return;
        MapData m = mapDataRepository.findOrCreate(map.plainName(), map.file.name(), map.author(), Vars.state.rules.mode().name());
        handleReputation(player, like, m);
    }

    private void handleReputation(Player player, boolean like, MapData map) {
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

    private String[][] convertListToArray(List<List<String>> rows) {
        String[][] result = new String[rows.size()][];

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);

            result[i] = row.toArray(new String[0]);
        }

        return result;
    }
}
