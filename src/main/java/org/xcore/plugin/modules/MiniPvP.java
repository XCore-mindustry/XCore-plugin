package org.xcore.plugin.modules;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.Utils;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.menus.TeamSelectMenu;

import static org.xcore.plugin.PluginVars.*;

public class MiniPvP {
    public static void init() {
        if (!config.isMiniPvP()) return;

        Database.init();
        Timer.schedule(() -> {
            if (Groups.player.isEmpty()) return;
            Groups.player.each(player -> Call.infoPopup(player.con, Utils.getLeaderboard(), 5f, 8, 0, 2, 50, 0));
        }, 0f, 5f);

        Vars.netServer.chatFormatter = (player, message) -> player != null ? "[coral][[[cyan]" + Database.cachedPlayerData.get(player.uuid()).rating + " [sky]#[white] " + player.coloredName() + "[coral]]: [white]" + message : message;

        Events.on(EventType.PlayerJoin.class, event -> Database.cachedPlayerData.put(
                event.player.uuid(), Database.getPlayerData(event.player)
                .setNickname(event.player.coloredName()))
        );

        Events.on(EventType.PlayerLeave.class, event -> Database.cachedPlayerData.remove(event.player.uuid()));

        Events.on(EventType.GameOverEvent.class, e -> {
            if (e.winner == Team.derelict) return;

            e.winner.data().players.each(p -> {
                var data = Database.cachedPlayerData.get(p.uuid());

                int increased = 150 / (e.winner.data().players.size + 1);
                data.rating += increased;
                p.sendMessage("Your team has won. Your rating has increased by " + increased);
                Log.info("@ rating increased by @", p.plainName(), increased);

                Database.setPlayerData(data);
                Database.cachedPlayerData.put(p.uuid(), data);
            });
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            var team = event.tile.team();

            if (event.tile.block() instanceof CoreBlock) {
                if (team != Team.derelict && team.cores().size <= 1) {
                    team.data().players.each(p -> {
                        Core.app.post(() -> {
                            if (Vars.state.teams.getActive().size != 1) TeamSelectMenu.show(p);
                        });

                        var data = Database.cachedPlayerData.get(p.uuid());

                        int reduced = 100 / (Groups.player.count(_p->_p.team() != team) + 1);

                        if ((data.rating - reduced) < 0) {
                            data.rating = 0;
                            p.sendMessage("Your team lost. Your rating is 0");
                        } else {
                            data.rating -= reduced;
                            p.sendMessage("Your team lost. Your rating is reduced by " + reduced);
                        }
                        Log.info("@ rating reduced by @", p.plainName(), reduced);

                        Database.setPlayerData(data);
                        Database.cachedPlayerData.put(p.uuid(), data);
                    });
                }
            }
        });

        XcorePlugin.info("MiniPvP loaded.");
    }
}
