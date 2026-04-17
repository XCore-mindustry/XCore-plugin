package org.xcore.plugin.gamemode.pvp;

import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Log;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.service.LeaderboardService;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.TopMenuCacheService;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class MiniPvP {
    public final Seq<String> defeatedPlayers = new Seq<>();

    private final Config config;
    private final SessionService sessionService;
    private final PlayerDataRepository playerDataRepository;
    private final LeaderboardService leaderboardService;
    private final TopMenuCacheService topMenuCacheService;

    @Inject
    public MiniPvP(Config config,
                   SessionService sessionService,
                   PlayerDataRepository playerDataRepository,
                   LeaderboardService leaderboardService,
                   TopMenuCacheService topMenuCacheService) {
        this.config = config;
        this.sessionService = sessionService;
        this.playerDataRepository = playerDataRepository;
        this.leaderboardService = leaderboardService;
        this.topMenuCacheService = topMenuCacheService;
    }

    @PostConstruct
    public void init() {
        if (!config.isMiniPvP()) return;

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

        Events.on(EventType.PlayEvent.class, e -> defeatedPlayers.clear());
        Events.on(EventType.PlayerConnectionConfirmed.class, e -> {
            if (defeatedPlayers.contains(e.player.uuid())) {
                e.player.team(Team.derelict);
                Session session = sessionService.get(e.player);
                if (session == null || session.data == null) return;

                // TODO: session is not guaranteed to be created at PlayerConnectionConfirmed stage.
                session.locale().send("pvp-you-spectator", args());
            }
        });

        Events.on(EventType.GameOverEvent.class, e -> {
            if (e.winner == Team.derelict) return;

            e.winner.data().players.each(p -> {
                var session = sessionService.get(p);
                var data = session.data;

                int calculated = 150 / (e.winner.data().players.size + 1);
                int increased = Mathf.clamp(calculated, 10, 60);

                data.pvpRating += increased;
                session.locale().send("pvp-team-won", args("increased", increased + ""));
                Log.info("@ rating increased by @", p.plainName(), increased);

                if (playerDataRepository.updatePvpRating(data.uuid, data.pvpRating)) {
                    topMenuCacheService.invalidateAll();
                }
            });
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            var team = event.tile.team();

            if (event.tile.block() instanceof CoreBlock) {
                if (team != Team.derelict && team.cores().size <= 1) {
                    int allies = team.data().players.size;
                    int rawEnemies = Groups.player.count(pl -> pl.team() != team && pl.team() != Team.derelict);
                    final int enemies = Math.max(1, rawEnemies);

                    team.data().players.each(p -> {
                        defeatedPlayers.add(p.uuid());

                        var session = sessionService.get(p);
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

                        if (playerDataRepository.updatePvpRating(data.uuid, data.pvpRating)) {
                            topMenuCacheService.invalidateAll();
                        }
                    });
                }
            }
        });

        info("MiniPvP loaded.");
    }
}
