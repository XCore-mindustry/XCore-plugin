package org.xcore.plugin.commands;

import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.xcore.plugin.modules.hexed.HexedRanks;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.modules.votes.VoteRtv;
import org.xcore.plugin.utils.Database;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.HexMember;
import org.xcore.plugin.utils.models.PlayerData;

import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.modules.hexed.MiniHexed.killTeam;
import static org.xcore.plugin.modules.hexed.MiniHexed.members;
import static org.xcore.plugin.utils.Utils.findTranslatorLanguage;
import static org.xcore.plugin.utils.Utils.voteChoice;

public class ClientCommands {
    public static void register(CommandHandler handler) {
        handler.<Player>register("discord", "Redirects you to discord server", (args, player) -> Call.openURI(player.con, discordURL));

        handler.<Player>register("js", "<code...>", "Execute javascript. [red]JS Access users only.", (args, player) -> {
            PlayerData data = Database.getCached(player.uuid());

            if (!player.admin || !data.jsAccess) {
                player.sendMessage("[blue]JS[]: [red]Access denied.");
                return;
            }

            player.sendMessage("[green]" + Vars.mods.getScripts().runConsole(args[0]));
        });

        handler.<Player>register("artv", "Change map. [red]Admin only", (args, player) -> {
            if (!player.admin) return;

            Events.fire(new EventType.GameOverEvent(Team.derelict));
            Call.sendMessage(Strings.format("@[accent] skipped map.", player.coloredName()));
        });

        handler.<Player>register("rtv", "[map]", "Rock the vote to change map", (args, player) -> {
            if (vote != null) {
                player.sendMessage("[scarlet]⚠ A voting is already in progress.");
                return;
            }

            var map = args.length > 0 ? Utils.findMap(args[0]) : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);

            if (map == null) {
                player.sendMessage("[scarlet]⚠ Map not found! [accent]Use [cyan]/maps[] to see a list of all available maps.");
                return;
            }

            vote = new VoteRtv(map);
            vote.vote(player, 1);
        });

        handler.<Player>register("history", "Enable/disable block inspection.", (args, player) -> {
            var data = Database.getCached(player.uuid());

            data.history = !data.history;

            player.sendMessage("[accent]History set to [red]" + data.history);
            Database.setCached(data);
        });

        handler.<Player>register("tr", "<lang>", "Set the translator language", (args, player) -> {
            var data = Database.getCached(player.uuid());

            String success = "[accent]The translator language has been successfully changed to [grey]@[]!";

            switch (args[0].toLowerCase()) {
                case "off" -> {
                    data.translatorLanguage = "off";
                    player.sendMessage("[accent]Translator is [grey]off[].");
                }
                case "auto" -> {
                    var lang = findTranslatorLanguage(player.locale);
                    data.translatorLanguage = lang == null ? "en" : lang;
                    player.sendMessage(Strings.format(
                            success, translatorLanguages.get(data.translatorLanguage)));
                }
                default -> {
                    var lang = findTranslatorLanguage(args[0]);
                    if (lang == null) {
                        player.sendMessage("[accent]There is no such language.");
                        break;
                    }

                    data.translatorLanguage = lang;

                    player.sendMessage(Strings.format(
                            success, translatorLanguages.get(data.translatorLanguage)));
                }
            }

            Database.setPlayerData(data);
            Database.setCached(data);
        });

        handler.<Player>register("maps", "[page]", "List all maps on server", (args, player) -> {
            if (args.length == 1 && !Strings.canParseInt(args[0])) {
                player.sendMessage("[scarlet]'page' must be a number.");
                return;
            }

            StringBuilder builder = new StringBuilder();
            Seq<Map> list = Utils.getAvailableMaps();
            Map map;
            int page = args.length == 1 ? Strings.parseInt(args[0]) : 1, lines = 8, pages = Mathf.ceil(list.size / lines);
            if (list.size % lines != 0) pages++;

            if (page > pages || page < 1) {
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and [orange]" + pages + "[].");
                return;
            }

            builder.append("[accent]Actual map: []").append(Vars.state.map.name()).append("[white]")
                    .append("\n[orange][gold]Maps list [lightgray]").append(page).append("[gray]/[lightgray]")
                    .append(pages);
            for (int i = (page - 1) * lines; i < lines * page; i++) {
                try {
                    map = list.get(i);
                    builder.append("\n").append(i + 1).append(". [orange] - [white]").append(map.name())
                            .append("[orange] | [white]").append(map.width).append("x")
                            .append(map.height).append("[orange] | By: [sky]").append(map.author());
                } catch (IndexOutOfBoundsException e) {
                    break;
                }
            }
            player.sendMessage(builder.toString());
        });

