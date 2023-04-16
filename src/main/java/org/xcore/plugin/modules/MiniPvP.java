package org.xcore.plugin.modules;

import arc.Events;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Timer;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.utils.Utils;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.config;
import static useful.Bundle.bundled;
import static useful.Bundle.sendToChat;

public class MiniPvP {
    public static ObjectMap<String, Team> teams = new ObjectMap<>();

    public static boolean closed = false;

    public static void init() {
        if (!config.isMiniPvP()) return;
        Utils.showLeaderboard(Utils::getPvPLeaderboard);

        Timer.schedule(() -> {
            if (Groups.player.size() < 4) {
                closed = false;
                return;
            }
            if (!closed) sendToChat("pvp.server-closed");
            closed = true;
        }, 120, 120);

        Events.on(EventType.PlayEvent.class, e -> closed = false);

        Events.on(EventType.PlayerConnectionConfirmed.class, e -> {
            Team team = teams.get(e.player.uuid());

            if (team != null) {
                e.player.team(team);
                return;
            }

            if (!closed) {
                var t = netServer.assignTeam(e.player);
                e.player.team(t);
                teams.put(e.player.uuid(), t);
                return;
            }

            e.player.team(Team.derelict);
            bundled(e.player, "pvp.you-spectator");
        });

        Events.on(EventType.GameOverEvent.class, e -> {
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
                        var data = Database.getCached(p.uuid());

                        int reduced = 100 / (Groups.player.count(_p -> _p.team() != team) + 1);

                        if ((data.pvpRating - reduced) < 0) {
                            data.pvpRating = 0;
                        } else {
                            data.pvpRating -= reduced;
                        }
                        bundled(p, "pvp.team-lose", reduced);

                        Log.info("@ rating reduced by @", p.plainName(), reduced);

                        teams.remove(p.uuid());
                        Database.setPlayerData(data);
                        Database.setCached(data);
                    });
                }
            }
        });

        XcorePlugin.info("MiniPvP loaded.");
    }
}
