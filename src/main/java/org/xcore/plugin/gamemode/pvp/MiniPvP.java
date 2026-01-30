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
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.LeaderboardService;
import org.xcore.plugin.database.DatabaseService;
import org.xcore.plugin.model.PlayerData;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class MiniPvP {
    public final Seq<String> defeatedPlayers = new Seq<>();

    private final Config config;
    private final DatabaseService database;
    private final BundleService bundle;
    private final LeaderboardService leaderboardService;

    @Inject
    public MiniPvP(Config config, DatabaseService database, BundleService bundle,
                   LeaderboardService leaderboardService) {
        this.config = config;
        this.database = database;
        this.bundle = bundle;
        this.leaderboardService = leaderboardService;
    }

    @PostConstruct
    public void init() {
        if (!config.isMiniPvP()) return;

        leaderboardService.start((builder, player, locale) -> {
            Seq<PlayerData> sorted = database.cachedPlayerData.copy().values().toSeq()
                    .select(d -> d.pvpRating != 0)
                    .sort(d -> d.pvpRating)
                    .reverse();

            sorted.truncate(10);
            builder.append(bundle.format(locale, "leaderboard", args())).append("\n\n");

            for (int i = 0; i < sorted.size; i++) {
                var data = sorted.get(i);
                builder.append(bundle.format(locale, "pvp-leaderboard-content", args(
                        "index", i + 1,
                        "nickname", data.nickname, // Важно: в базе храним цветной ник
                        "rating", data.pvpRating
                ))).append("\n");
            }
        });

        Events.on(EventType.PlayEvent.class, e -> defeatedPlayers.clear());
        Events.on(EventType.PlayerConnectionConfirmed.class, e -> {
            if (defeatedPlayers.contains(e.player.uuid())) {
                e.player.team(Team.derelict);
                bundle.send(e.player, "pvp-you-spectator", args());
            }
        });

        Events.on(EventType.GameOverEvent.class, e -> {
            if (e.winner == Team.derelict) return;

            e.winner.data().players.each(p -> {
                var data = database.getCached(p.uuid());

                int calculated = 150 / (e.winner.data().players.size + 1);
                int increased = Mathf.clamp(calculated, 10, 60);

                data.pvpRating += increased;
                bundle.send(p, "pvp-team-won", args("increased", increased + ""));
                Log.info("@ rating increased by @", p.plainName(), increased);

                database.getPlayerDataRepository().save(data);
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

                        var data = database.getCached(p.uuid());

                        int reduced = (int) (25f * ((float) allies / enemies));

                        reduced = Mathf.clamp(reduced, 5, 50);

                        if ((data.pvpRating - reduced) < 0) {
                            data.pvpRating = 0;
                        } else {
                            data.pvpRating -= reduced;
                        }
                        bundle.send(p, "pvp-team-lose", args("reduced", reduced + ""));

                        Log.info("@ rating reduced by @", p.plainName(), reduced);

                        database.getPlayerDataRepository().save(data);
                    });
                }
            }
        });

        info("MiniPvP loaded.");
    }
}