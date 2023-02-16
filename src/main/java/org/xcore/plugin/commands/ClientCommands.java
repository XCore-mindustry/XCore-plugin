package org.xcore.plugin.commands;

import arc.Events;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Timekeeper;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.Utils;
import org.xcore.plugin.modules.Database;
import org.xcore.plugin.modules.MiniHexed;
import org.xcore.plugin.modules.models.PlayerData;

import static mindustry.Vars.mods;
import static mindustry.Vars.netServer;
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

        handler.removeCommand("votekick");
        handler.removeCommand("vote");

        handler.<Player>register("votekick", "[player...]", "Vote to kick a player.", (args, player) -> {
            if(Groups.player.size() < 3){
                player.sendMessage("[scarlet]At least 3 players are needed to start a votekick.");
                return;
            }

            if(player.isLocal()){
                player.sendMessage("[scarlet]Just kick them yourself if you're the host.");
                return;
            }

            if(currentlyKicking[0] != null){
                player.sendMessage("[scarlet]A vote is already in progress.");
                return;
            }

            if(args.length == 0){
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]Players to kick: \n");

                Groups.player.each(p -> !p.admin && p.con != null && p != player, p -> builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id()).append(")\n"));
                player.sendMessage(builder.toString());
            }else{
                Player found;
                if(args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))){
                    int id = Strings.parseInt(args[0].substring(1));
                    found = Groups.player.find(p -> p.id() == id);
                }else{
                    found = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
                }

                if(found != null){
                    if(found == player){
                        player.sendMessage("[scarlet]You can't vote to kick yourself.");
                    }else if(found.admin){
                        player.sendMessage("[scarlet]Did you really expect to be able to kick an admin?");
                    }else if(found.isLocal()){
                        player.sendMessage("[scarlet]Local players cannot be kicked.");
                    }else if(found.team() != player.team()){
                        player.sendMessage("[scarlet]Only players on your team can be kicked.");
                    }else{
                        Timekeeper vtime = cooldowns.get(player.uuid(), () -> new Timekeeper(voteCooldown));

                        if(!vtime.get()){
                            player.sendMessage("[scarlet]You must wait " + voteCooldown/60 + " minutes between votekicks.");
                            return;
                        }

                        Utils.VoteSession session = new Utils.VoteSession(currentlyKicking, found);
                        session.vote(player, 1);
                        vtime.reset();
                        currentlyKicking[0] = session;
                    }
                }else{
                    player.sendMessage("[scarlet]No player [orange]'" + args[0] + "'[scarlet] found.");
                }
            }
        });

        handler.<Player>register("vote", "<y/n>", "Vote to kick the current player.", (arg, player) -> {
            if(currentlyKicking[0] == null){
                player.sendMessage("[scarlet]Nobody is being voted on.");
            }else{
                if(player.isLocal()){
                    player.sendMessage("[scarlet]Local players can't vote. Kick the player yourself instead.");
                    return;
                }

                //hosts can vote all they want
                if((currentlyKicking[0].voted.contains(player.uuid()) || currentlyKicking[0].voted.contains(netServer.admins.getInfo(player.uuid()).lastIP))){
                    player.sendMessage("[scarlet]You've already voted. Sit down.");
                    return;
                }

                if(currentlyKicking[0].target == player){
                    player.sendMessage("[scarlet]You can't vote on your own trial.");
                    return;
                }

                if(currentlyKicking[0].target.team() != player.team()){
                    player.sendMessage("[scarlet]You can't vote for other teams.");
                    return;
                }

                int sign = switch(arg[0].toLowerCase()){
                    case "y", "yes" -> 1;
                    case "n", "no" -> -1;
                    default -> 0;
                };

                if(sign == 0){
                    player.sendMessage("[scarlet]Vote either 'y' (yes) or 'n' (no).");
                    return;
                }

                currentlyKicking[0].vote(player, sign);
            }
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
            handler.removeCommand("vote");
            handler.removeCommand("rtv");
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
