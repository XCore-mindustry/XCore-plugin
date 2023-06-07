package org.xcore.plugin.modules;

import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.utils.Utils;

import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.database;
import static useful.Bundle.send;

public class MiniPvP {
    public static final Seq<String> defeatedPlayers = new Seq<>();

    public static void init() {
        if (!config.isMiniPvP()) return;
        Utils.showLeaderboard(Utils::getPvPLeaderboard);

        Events.on(EventType.PlayEvent.class, e -> defeatedPlayers.clear());
        Events.on(EventType.PlayerConnectionConfirmed.class, e -> {
            if (defeatedPlayers.contains(e.player.uuid())) {
                e.player.team(Team.derelict);
                send(e.player, "pvp.you-spectator");
            }
        });

        Events.on(EventType.GameOverEvent.class, e -> {
            if (e.winner == Team.derelict) return;

            e.winner.data().players.each(p -> {
                var data = database.getCached(p.uuid());

                int increased = 150 / (e.winner.data().players.size + 1);
                data.pvpRating += increased;
                send(p, "pvp.team-won", increased);
                Log.info("@ rating increased by @", p.plainName(), increased);

                data.save();
            });
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            var team = event.tile.team();

            if (event.tile.block() instanceof CoreBlock) {
                if (team != Team.derelict && team.cores().size <= 1) {
                    team.data().players.each(p -> {
                        defeatedPlayers.add(p.uuid());

                        var data = database.getCached(p.uuid());

                        int reduced = 100 / (Groups.player.count(_p -> _p.team() != team) + 1);

                        if ((data.pvpRating - reduced) < 0) {
                            data.pvpRating = 0;
                        } else {
                            data.pvpRating -= reduced;
                        }
                        send(p, "pvp.team-lose", reduced);

                        Log.info("@ rating reduced by @", p.plainName(), reduced);

                        data.save();
                    });
                }
            }
        });

        XcorePlugin.info("MiniPvP loaded.");
    }
}
