package org.xcore.plugin.gamemode.hexed;

import arc.Events;
import arc.func.Func;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import com.ospx.flubundle.Bundle;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.*;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Unitc;
import mindustry.maps.MapException;
import mindustry.net.Packets;
import mindustry.net.WorldReloader;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.enums.FinishReason;
import org.xcore.plugin.service.LeaderboardService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.service.TopMenuCacheService;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;
import static mindustry.Vars.world;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class MiniHexedService {
    public final ObjectMap<String, HexMember> members = new ObjectMap<>();
    public final ObjectMap<HexedRanks.HexedRank, Seq<String>> rankings = new ObjectMap<>();
    public Schematic startBase;
    private int greenCores = 0;

    private static int winScore = 2400;

    private final Config config;
    private final SessionService sessionService;
    private final PlayerDataRepository playerDataRepository;
    private final NetworkService network;
    private final Bundle bundle;
    private final LeaderboardService leaderboardService;
    private final PlayerDisplayService playerDisplayService;
    private final GameDataService gameDataService;
    private final TopMenuCacheService topMenuCacheService;

    private static boolean gameover = false;

    public MiniHexedService(Config config,
                            SessionService sessionService,
                            PlayerDataRepository playerDataRepository,
                            NetworkService networkService,
                            Bundle bundle,
                            LeaderboardService leaderboardService,
                            PlayerDisplayService playerDisplayService,
                            GameDataService gameDataService,
                            TopMenuCacheService topMenuCacheService) {
        this.config = config;
        this.sessionService = sessionService;
        this.playerDataRepository = playerDataRepository;
        this.network = networkService;
        this.bundle = bundle;
        this.leaderboardService = leaderboardService;
        this.playerDisplayService = playerDisplayService;
        this.gameDataService = gameDataService;
        this.topMenuCacheService = topMenuCacheService;
    }

    @PostConstruct
    public void init() {
        if (!config.isMiniHexed()) return;

        leaderboardService.start((builder, player, locale) -> {
            var teams = Vars.state.teams.getActive().copy()
                    .select(t -> !t.players.isEmpty() && t.team != Team.derelict)
                    .sort(t -> t.cores.size)
                    .reverse();

            teams.truncate(10);
            builder.append(locale.format("leaderboard", args())).append("\n\n");
            for (int i = 0; i < teams.size; i++) {
                var team = teams.get(i);
                String nickname = team.players.isEmpty() ? "Unknown" : team.players.first().coloredName();

                builder.append(locale.format("hexed-leaderboard-content", args(
                        "index", i + 1,
                        "nickname", nickname,
                        "hexes", team.cores.size
                ))).append("\n");
            }
        });

        startBase = Schematics.readBase64("bXNjaAF4nDWQ3W6DMAxGv/wQUpDWV+gLcLPXmXaRQap2YhgFurYvv82ONSLlJLGPbYEWvYNf0lfGy0glny75cdr2VHb0U97Gcl33Ky0Awpw+8rzBvr336Eda11yGe5pndCvd+bzQlBFHWr7zkwqOZypjHtZCn3nc+cFNN0K/0ZzKsKYlsygdh+2SyoR4W2ZKUy7o07UM5yTOE8d72rl2fuylvsBPxDvwivpZ2QyvejZCFy387w+/NUbCXrMaRVCvVSUqDopOICfrOJcXV1TdqG5E94wWrmGwLjio1/0PZAMcC6blG2d6RhTBaqbVTCeZkctFA23rNOAlcKh9uIQXs8a9huVmPcPBWYaXORteFUEmaDQzaJfAcoVVVC+oF9QL6gX5Lx0jdppa5w1S7Q8n5z8n");
        Events.on(EventType.PlayEvent.class, event -> {
            applyRules();
            Timer.schedule(() -> {
                greenCores = Team.green.cores().size;
                info("Found @ green cores.", greenCores);
            }, 5);
        });
        Events.on(EventType.PlayerLeave.class, event -> {
            var member = members.get(event.player.uuid());
            if (member != null) {
                member.leave();
            }
        });
        Events.on(EventType.GameOverEvent.class, e -> winScore = 2400);
        Events.on(EventType.BlockDestroyEvent.class, event -> {
            var team = event.tile.team();
            var block = event.tile.block();
            if (block instanceof CoreBlock && !team.data().players.isEmpty() && team != Team.derelict && team.cores().size <= 1) {
                var player = team.data().players.first();

                sessionService.broadcast("hexed-eliminated", args("nickname", player.coloredName()));
                player.team(Team.derelict);
            }
        });
        Events.on(EventType.UnitCreateEvent.class, event -> members.values().forEach((member) -> member.handleUnit(event.unit)));
        Events.run(EventType.Trigger.update, () -> members.each((uuid, member) -> {
            var session = sessionService.get(member.uuid);
            if (session == null) return;

            var data = session.data;

            if (member.controlled() > 1 && data != null) {
                var ranked = rankings.get(data.hexedRank());

                if (ranked == null) {
                    rankings.put(data.hexedRank(), Seq.with(member.uuid));
                } else {
                    if (!ranked.contains(member.uuid)) ranked.add(member.uuid);
                }
            }

            if (member.controlled() >= greenCores && greenCores != 0 && !gameover && !Vars.state.gameOver) {
                endGame();
            }
        }));
        Timer.schedule(() -> {
            if (!Groups.player.isEmpty() && !Vars.state.gameOver && !gameover) {
                winScore -= 1;
            }
            int sec = winScore % 60;
            int min = (winScore / 60) % 60;

            Groups.player.each(p -> {
                var session = sessionService.get(p);
                if (session == null) return;
                var message = session.locale().format("hexed-popup", args(
                        "minutes", min,
                        "seconds", sec));
                Call.infoPopup(p.con(), message, 1, Align.bottom, 0, 0, 0, 0);
            });

            if (winScore < 1 && !gameover && !Vars.state.gameOver) {
                endGame();
            }
        }, 0f, 1);

        netServer.assigner = (player, players) -> {
            var member = new HexMember(player.uuid(), this);

            if (members.containsKey(player.uuid())) member = members.get(player.uuid());
            members.put(player.uuid(), member);
            return member.join();
        };

        info("MiniHexed loaded.");
    }

    private void applyRules() {
        UnitTypes.risso.flying = true;
        UnitTypes.minke.flying = true;
        UnitTypes.bryde.flying = true;
        UnitTypes.sei.flying = true;
        UnitTypes.omura.flying = true;
        UnitTypes.retusa.flying = true;
        UnitTypes.oxynoe.flying = true;
        UnitTypes.cyerce.flying = true;
        UnitTypes.aegires.flying = true;
        UnitTypes.navanax.flying = true;

        UnitTypes.crawler.flying = true;
        UnitTypes.atrax.flying = true;
        UnitTypes.spiroct.flying = true;

        Vars.state.rules.canGameOver = false;
        Vars.state.rules.waves = false;
        Vars.state.rules.pvp = true;
        Vars.state.rules.pvpAutoPause = false;
    }

    private void endGame() {
        winScore = 2400;
        gameover = true;
        var rankedTeams = Vars.state.teams.getActive().copy().select(t -> !t.players.isEmpty()).sort(t -> t.cores.size).reverse();
        Map<String, Integer> placements = buildPlacements(rankedTeams);
        var teams = rankedTeams.copy();
        teams.truncate(3);

        gameDataService.applyPlacements(placements);
        gameDataService.finishGame(teams.isEmpty() ? null : teams.first().team, FinishReason.NATURAL);

        if (!teams.isEmpty()) {
            var winnerTeam = teams.get(0);
            var player = winnerTeam.players.first();
            var winnerSession = sessionService.get(player.uuid());
            var data = winnerSession != null ? winnerSession.data : sessionService.getOrLoadFromDb(player.uuid());

            if (data != null) {
                var ranked = rankings.get(data.hexedRank());
                if (ranked != null && ranked.size > 1 ||
                        rankings.keys().toSeq().contains(r -> data.hexedRank().ordinal() < r.ordinal())) {
                    data.hexedPoints++;
                    if (data.hexedRank().checkNext(data.hexedPoints)) {
                        data.hexedRank(data.hexedRank().next);
                        data.hexedPoints = 0;
                        if (winnerSession != null) {
                            playerDisplayService.refresh(player, data);
                        }
                    }
                }
                if (playerDataRepository.updateHexedProgress(data.uuid, data.hexedRank, data.hexedPoints)) {
                    topMenuCacheService.invalidateAll();
                }
            }
        }

        Func<Localization, String> generateMessage = locale -> {
            StringBuilder builder = new StringBuilder();
            if (!teams.isEmpty()) {
                builder.append(locale.format("hexed-game-over-header", args())).append("\n");
                for (int i = 0; i < teams.size; i++) {
                    var team = teams.get(i);
                    var player = team.players.first();

                    builder.append(locale.format("hexed-game-over-winner-row", args(
                            "index", i + 1,
                            "name", player.coloredName(),
                            "cores", team.cores.size
                    ))).append("\n");
                }
            } else {
                builder.append(locale.format("hexed-game-over-no-winners", args()));
            }
            builder.append("\n").append(locale.format("hexed-game-over-restart", args()));
            return builder.toString();
        };

        Groups.player.each(p -> {
            var session = sessionService.get(p);
            if (session == null) return;
            Call.infoMessage(p.con, generateMessage.get(session.locale()));
        });

        String rawMessage = generateMessage.get(new Localization(bundle));
        network.post(new SocketEvents.ServerActionEvent(Strings.stripColors(rawMessage), config.server));

        Events.fire("hexed_world-reload");
        Timer.schedule(this::reloadMap, 10);
    }

    private Map<String, Integer> buildPlacements(Seq<Teams.TeamData> teams) {
        Map<String, Integer> placements = new HashMap<>();
        for (int i = 0; i < teams.size; i++) {
            var team = teams.get(i);
            int placement = i + 1;
            team.players.each(player -> placements.put(player.uuid(), placement));
        }
        return placements;
    }

    private void reloadMap() {
        try {
            var map = Vars.maps.getNextMap(Gamemode.pvp, Vars.state.map);
            var reloader = new WorldReloader();
            netServer.kickAll(Packets.KickReason.serverRestarting);
            reloader.begin();
            world.loadMap(map, map.applyRules(Vars.state.rules.mode()));
            Vars.state.rules = Vars.state.map.applyRules(Vars.state.rules.mode());
            applyRules();
            Vars.logic.play();
            members.each((uuid, member) -> member.cancelTasks());
            members.clear();
            rankings.clear();
            reloader.end();
            gameover = false;
        } catch (MapException e) {
            Log.err("@: @", e.map.name(), e.getMessage());
        }
    }

    public void killTeam(Team team) {
        if (team == Team.derelict || team == Team.green || !team.data().active()) return;

        if (!team.data().players.isEmpty()) {
            var player = team.data().players.first();
            sessionService.broadcast("hexed-eliminated", args("nickname", player.coloredName()));
            player.team(Team.derelict);
        }

        team.data().cores.each(core -> core.tile.setNet(Blocks.coreShard, Team.green, 0));

        team.data().destroyToDerelict();

        team.data().units.each(Unitc::kill);
        team.data().plans.clear();
    }
}
