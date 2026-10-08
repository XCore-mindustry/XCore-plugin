package org.xcore.plugin.gamemode.pvp;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import com.ospx.flubundle.Args;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.mindustry.ContentNames;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.server.ServerControl;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.gamemode.pvp.rating.MiniPvPMatchTracker;
import org.xcore.plugin.gamemode.pvp.rating.MiniPvPRatingSettler;
import org.xcore.plugin.service.LeaderboardService;
import org.xcore.plugin.service.TopMenuCacheService;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
    private final MiniPvPMatchTracker matchTracker;
    private final MiniPvPRatingSettler ratingSettler;

    private long lastHudUpdate = 0L;

    @Inject
    public MiniPvP(
            TomlXcoreConfig config,
            SessionService sessionService,
            PlayerDataRepository playerDataRepository,
            LeaderboardService leaderboardService,
            TopMenuCacheService topMenuCacheService,
            ObserverService observerService,
            Async async,
            MiniPvPMatchTracker matchTracker,
            MiniPvPRatingSettler ratingSettler
    ) {
        this.config = config;
        this.sessionService = sessionService;
        this.playerDataRepository = playerDataRepository;
        this.leaderboardService = leaderboardService;
        this.topMenuCacheService = topMenuCacheService;
        this.observerService = observerService;
        this.async = async;
        this.matchTracker = matchTracker;
        this.ratingSettler = ratingSettler;
    }

    public MiniPvP(
            TomlXcoreConfig config,
            SessionService sessionService,
            PlayerDataRepository playerDataRepository,
            LeaderboardService leaderboardService,
            TopMenuCacheService topMenuCacheService,
            ObserverService observerService,
            Async async
    ) {
        this(config, sessionService, playerDataRepository, leaderboardService, topMenuCacheService, observerService, async,
                new MiniPvPMatchTracker(), null);
    }

    @PostConstruct
    public void init() {
        if (!"mini-pvp".equals(config.server.name)) return;

        Events.on(EventType.PlayEvent.class, e -> {
            clearRoundState();
            if (matchTracker != null) {
                matchTracker.startMatch(observerService);
            }
            if (ratingSettler != null) {
                ratingSettler.onNewRound();
            }
        });

        Events.on(EventType.PlayerConnectionConfirmed.class, e -> {
            if (defeatedPlayers.contains(e.player.uuid())) {
                observerService.enter(e.player);
                Session session = sessionService.get(e.player);
                if (session == null || session.data == null) return;
                session.locale().send("pvp-you-spectator", args());
            } else if (matchTracker != null) {
                matchTracker.onPlayerJoin(e.player);
            }
        });

        Events.on(EventType.PlayerLeave.class, e -> {
            if (matchTracker != null && e.player != null) {
                matchTracker.onPlayerLeave(e.player);
            }
        });

        Events.run(EventType.Trigger.update, () -> {
            if (!roundHadMultipleTeams && Vars.state != null && Vars.state.isPlaying() && Vars.state.rules.pvp) {
                updateRoundTeamsPresence();
            }
            updateHud();
        });

        Events.on(EventType.GameOverEvent.class, e -> {
            if (e.winner == Team.derelict) return;

            if (ratingSettler != null) {
                ratingSettler.settle(e.winner);
            }
            try {
                Call.hideHudText();
            } catch (Exception ignored) {
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            var team = event.tile.team();

            if (event.tile.block() instanceof CoreBlock) {
                updateRoundTeamsPresence();

                if (team != Team.derelict && team.cores().size <= 1) {
                    if (matchTracker != null) {
                        int aliveTeams = countAliveTeamsWithCores();
                        matchTracker.onTeamEliminated(team.id, aliveTeams);

                        // Mark all players who participated on this team (including disconnected leavers) as defeated
                        for (var p : matchTracker.participants().values()) {
                            if (p.teamId() == team.id) {
                                defeatedPlayers.add(p.uuid());
                            }
                        }
                    }

                    if (team.data() != null && team.data().players != null) {
                        Seq.with(team.data().players).each(p -> {
                            defeatedPlayers.add(p.uuid());
                            observerService.enter(p);

                            var session = sessionService.get(p);
                            if (session != null && session.locale() != null) {
                                session.locale().send("pvp-you-spectator", args());
                            }
                        });
                    }
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

    private void updateHud() {
        if (Vars.state == null || !Vars.state.isPlaying() || !roundHadMultipleTeams) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHudUpdate < 1000L) {
            return;
        }
        lastHudUpdate = now;

        long started = matchTracker != null ? matchTracker.startedAt() : now;
        long durationSeconds = Math.max(0L, (now - started) / 1000L);
        String timeStr = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60);

        Map<Team, Integer> alive = new LinkedHashMap<>();
        if (Vars.state.teams != null) {
            for (TeamData t : Vars.state.teams.getActive()) {
                if (t.team == Team.derelict || observerService.isObserverTeam(t.team) || !t.isAlive()) continue;
                alive.put(t.team, countActivePlayers(t.team));
            }
        }

        if (alive.isEmpty()) {
            return;
        }

        // Rendered once per locale: team names and the HUD label are localized.
        Bundle bundle = Bundle.INSTANCE;
        Map<Locale, String> rendered = new HashMap<>();
        Groups.player.each(player -> {
            if (player.con == null) return;
            String text = rendered.computeIfAbsent(bundle.locale(player), locale -> bundle.format(locale,
                    "pvp-hud-status", Args.of("teams", teamsLine(alive, locale), "time", timeStr)));
            try {
                Call.setHudText(player.con, text);
            } catch (Exception ignored) {
            }
        });
    }

    static String teamsLine(Map<Team, Integer> alive, Locale locale) {
        StringBuilder line = new StringBuilder();
        alive.forEach((team, count) -> {
            if (!line.isEmpty()) line.append(" [gray]vs[] ");
            line.append("[#").append(team.color.toString()).append("]")
                    .append(ContentNames.vanilla().name(team, locale))
                    .append(" (").append(count).append(")[]");
        });
        return line.toString();
    }

    public int countAliveTeamsWithCores() {
        if (Vars.state == null || Vars.state.teams == null) return 0;
        int count = 0;
        for (TeamData t : Vars.state.teams.getActive()) {
            if (t.team == Team.derelict || observerService.isObserverTeam(t.team)) continue;
            if (t.cores != null && !t.cores.isEmpty()) {
                count++;
            }
        }
        return count;
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
        if (ServerControl.instance != null) {
            ServerControl.instance.inGameOverWait = false;
        }
        try {
            Call.hideHudText();
        } catch (Exception ignored) {
        }
    }
}
