package org.xcore.plugin.modules;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.utils.Utils;

import static mindustry.Vars.netServer;
import static mindustry.Vars.player;
import static org.xcore.plugin.PluginVars.config;
import static useful.Bundle.bundled;

public class MiniPvP {
    public static Seq<String> losingPlayers = new Seq<>();

    public static void init() {
        if (!config.isMiniPvP()) return;
        Utils.showLeaderboard(Utils::getPvPLeaderboard);

        Events.on(EventType.GameOverEvent.class, e -> {
            losingPlayers.clear();
            if (e.winner == Team.derelict) return;

            e.winner.data().players.each(p -> {
                var data = Database.getCached(p.uuid());

                int increased = 150 / (e.winner.data().players.size + 1);
                data.pvpRating += increased;
                bundled(p, "pvp.team-won", increased);
                Log.info("@ rating increased by @", p.plainName(), increased);

                Database.setPlayerData(data);
                Database.setCached(data);
            });
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            var team = event.tile.team();

            if (event.tile.block() instanceof CoreBlock) {
                if (team != Team.derelict && team.cores().size <= 1) {
                    team.data().players.each(p -> {
                        Core.app.post(() -> {
                            if (Vars.state.teams.getActive().size != 1) {
                                p.team(netServer.assignTeam(p));
                                if (!losingPlayers.contains(p.uuid())) losingPlayers.add(p.uuid());
                            }
                        });

                        if (losingPlayers.contains(p.uuid())) return;

                        var data = Database.getCached(p.uuid());

                        int reduced = 100 / (Groups.player.count(_p -> _p.team() != team) + 1);

                        if ((data.pvpRating - reduced) < 0) {
                            data.pvpRating = 0;
                        } else {
                            data.pvpRating -= reduced;
                        }
                        bundled(p, "pvp.team-lose", reduced);

                        Log.info("@ rating reduced by @", p.plainName(), reduced);

                        Database.setPlayerData(data);
                        Database.setCached(data);
                    });
                }
            }
        });

        XcorePlugin.info("MiniPvP loaded.");
    }
}
