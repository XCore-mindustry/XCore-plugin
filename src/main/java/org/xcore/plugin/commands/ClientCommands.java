package org.xcore.plugin.commands;

import arc.Core;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.hexed.HexedRanks;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.modules.votes.VoteRtv;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.HexMember;
import org.xcore.plugin.utils.models.PlayerData;
import useful.Bundle;

import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;
import static mindustry.Vars.world;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.modules.hexed.MiniHexed.killTeam;
import static org.xcore.plugin.modules.hexed.MiniHexed.members;
import static org.xcore.plugin.utils.Find.findTranslatorLanguage;
import static org.xcore.plugin.utils.Utils.reloadWorld;
import static org.xcore.plugin.utils.Utils.voteChoice;
import static useful.Bundle.*;

public class ClientCommands {
    public static void register(CommandHandler handler) {
        handler.removeCommand("help");
        register("help", (args, player) -> {
            if (args.length > 0 && !Strings.canParseInt(args[0])) {
                send(player, "error.page-number");
                return;
            }
            int commandsPerPage = 6;
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float) netServer.clientCommands.getCommandList().size / commandsPerPage);

            page--;

            if (page >= pages || page < 0) {
                send(player, "error.page-between", pages);
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(format("commands.help.start-content", player.locale, page + 1, pages));

            for (int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), netServer.clientCommands.getCommandList().size); i++) {
                CommandHandler.Command command = netServer.clientCommands.getCommandList().get(i);
                result.append(format("commands.help.content",
                        player.locale,
                        command.text,
                        format("commands." + command.text + ".params", player.locale),
                        format("commands." + command.text + ".description", player.locale)));
            }
            player.sendMessage(result.toString());
        });

        register("discord", (args, player) -> Call.openURI(player.con, discordUrl));

        handler.removeCommand("t");
        register("t", (args, player) -> sendFrom(other -> other.team() == player.team(), player, args[0], "commands.t.chat", player.team().color, player.coloredName(), args[0]));

        register("js", (args, player) -> {
            PlayerData data = database.getCached(player.uuid());

            if (!player.admin || !data.jsAccess) {
                send(player, "error.access-denied");
                return;
            }

            player.sendMessage("[green]" + Vars.mods.getScripts().runConsole(args[0]));
        });

        register("artv", (args, player) -> {
            if (!player.admin) return;

            var map = args.length > 0 ? Utils.findMap(args[0]) : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);

            if (map == null) {
                send(player, "error.map-not-found");
                return;
            }

            Timer.schedule(() -> reloadWorld(() -> world.loadMap(map, map.applyRules(Gamemode.valueOf(Core.settings.getString("lastServerMode"))))), mapLoadDelay);

            send("commands.artv.map-skipped", player.coloredName());
        });

        register("rtv", (args, player) -> {
            if (vote != null) {
                send(player, "error.vote-in-progress");
                return;
            }

            var map = args.length > 0 ? Utils.findMap(args[0]) : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);

            if (map == null) {
                send(player, "error.map-not-found");
                return;
            }

            vote = new VoteRtv(map);
            vote.vote(player, 1);
        });

        register("lb", (args, player) -> {
            var data = database.getCached(player.uuid());

            data.leaderboard = !data.leaderboard;

            send(player, "commands.lb.success", data.leaderboard);
            database.setCached(data);
            database.getPlayerDataExecutor().setPlayerData(data);
        });

        register("login", (args, player) -> {
            SockCommunicator.sendEvent(new SocketEvents.AdminRequestEvent(player.uuid(), player.name, config.server));
            send(player, "commands.login.success");
        });

        register("tr", (args, player) -> {
            var data = database.getCached(player.uuid());

            switch (args[0].toLowerCase()) {
                case "off" -> {
                    data.translatorLanguage = "off";
                    send(player, "commands.tr.off");
                }
                case "auto" -> {
                    var lang = findTranslatorLanguage(player.locale);
                    data.translatorLanguage = lang == null ? "en" : lang;
                }
                default -> {
                    var lang = findTranslatorLanguage(args[0]);
                    if (lang == null) {
                        send(player, "commands.tr.not-found");
                        return;
                    }

                    data.translatorLanguage = lang;
                }
            }
            send(player, "commands.tr.success", translatorLanguages.get(data.translatorLanguage));

            database.getPlayerDataExecutor().setPlayerData(data);
            database.setCached(data);
        });

        register("maps", (args, player) -> {
            if (args.length == 1 && !Strings.canParseInt(args[0])) {
                send(player, "commands.maps.page-must-number");
                return;
            }

            StringBuilder builder = new StringBuilder();
            Seq<Map> list = Utils.getAvailableMaps();
            Map map;
            int page = args.length == 1 ? Strings.parseInt(args[0]) : 1, lines = 8, pages = Mathf.ceil(list.size / lines);
            if (list.size % lines != 0) pages++;

            if (page > pages || page < 1) {
                send(player, "error.page-between", pages);
                return;
            }

            builder.append(format("commands.maps.start-content", player.locale, Vars.state.map.name(), page, pages));
            for (int i = (page - 1) * lines; i < lines * page; i++) {
                try {
                    map = list.get(i);
                    builder.append(format("commands.maps.content", player.locale,
                            i + 1, map.name(), map.width, map.height, map.author()));
                } catch (IndexOutOfBoundsException e) {
                    break;
                }
            }
            player.sendMessage(builder.toString());
        });

        handler.removeCommand("votekick");
        register("votekick", (args, player) -> {
            if (voteKick != null) {
                send(player, "error.vote-in-progress");
                return;
            }

            PlayerData data = database.getPlayerDataExecutor().getPlayerData(player.uuid());

            if (data.totalPlayTime < votekickPlayTime) {
                send(player, "error.votekick-total-playtime", votekickPlayTime);
                return;
            }

            Player found = Find.player(args[0]);

            if (found == null) {
                send(player, "error.player-not-found");
                return;
            }

            if (found.admin) {
                player.kick(Bundle.get("error.player-admin", player), 5 * 60 * 1000);
            }

            if (found.team() != player.team()) {
                send(player, "error.player-not-teammate");
                return;
            }

            voteKick = new VoteKick(player, found);
            voteKick.vote(player, 1);
        });

        handler.removeCommand("vote");
        register("vote", (args, player) -> {
            if (voteKick == null) {
                send(player, "error.no-voting");
                return;
            }

            if (voteKick.voted.containsKey(player.id)) {
                send(player, "error.already-voted");
                return;
            }

            if (voteKick.target == player) {
                send(player, "error.vote-yourself");
                return;
            }

            int sign = voteChoice(args[0]);
            if (sign == 0) {
                send(player, "commands.vote.vote-with");
                return;
            }

            voteKick.vote(player, sign);
        });

        register("spectate", (args, player) -> {
            if (config.isMiniHexed()) killTeam(player.team());

            player.team(Team.derelict);
            player.unit().kill();
            send(player, "commands.spectate.success");
        });

        if (config.isMiniHexed()) {
            handler.removeCommand("history");
            handler.removeCommand("votekick");
            handler.removeCommand("vote");
            handler.removeCommand("rtv");

            register("rank", (args, player) -> {
                var target = args.length > 0 ? Find.player(args[0]) : player;

                if (target == null) {
                    send(player, "error.player-not-found");
                    return;
                }

                var data = database.getCached(target.uuid());
                var rank = data.hexedRank();

                infoMessage(player, "commands.rank.content",
                        target.name,
                        rank.tag,
                        format("hexed.ranks." + rank.name(), player.locale),
                        data.hexedPoints,
                        rank.next.requirements.wins());
            });

            register("ranks", (args, player) -> {
                var builder = new StringBuilder();

                for (HexedRanks.HexedRank rank : HexedRanks.HexedRank.values()) {
                    builder.append(format("commands.ranks.content", player.locale,
                            rank.tag,
                            format("hexed.ranks." + rank.name(), player.locale),
                            rank.requirements == null ? 0 : rank.requirements.wins()));
                }
                builder.append(format("commands.ranks.footer", player.locale));

                Call.infoMessage(player.con, builder.toString());
            });

            register("top", (args, player) -> {
                Seq<PlayerData> leaders = database.getPlayerDataExecutor().getLeaders("hexedRank", "hexedPoints");

                var builder = new StringBuilder();
                if (leaders.isEmpty()) {
                    builder.append(format("empty", player.locale));
                } else for (int i = 0; i < leaders.size; i++) {
                    var data = leaders.get(i);

                    builder.append(format("commands.top.hexed-content",
                            player.locale,
                            i + 1,
                            data.nickname,
                            format("hexed.ranks." + data.hexedRank().name(), player.locale),
                            data.hexedPoints));
                }
                player.sendMessage(builder.toString());
            });

            register("ai", (args, player) -> {
                HexMember member = members.get(player.uuid());

                if (player.team() == Team.derelict || member.team == Team.derelict) {
                    send(player, "error.spectator");
                    return;
                }

                switch (args[0]) {
                    case "attack", "a" -> member.setUnitState(Utils.UnitState.ATTACK);
                    case "idle", "i" -> member.setUnitState(Utils.UnitState.IDLE);
                    default -> {
                        send(player, "commands.ai.usage");
                        return;
                    }
                }

                send(player, "success");
            });
        }

        if (config.isMiniPvP()) {
            register("top", (args, player) -> {
                Seq<PlayerData> leaders = database.getPlayerDataExecutor().getLeaders("pvpRating");

                var builder = new StringBuilder();
                if (leaders.isEmpty()) {
                    builder.append(format("empty", player.locale));
                } else for (int i = 0; i < leaders.size; i++) {
                    var data = leaders.get(i);

                    builder.append(format("commands.top.pvp-content", player.locale,
                            i + 1, data.nickname, data.pvpRating));
                }
                player.sendMessage(builder.toString());
            });
        }

        register("mute", (args, player) -> {
            if (!player.admin) {
                send(player, "error.access-denied");
                return;
            }
            var target = Find.playerByName(args[0]);

            if (target == null) {
                send(player, "error.player-not-found");
                return;
            }

            PlayerData data = database.getCached(target.uuid());

            data.muted = Time.millis() + TimeUnit.HOURS.toMillis(Strings.parseInt(args[1]));

            database.getPlayerDataExecutor().setPlayerData(data);
            send(player, "commands.mute.success", target.coloredName());
            send(target, "you-are-muted-by", player.coloredName(), args[1]);
        });

        register("unmute", (args, player) -> {
            if (!player.admin) {
                send(player, "error.access-denied");
                return;
            }
            var target = Find.playerByName(args[0]);

            if (target == null) {
                send(player, "error.player-not-found");
                return;
            }

            PlayerData data = database.getCached(target.uuid());

            data.muted = 0;
            database.getPlayerDataExecutor().setPlayerData(data);
            send(player, "commands.unmute.success", target.coloredName());
        });
    }

    public static void register(String name, CommandHandler.CommandRunner<Player> runner) {
        netServer.clientCommands.register(name,
                Bundle.get("commands." + name + ".params", "", defaultLocale),
                Bundle.get("commands." + name + ".description", defaultLocale),
                runner);
    }
}
