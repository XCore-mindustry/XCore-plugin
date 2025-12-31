package org.xcore.plugin.modules;

import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.utils.Utils;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.PluginVars.*;

public class MiniPvP {
    public static final Seq<String> defeatedPlayers = new Seq<>();

    public static void init() {
        if (!config.isMiniPvP()) return;
        Utils.showLeaderboard(Utils::getPvPLeaderboard);

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

                data.save();
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

                        data.save();
                    });
                }
            }
        });

        XcorePlugin.info("MiniPvP loaded.");
    }
}
