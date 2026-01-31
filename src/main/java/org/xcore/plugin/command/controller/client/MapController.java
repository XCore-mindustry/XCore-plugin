package org.xcore.plugin.command.controller.client;

import arc.Core;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.ui.Menus;
import org.xcore.plugin.command.core.annotation.AdminOnly;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.GameStateService;
import org.xcore.plugin.database.DatabaseService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.vote.VoteRtvFactory;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;

import static com.ospx.flubundle.Bundle.args;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class MapController {

    private int genericMenuId;

    private final ObjectMap<String, MenuSession> playerSessionContext = new ObjectMap<>();

    private final DatabaseService database;
    private final GlobalConfig globalConfig;
    private final BundleService bundle;
    private final VoteService voteService;
    private final VoteRtvFactory voteRtvFactory;
    private final MapService mapService;
    private final GameStateService gameStateService;


    private static class MenuSession {
        final List<Runnable> actions = new ArrayList<>();

        String add(String buttonName, Runnable action) {
            actions.add(action);
            return buttonName;
        }
    }

    @Inject
    public MapController(DatabaseService database, GlobalConfig globalConfig, BundleService bundle,
                         VoteService voteService, VoteRtvFactory voteRtvFactory, MapService mapService,
                         GameStateService gameStateService) {
        this.database = database;
        this.globalConfig = globalConfig;
        this.bundle = bundle;
        this.voteService = voteService;
        this.voteRtvFactory = voteRtvFactory;
        this.mapService = mapService;
        this.gameStateService = gameStateService;
    }

//    public void initMenu() {
//        this.mapMenuId = Menus.registerMenu((player, option) -> {
//            MapData map = playerMapContext.get(player.uuid());
//
//            if (map == null) return;
//
//            if (player.admin) {
//                switch (option) {
//                case 0 -> handleReputation(player, true, map);
//                case 1 -> handleReputation(player, false, map);
//                case 2 -> startRtvSession(player, mapService.findMap(map.name), true, false);
//                case 3 -> startRtvSession(player, mapService.findMap(map.name), true, true);
//                case 4 -> {} // close
//                case 5 -> {} // handleMaps
//                }
//            } else {
//                switch (option) {
//                case 0 -> handleReputation(player, true, map);
//                case 1 -> handleReputation(player, false, map);
//                case 2 -> startRtvSession(player, mapService.findMap(map.name), true, false);
//                case 3 -> {} // close
//                case 4 -> {} // handleMaps
//                }
//            }
//
//        });
//
//        this.mapsMenuId = Menus.registerMenu((player, option) -> {
//            switch (option) {
//                case 0 -> Call.openURI(player.con, globalConfig.discordUrl);
//            }
//        });
//    }

    public void initMenu() {
        this.genericMenuId = Menus.registerMenu((player, option) -> {
            MenuSession session = playerSessionContext.get(player.uuid());
            if (session != null && option >= 0 && option < session.actions.size()) {
                session.actions.get(option).run();
            }
        });
    }

    @Command(name = "map", params = "[map-name]", aliases = {"map-stats", "map-statistics"})
    public void map(ClientContext ctx) {
        Map map = ctx.args().length > 0 ? mapService.findMap(ctx.arg(0)) : Vars.state.map;
        if (map == null) {
            ctx.send("error-map-not-found", args());
            return;
        }

        MapData m = database.getMapDataRepository().find(map.plainName(), map.author(), Vars.state.rules.mode().name());
        handleMap(ctx.player(), m);
    }

    @Command(name = "maps", params = "[page]", aliases = {"map-ui"})
    public void maps(ClientContext ctx) {
        handleMaps(ctx.player(), ctx.argInt(0, 1));
    }

    @Command(name = "maps-text", params = "[page]")
    public void mapsText(ClientContext ctx) {
        Seq<Map> list = mapService.getAvailableMaps();
        int lines = globalConfig.MapsPageLines;
        int pageCount = Mathf.ceil((float) list.size / lines);
        int page = ctx.argInt(0, 1);

        if (page < 1 || page > pageCount) {
            ctx.send("error-page-between", args("pageCount", pageCount));
            return;
        }

        StringBuilder builder = new StringBuilder(ctx.format("commands-maps-text-start-content", args(
                "mapName", Vars.state.map.name(),
                "page", page,
                "pageCount", pageCount
        )));

        long now = System.currentTimeMillis();
        for (int i = (page - 1) * lines; i < Math.min(page * lines, list.size); i++) {
            Map map = list.get(i);
            MapData m = database.getMapDataRepository().find(map.plainName(), map.author(), Vars.state.rules.mode().name());

            String last = m.playedTimes == 0
                    ? ctx.format("never", args())
                    : (now - m.lastPlayedTime) / 60000 + "m";

            builder.append(ctx.format("commands-maps-text-content", args(
                    "index", i + 1,
                    "mapName", m.name,
                    "mapAuthor", m.author,
                    "mapWidth", map.width,
                    "mapHeight", map.height,
                    "mapReputation", m.reputation,
                    "mapLastPlayed", last
            )));
        }
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
        if (voteService.isVoting() && !forced) {
            bundle.send("error-vote-in-progress", args());
            return;
        } else if (voteService.isVoting() && forced) {
            voteService.endVote();
        }

        if (target == null) {
            bundle.send("error-map-not-found", args());
            return;
        }

        if (forced) {
            Timer.schedule(() -> gameStateService.reloadWorld(() -> {
                Gamemode mode = Gamemode.valueOf(Core.settings.getString("lastServerMode"));
                Vars.world.loadMap(target, target.applyRules(mode));
            }), globalConfig.mapSwitchDelaySeconds);

            bundle.send("commands-artv-map-skipped", args(
                    "nickname", player.coloredName()
            ));
        } else {
            var vote = voteRtvFactory.create(target, isManual);
            voteService.startVote(vote);
            vote.vote(player, 1);
        }

    }

    private void handleMap(Player player, MapData m) {
        Map map = mapService.findMap(m.name);

        String last = m.playedTimes == 0
                ? bundle.format(bundle.locale(player), "never", args())
                : (System.currentTimeMillis() - m.lastPlayedTime) / 60000 + "m";

        String menuTitle = bundle.format(bundle.locale(player),"commands-map-title", args());
        String menuContent = bundle.format(bundle.locale(player), "commands-map-content", args(
                "mapName", m.name,
                "mapAuthor", m.author,
                "mapDescription", map.description(),
                "mapWidth", map.width,
                "mapHeight", map.height,
                "mapReputation", m.reputation,
                "mapPopularity", String.format("%.1f", m.popularity),
                "mapInterest", String.format("%.1f", m.interest),
                "mapPlayedTimes", m.playedTimes,
                "mapPlayedTimesYear", m.playedTimesYear,
                "mapLastPlayed", last,
                "mapMin", m.minimumGameTime / 60000,
                "mapAvg", m.averageGameTime / 60000,
                "mapMax", m.maximumGameTime / 60000
            ));

        PlayerData pData = database.getCached(player.uuid());
        Boolean currentVote = pData.mapVotes.get(m.name);

        String likeButtonText = Boolean.TRUE.equals(currentVote)
                ? bundle.format(bundle.locale(player), "map-vote-like-selected", args())
                : bundle.format(bundle.locale(player), "map-vote-like", args());
        String dislikeButtonText = Boolean.FALSE.equals(currentVote)
                ? bundle.format(bundle.locale(player), "map-vote-dislike-selected", args())
                : bundle.format(bundle.locale(player), "map-vote-dislike", args());

        MenuSession session = new MenuSession();
        List<List<String>> rows = new ArrayList<>();

        List<String> row1 = new ArrayList<>();
        row1.add(session.add(likeButtonText, () -> handleReputation(player, true, m)));
        row1.add(session.add(dislikeButtonText, () -> handleReputation(player, false, m)));
        rows.add(row1);

        List<String> row2 = new ArrayList<>();
        row2.add(session.add(bundle.format(bundle.locale(player), "map-rtv", args()),
                () -> startRtvSession(player, map, true, false)));
        if (player.admin) {
             row2.add(session.add(bundle.format(bundle.locale(player), "map-artv", args()),
                     () -> startRtvSession(player, map, true, true)));
        }
        rows.add(row2);

        List<String> row3 = new ArrayList<>();
        row3.add(session.add(bundle.format(bundle.locale(player), "close", args()), () -> {}));
        row3.add(session.add(bundle.format(bundle.locale(player), "map-maps", args()),
                 () -> handleMaps(player, 1)));
        rows.add(row3);

        playerSessionContext.put(player.uuid(), session);
        Call.menu(player.con, genericMenuId, menuTitle, menuContent, convertListToArray(rows));
    }

    private void handleMaps(Player player, int page) {
        Seq<Map> list = mapService.getAvailableMaps();
        int lines = globalConfig.MapsPageLines;
        int pageCount = Mathf.ceil((float) list.size / lines);

        if (page < 1) page = 1;
        if (page > pageCount) page = pageCount;

        String menuTitle = bundle.format(bundle.locale(player), "commands-maps-title", args());
        String menuContent = bundle.format(bundle.locale(player), "commands-maps-content", args("page", page, "pageCount", pageCount));

        String previousButtonText = bundle.format(bundle.locale(player), "previous", args());
        String nextButtonText = bundle.format(bundle.locale(player), "next", args());
        String closeButtonText = bundle.format(bundle.locale(player), "close", args());

        MenuSession session = new MenuSession();
        List<List<String>> rows = new ArrayList<>();

        List<String> navRow = new ArrayList<>();
        if (page > 1) {
            final int prevPage = page - 1;
            navRow.add(session.add(bundle.format(bundle.locale(player), previousButtonText, args()), () -> handleMaps(player, prevPage)));
        }
        if (page < pageCount) {
            final int nextPage = page + 1;
            navRow.add(session.add(bundle.format(bundle.locale(player), nextButtonText, args()), () -> handleMaps(player, nextPage)));
        }
        if (!navRow.isEmpty()) rows.add(navRow);

        for (int i = (page - 1) * lines; i < Math.min(page * lines, list.size); i++) {
            Map map = list.get(i);
            List<String> mapRow = new ArrayList<>();
            mapRow.add(session.add(map.name(), () -> {
                MapData data = database.getMapDataRepository().find(map.plainName(), map.author(), Vars.state.rules.mode().name());
                handleMap(player, data);
            }));
            rows.add(mapRow);
        }

        List<String> closeRow = new ArrayList<>();
        closeRow.add(session.add(bundle.format(bundle.locale(player), closeButtonText, args()), () -> {}));
        rows.add(closeRow);

        playerSessionContext.put(player.uuid(), session);
        Call.menu(player.con, genericMenuId, menuTitle, menuContent, convertListToArray(rows));
    }

    private void handleReputation(Player player, boolean like) {
        Map map = Vars.state.map;
        if (map == null) return;
        MapData m = database.getMapDataRepository().find(map.plainName(), map.author(), Vars.state.rules.mode().name());
        handleReputation(player, like, m);
    }

    private void handleReputation(Player player, boolean like, MapData m) {
        PlayerData p = database.getCached(player.uuid());
        String id = String.valueOf(m.id);
        Boolean prev = p.mapVotes.get(id);

        if (Boolean.valueOf(like).equals(prev)) {
            bundle.send("error-already-voted", args());
            return;
        }

        int mod = (prev == null) ? 1 : 2;
        if (like) {
            m.reputation += mod;
            m.popularity += (mod * 2.0);
            bundle.send("commands-like-success", args());
        } else {
            m.reputation -= mod;
            m.popularity -= (mod * 2.0);
            bundle.send("commands-dislike-success", args());
        }

        p.mapVotes.put(id, like);

        database.getPlayerDataRepository().save(p);
        database.getMapDataRepository().save(m);
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
