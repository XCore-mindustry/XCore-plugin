package org.xcore.plugin.gamemode.pvp;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Log;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.service.LeaderboardService;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.TopMenuCacheService;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class MiniPvP {
    public final Seq<String> defeatedPlayers = new Seq<>();
    public boolean roundHadMultipleTeams = false;

    private final TomlXcoreConfig config;
    private final SessionService sessionService;
    private final PlayerDataRepository playerDataRepository;
    private final LeaderboardService leaderboardService;
    private final TopMenuCacheService topMenuCacheService;
    private final ObserverService observerService;
    private final Async async;

    @Inject
    public MiniPvP(TomlXcoreConfig config,
                   SessionService sessionService,
                   PlayerDataRepository playerDataRepository,
                   LeaderboardService leaderboardService,
                   TopMenuCacheService topMenuCacheService,
                   ObserverService observerService,
                   Async async) {
        this.config = config;
        this.sessionService = sessionService;
        this.playerDataRepository = playerDataRepository;
        this.leaderboardService = leaderboardService;
        this.topMenuCacheService = topMenuCacheService;
        this.observerService = observerService;
        this.async = async;
    }

    @PostConstruct
    public void init() {
        if (!"mini-pvp".equals(config.server.name)) return;

        leaderboardService.start((builder, player, locale) -> {
            Seq<PlayerData> sorted = new Seq<>();
            for (var d : sessionService.getAllCachedSnapshot()) {
                if (d.data.pvpRating != 0) {
                    sorted.add(d.data);
                }
            }
            sorted.sort(d -> d.pvpRating);
            sorted.reverse();

            sorted.truncate(10);
            builder.append(locale.format("leaderboard", args())).append("\n\n");

            for (int i = 0; i < sorted.size; i++) {
                var data = sorted.get(i);
                builder.append(locale.format("pvp-leaderboard-content", args(
                        "index", i + 1,
                        "nickname", data.nickname,
                        "rating", data.pvpRating
                ))).append("\n");
            }
        });

        Events.on(EventType.PlayEvent.class, e -> clearRoundState());
        Events.on(EventType.PlayerConnectionConfirmed.class, e -> {
            if (defeatedPlayers.contains(e.player.uuid())) {
                observerService.enter(e.player);
                Session session = sessionService.get(e.player);
                if (session == null || session.data == null) return;

                // TODO: session is not guaranteed to be created at PlayerConnectionConfirmed stage.
                session.locale().send("pvp-you-spectator", args());
            }
        });

        Events.run(EventType.Trigger.update, () -> {
            if (!roundHadMultipleTeams && Vars.state != null && Vars.state.isPlaying() && Vars.state.rules.pvp) {
                updateRoundTeamsPresence();
            }
        });

        Events.on(EventType.GameOverEvent.class, e -> {
            if (e.winner == Team.derelict) return;

            Seq<Player> winners = new Seq<>();
            Groups.player.each(p -> {
                if (p.team() == e.winner && !observerService.isObserving(p)) {
                    winners.add(p);
                }
            });
            if (winners.isEmpty() && e.winner.data() != null && e.winner.data().players != null) {
                e.winner.data().players.each(p -> {
                    if (!observerService.isObserving(p)) {
                        winners.add(p);
                    }
                });
            }
            if (winners.isEmpty()) return;

            int calculated = 150 / (winners.size + 1);
            int increased = Mathf.clamp(calculated, 10, 60);

            winners.each(p -> {
                var session = sessionService.get(p);
                if (session == null || session.data == null) return;
                var data = session.data;

                data.pvpRating += increased;
                session.locale().send("pvp-team-won", args("increased", increased + ""));
                Log.info("@ rating increased by @", p.plainName(), increased);

                persistRatingAsync(data);
            });
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            var team = event.tile.team();

            if (event.tile.block() instanceof CoreBlock) {
                updateRoundTeamsPresence();

                if (team != Team.derelict && team.cores().size <= 1) {
                    int allies = team.data().players.size;
                    int rawEnemies = Groups.player.count(pl -> pl.team() != team && !observerService.isObserving(pl));
                    final int enemies = Math.max(1, rawEnemies);

                    Seq.with(team.data().players).each(p -> {
                        defeatedPlayers.add(p.uuid());
                        observerService.enter(p);

                        var session = sessionService.get(p);
                        if (session == null || session.data == null) return;
                        var data = session.data;

                        int reduced = (int) (25f * ((float) allies / enemies));

                        reduced = Mathf.clamp(reduced, 5, 50);

                        if ((data.pvpRating - reduced) < 0) {
                            data.pvpRating = 0;
                        } else {
                            data.pvpRating -= reduced;
                        }
                        session.locale().send("pvp-team-lose", args("reduced", reduced + ""));

                        Log.info("@ rating reduced by @", p.plainName(), reduced);

                        persistRatingAsync(data);
                    });
                }

                if (Core.app != null) {
                    Core.app.post(this::checkPvPGameOver);
                } else {
                    checkPvPGameOver();
                }
            }
        });

        info("MiniPvP loaded.");
    }

    private void persistRatingAsync(PlayerData data) {
        String uuid = data.uuid;
        int rating = data.pvpRating;

        async.observe(playerDataRepository.updatePvpRatingAsync(uuid, rating), (persisted, error) -> {
            if (error != null) {
                Log.warn("Failed to persist PvP rating for @: @", uuid, error.getMessage());
            } else if (Boolean.TRUE.equals(persisted)) {
                topMenuCacheService.invalidateAllAsync();
            }
        });
    }

    public int countActivePlayers(Team team) {
        if (team == null || team == Team.derelict || observerService.isObserverTeam(team)) {
            return 0;
        }
        return Groups.player.count(p -> p.team() == team && !observerService.isObserving(p));
    }

    public Seq<Team> getAliveTeamsWithPlayers() {
        Seq<Team> alive = new Seq<>();
        if (Vars.state == null || Vars.state.teams == null) {
            return alive;
        }

        for (TeamData t : Vars.state.teams.getActive()) {
            if (t.team == Team.derelict || observerService.isObserverTeam(t.team)) continue;
            if (!t.isAlive()) continue;
            if (countActivePlayers(t.team) > 0) {
                alive.add(t.team);
            }
        }
        return alive;
    }

    public boolean updateRoundTeamsPresence() {
        if (Vars.state == null || Vars.state.teams == null) {
            return roundHadMultipleTeams;
        }

        int activeTeams = 0;
        for (TeamData t : Vars.state.teams.getActive()) {
            if (t.team == Team.derelict || observerService.isObserverTeam(t.team) || !t.isAlive()) continue;
            if (countActivePlayers(t.team) > 0) {
                activeTeams++;
            }
        }
        if (activeTeams >= 2) {
            roundHadMultipleTeams = true;
        }
        return roundHadMultipleTeams;
    }

    public void checkPvPGameOver() {
        if (!"mini-pvp".equals(config.server.name)) return;
        if (Vars.state == null || Vars.state.gameOver || Vars.state.isMenu() || !Vars.state.rules.pvp) return;
        if (!roundHadMultipleTeams) return;

        Seq<Team> remaining = getAliveTeamsWithPlayers();
        if (remaining.size <= 1) {
            Team winner = remaining.isEmpty() ? Team.derelict : remaining.first();
            Vars.state.gameOver = true;
            Events.fire(new EventType.GameOverEvent(winner));
        }
    }

    void clearRoundState() {
        defeatedPlayers.each(observerService::resetObserverState);
        defeatedPlayers.clear();
        roundHadMultipleTeams = false;
        try {
            Class<?> scClass = Class.forName("mindustry.server.ServerControl");
            Object instance = scClass.getField("instance").get(null);
            if (instance != null) {
                scClass.getField("inGameOverWait").setBoolean(instance, false);
            }
        } catch (Throwable ignored) {
        }
    }
}