        handler.removeCommand("votekick");
        handler.<Player>register("votekick", "[player...]", "Vote to kick a player.", (args, player) -> {
            if (voteKick != null) {
                player.sendMessage("[scarlet]⚠ A voting is already in progress.");
                return;
            }

            Player found;
            if (args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                int id = Strings.parseInt(args[0].substring(1));
                found = Groups.player.find(p -> p.id() == id);
            } else {
                found = Groups.player.find(p -> Strings.stripColors(p.name).equalsIgnoreCase(args[0]));
            }

            if (found == null) {
                player.sendMessage("[accent]Player not found.");
                return;
            }

            voteKick = new VoteKick(player, found);
            voteKick.vote(player, 1);
        });

        handler.removeCommand("vote");
        handler.<Player>register("vote", "<y/n>", "Vote to kick the current player.", (args, player) -> {
            if (voteKick == null) {
                player.sendMessage("[scarlet]⚠ No voting at the moment.");
                return;
            }

            if (voteKick.voted.containsKey(player.id)) {
                player.sendMessage("[scarlet]⚠ You have already voted. Calm down.");
                return;
            }

            int sign = voteChoice(args[0]);
            if (sign == 0) {
                player.sendMessage("[scarlet]⚠ Vote with [orange]/vote <y/n>[].");
                return;
            }

            voteKick.vote(player, sign);
        });

        handler.<Player>register("spectate", "Spectate.", (args, player) -> {
            if (config.isMiniHexed()) killTeam(player.team());

            player.team(Team.derelict);
            player.unit().kill();
            player.sendMessage("You are now spectating.");
        });

        if (config.isMiniHexed()) {
            handler.removeCommand("history");
            handler.removeCommand("votekick");
            handler.removeCommand("vote");
            handler.removeCommand("rtv");

            handler.<Player>register("rank", "Shows information about your rank", (args, player) -> {
                var data = Database.getCached(player.uuid());

                if (data == null) {
                    player.sendMessage("[red]NOT AVAILABLE");
                    return;
                }

                var rank = data.hexedRank();

                Call.infoMessage(player.con, Strings.format("@ [accent]@\n[gold]Wins: @/@", rank.tag, rank.name, data.hexedPoints, rank.next.requirements.wins()));
            });

            handler.<Player>register("ranks", "Shows information about ranks", (args, player) -> {
                var builder = new StringBuilder();

                for (HexedRanks.HexedRank rank : HexedRanks.HexedRank.values()) {
                    builder.append(rank.tag).append(" [accent]").append(rank.name).append("\n")
                            .append("[gold]Requirements: ").append("[grey]").append(rank.requirements == null ? 0 : rank.requirements.wins())
                            .append(" [accent]wins[]").append("[]\n\n");
                }

                builder.append("[accent]The number of wins is rolled into the number of wins over players of your rank. ");

                Call.infoMessage(player.con, builder.toString());

            });

            handler.<Player>register("ai", "<idle/i/attack/a>", "Control ai", (args, player) -> {
                HexMember member = members.get(player.uuid());

                if (player.team() == Team.derelict || member.team == Team.derelict) {
                    player.sendMessage("[red]Error. [accent]You are spectator.");
                    return;
                }

                switch (args[0]) {
                    case "attack", "a" -> member.setUnitState(Utils.UnitState.ATTACK);
                    case "idle", "i" -> member.setUnitState(Utils.UnitState.IDLE);
                    default -> {
                        player.sendMessage("[red]attack(i) []or [accent]idle(i).");
                        return;
                    }
                }

                player.sendMessage("[green]Successfully.");
            });
        }

        if (config.isMiniPvP()) {
            handler.<Player>register("top", "Shows top players by rating", (args, player) -> {
                Seq<PlayerData> leaders = Database.getLeaders("pvpRating");

                var builder = new StringBuilder();
                if (leaders.isEmpty()) {
                    builder.append("Empty.");
                } else for (int i = 0; i < leaders.size; i++) {
                    var data = leaders.get(i);

                    builder.append("[orange]").append(i + 1).append(". ")
                            .append(data.nickname).append("[accent]: [cyan]")
                            .append(data.pvpRating).append("\n");
                }
                player.sendMessage(builder.toString());
            });
        }
    }
}
