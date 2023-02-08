package org.xcore.plugin.commands;

import arc.Events;
import arc.struct.Seq;
import arc.util.CommandHandler;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.modules.Database;
import org.xcore.plugin.modules.MiniHexed;
import org.xcore.plugin.modules.models.PlayerData;

import static mindustry.Vars.mods;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.modules.MiniHexed.killTeam;

public class ClientCommands {
    public static void register(CommandHandler handler) {
        handler.<Player>register("discord", "Redirects you to discord server", (args, player) -> Call.openURI(player.con, discordURL));

        handler.<Player>register("js", "<code...>", "Execute javascript. [red]ADMIN ONLY", (args, player) -> {
            if (!player.admin) return;
            player.sendMessage("[green]" + mods.getScripts().runConsole(args[0]));
        });

        handler.<Player>register("rtv", "[off]", "Rock the vote to change map", (args, player) -> {
            if (player.admin()) {
                rtvEnabled = args.length != 1 || !args[0].equals("off");
            }
            if (!rtvEnabled) {
                player.sendMessage("RTV: RockTheVote is disabled");
                return;
            }
            rtvVotes.add(player.uuid());
            int cur = rtvVotes.size();
            int req = (int) Math.ceil(rtvRatio * Groups.player.size());
            Call.sendMessage("RTV: [accent]" + player.name + "[] wants to change the map, [green]" + cur +
                    "[] votes, [green]" + req + "[] required");

            if (cur < req) {
                return;
            }

            rtvVotes.clear();
            Call.sendMessage("RTV: [green] vote passed, changing map.");
            Events.fire(new EventType.GameOverEvent(Team.derelict));
        });

        handler.<Player>register("spectate", "Spectate.", (args, player) -> {
            if (config.isMiniHexed()) {
                var team = MiniHexed.teams.remove(player.uuid());

                if (team != null) {
                    killTeam(player.team());
                }
            }

            player.team(Team.derelict);
            player.unit().kill();
            player.sendMessage("You are now spectating.");
        });

        if (config.isMiniHexed()) {
            handler.removeCommand("votekick");
            handler.removeCommand("rtv");
            handler.removeCommand("vote");
        }

        if (config.isMiniPvP()) {
            handler.<Player>register("top", "Shows top players by wins", (args, player) -> {
                Seq<PlayerData> leaders = Database.getLeaders();

                var builder = new StringBuilder();
                if (leaders.isEmpty()) {
                    builder.append("Empty.");
                } else for (int i = 0; i < leaders.size; i++) {
                    var data = leaders.get(i);

                    builder.append("[orange]").append(i + 1).append(". ")
                            .append(data.nickname).append("[accent]: [cyan]")
                            .append(data.rating).append("\n");
                }
                player.sendMessage(builder.toString());
            });
        }
    }
}
