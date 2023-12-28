package org.xcore.plugin.commands;

import arc.Core;
import arc.math.Mathf;
import arc.struct.*;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.hexed.HexMember;
import org.xcore.plugin.modules.hexed.HexedRanks;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.modules.votes.VoteRtv;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.AdminData;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.MuteData;
import org.xcore.plugin.utils.models.PlayerData;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.modules.hexed.MiniHexed.killTeam;
import static org.xcore.plugin.modules.hexed.MiniHexed.members;
import static org.xcore.plugin.utils.Find.findTranslatorLanguage;
import static org.xcore.plugin.utils.Utils.reloadWorld;
import static org.xcore.plugin.utils.Utils.voteChoice;

public class ClientCommands {
    
    public static void register(CommandHandler handler) {
        register("help", (args, player) -> {
            if (args.length > 0 && !Strings.canParseInt(args[0])) {
                bundle.send(player, "error-page-number", args());
                return;
            }

            int commandsPerPage = 6;
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float) netServer.clientCommands.getCommandList().size / commandsPerPage);

            page--;

            if (page >= pages || page < 0) {
                bundle.send(player, "error-page-between", args("totalPages", pages));
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(bundle.format(bundle.locale(player), "commands-help-start-content",
                            args("page", page + 1,
                                    "totalPages", pages)))
                    .append("\n\n");

            for (int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), netServer.clientCommands.getCommandList().size); i++) {
                CommandHandler.Command command = netServer.clientCommands.getCommandList().get(i);
                if (command.text.equals("login")) continue;

                result.append(bundle.format(bundle.locale(player), "commands-help-content",
                                args("commandName", command.text,
                                        "commandParams", bundle.format(bundle.locale(player), "commands-" + command.text + "-params", command.paramText, args()),
                                        "commandDescription", bundle.format(bundle.locale(player), "commands-" + command.text + "-description", command.description, args()))))
                        .append("\n");
            }
            player.sendMessage(result.toString());
        });

        register("discord", (args, player) -> Call.openURI(player.con, discordUrl));

        register("t", (args, player) -> {
            var data = database.getCached(player.uuid());
            var mute = database.getMuteDatas().get(player.uuid());

            if (mute != null && !mute.expired()) {
                Duration remain = Duration.ofMillis(mute.expireDate.getTime() - Time.millis());
                bundle.send(player, "you-are-muted",
                        args("adminName", mute.adminName,
                                "reason", mute.reason,
                                "remainMinutes", remain.toMinutes(),
                                "remainSeconds", remain.toSecondsPart()));
                return;
            } else if (mute != null) {
                database.getMuteDatas().delete(player.uuid());
            }

            Groups.player.each(
                    other -> other.team() == player.team(),
                    p -> p.sendMessage(bundle.format(bundle.locale(p), "commands-t-chat", args(
                            "color", player.team().color,
                            "name", player.coloredName(),
                            "message", args[0])), player)
            );
        });

        register("g", (args, player) -> {
            var data = database.getCached(player.uuid());
            if (data.totalPlayTime < globalChatPlayTime && !player.admin) {
                bundle.send(player, "error-globalchat-total-playtime", args("globalChatPlayTime", globalChatPlayTime));
                return;
            }

            NetSock.post(new SocketEvents.GlobalChatEvent(player.coloredName(), args[0], config.server));
            NetSock.post(new SocketEvents.MessageEvent(player.plainName(), "[" + config.server + "] " + args[0].replace("`", "*"), "global"));
        });

        register("rtv", (args, player) -> {
            if (vote != null) {
                bundle.send(player, "error-vote-in-progress", args());
                return;
            }

            var map = args.length > 0 ? Utils.findMap(args[0]) : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);

            if (map == null) {
                bundle.send(player, "error-map-not-found", args());
                return;
            }

            vote = new VoteRtv(map);
            vote.vote(player, 1);
        });

        register("stats", (args, player) -> {
            PlayerData data;
            if (args.length > 0) {
                data = database.getCachedOrDb(Strings.parseInt(args[0]));
            } else {
                data = database.getCached(player.uuid());
            }


            if (data == null) {
                bundle.send(player, "error-player-not-found", args());
                return;
            }

            bundle.infoMessage(player, "commands-stats-content", args(
                    "nickname", data.nickname,
                    "pid", data.pid,
                    "totalPlayTime", data.totalPlayTime,
                    "hexedRankTag", data.hexedRank().tag,
                    "hexedRankName", data.hexedRank().name(),
                    "pvpRating", data.pvpRating));
        });

        register("lb", (args, player) -> {
            var data = database.getCached(player.uuid());

            data.leaderboard = !data.leaderboard;

            bundle.send(player, "commands-lb-success", args("leaderboardEnabled", String.valueOf(data.leaderboard)));
            data.save();
        });

        register("tr", (args, player) -> {
            var data = database.getCached(player.uuid());

            switch (args[0].toLowerCase()) {
                case "off" -> {
                    data.translatorLanguage = "off";
                    bundle.send(player, "commands-tr-off", args());
                }
                case "auto" -> {
                    var lang = findTranslatorLanguage(player.locale);
                    data.translatorLanguage = lang == null ? "en" : lang;
                }
                default -> {
                    var lang = findTranslatorLanguage(args[0]);
                    if (lang == null) {
                        bundle.send(player, "commands-tr-not-found", args());
                        return;
                    }

                    data.translatorLanguage = lang;
                }
            }
            bundle.send(player, "commands-tr-success",
                    args("translatorLanguage", translatorLanguages.get(data.translatorLanguage)));
            data.save();
        });

        register("maps", (args, player) -> {
            if (args.length == 1 && !Strings.canParseInt(args[0])) {
                bundle.send(player, "commands-maps-page-must-number", args());
                return;
            }

            Seq<Map> list = Utils.getAvailableMaps();
            int lines = 8;
            int page = args.length == 1 ? Strings.parseInt(args[0]) : 1;

            int pageCount = list.size / lines + (list.size % lines == 0 ? 0 : 1);

            if (page < 1 || page > pageCount) {
                bundle.send(player, "error.page-between", args("pageCount", pageCount));
                return;
            }

            StringBuilder builder = new StringBuilder(
                    bundle.format(bundle.locale(player), "commands-maps-start-content", args(
                            "mapName", Vars.state.map.name(),
                            "page", page,
                            "pageCount", pageCount)));

            int startIndex = (page - 1) * lines;
            int endIndex = Math.min(startIndex + lines, list.size);

            for (int i = startIndex; i < endIndex; i++) {
                Map map = list.get(i);
                builder.append(
                        bundle.format(bundle.locale(player), "commands-maps-content", args(
                                "index", i + 1,
                                "mapName", map.name(),
                                "mapWidth", map.width,
                                "mapHeight", map.height,
                                "mapAuthor", map.author())));
            }

            player.sendMessage(builder.toString());
        });

        register("votekick", (args, player) -> {
            if (voteKick != null) {
                bundle.send(player, "error-vote-in-progress", args());
                return;
            }

            PlayerData data = database.getCached(player.uuid());

            if (data.totalPlayTime < votekickPlayTime) {
                bundle.send(player, "error-votekick-total-playtime", args(
                        "votekickPlayTime", votekickPlayTime));
                return;
            }

            Player found = Find.player(args[0]);
            if (found == null) {
                bundle.send(player, "error-player-not-found", args());
                return;
            }

            if (found.admin) {
                player.kick(bundle.format(bundle.locale(player), "error-player-admin", Collections.emptyMap()), 5 * 60 * 1000);
            }

            if (found.team() != player.team()) {
                bundle.send(player, "error-player-not-teammate", args());
                return;
            }

            voteKick = new VoteKick(player, found, args[1]);
            voteKick.vote(player, 1);
        });

        register("vote", (args, player) -> {
            if (voteKick == null) {
                bundle.send(player, "error-no-voting", args());
                return;
            }

            if (voteKick.voted.containsKey(player.id)) {
                bundle.send(player, "error-already-voted", args());
                return;
            }

            if (voteKick.target == player) {
                bundle.send(player, "error-vote-yourself", args());
                return;
            }

            int sign = voteChoice(args[0]);
            if (sign == 0) {
                bundle.send(player, "commands-vote-vote-with", args());
                return;
            }

            voteKick.vote(player, sign);
        });
        if (config.isMiniHexed()) {
            handler.removeCommand("history");
            handler.removeCommand("votekick");
            handler.removeCommand("vote");
            handler.removeCommand("rtv");

            register("spectate", (args, player) -> killTeam(player.team()));

            register("rank", (args, player) -> {
                var target = args.length > 0 ? Find.player(args[0]) : player;

                if (target == null) {
                    bundle.send(player, "error-player-not-found", args());
                    return;
                }

                var data = database.getCached(target.uuid());
                var rank = data.hexedRank();

                bundle.infoMessage(player, "commands-rank-content", args(
                        "nickname", target.name,
                        "rankTag", rank.tag,
                        "rankName", bundle.format(bundle.locale(player), "hexed-ranks-" + rank.name(), args()),
                        "points", data.hexedPoints,
                        "requiredPoints", rank.next.requirements.wins()));
            });

            register("ranks", (args, player) -> {
                var builder = new StringBuilder();

                for (HexedRanks.HexedRank rank : HexedRanks.HexedRank.values()) {
                    builder.append(bundle.format(bundle.locale(player), "commands-ranks-content", args(
                            "rankTag", rank.tag,
                            "rankName", bundle.format(bundle.locale(player), "hexed-ranks-" + rank.name(), args()),
                            "requiredPoints", rank.requirements == null ? 0 : rank.requirements.wins())))
                        .append("\n");
                }
                builder.append(bundle.format(bundle.locale(player), "commands-ranks-footer", args()));

                Call.infoMessage(player.con, builder.toString());
            });

            register("top", (args, player) -> {
                Seq<PlayerData> leaders = database.getPlayerDatas().getLeaders("hexedRank", "hexedPoints");

                var builder = new StringBuilder();
                if (leaders.isEmpty()) {
                    builder.append(bundle.format(bundle.locale(player), "empty", args()));
                } else for (int i = 0; i < leaders.size; i++) {
                    var data = leaders.get(i);

                    builder.append(bundle.format(bundle.locale(player), "commands-top-hexed-content", args(
                                    "index", i + 1,
                                    "nickname", data.nickname,
                                    "rankName", bundle.format(bundle.locale(player), "hexed-ranks-" + data.hexedRank().name(), args()),
                                    "points", data.hexedPoints)))
                            .append("\n");
                }
                player.sendMessage(builder.toString());
            });

            register("ai", (args, player) -> {
                HexMember member = members.get(player.uuid());

                if (player.team() == Team.derelict || member.team == Team.derelict) {
                    bundle.send(player, "error-spectator", args());
                    return;
                }

                switch (args[0]) {
                    case "attack", "a" -> member.setUnitState(Utils.UnitState.ATTACK);
                    case "idle", "i" -> member.setUnitState(Utils.UnitState.IDLE);
                    default -> {
                        bundle.send(player, "commands-ai-usage", args());
                        return;
                    }
                }

                bundle.send(player, "success", args());
            });
        }

        if (config.isMiniPvP()) {
            register("top", (args, player) -> {
                Seq<PlayerData> leaders = database.getPlayerDatas().getLeaders("pvpRating");

                var builder = new StringBuilder();
                if (leaders.isEmpty()) {
                    builder.append(bundle.format(bundle.locale(player), "empty", args()));
                } else for (int i = 0; i < leaders.size; i++) {
                    var data = leaders.get(i);

                    builder.append(bundle.format(bundle.locale(player), "commands-top-pvp-content", args(
                                    "index", i + 1,
                                    "nickname", data.nickname,
                                    "rating", data.pvpRating)))
                            .append("\n");
                }
                player.sendMessage(builder.toString());
            });
        }

        register("ban", true, (args, player) -> {
            var id = Strings.parseInt(args[0]);

            if (id < 1) {
                bundle.send(player, "error-invalid-id", args());
                return;
            }

            var target = database.getPlayerDatas().getById(id);

            if (target == null) {
                bundle.send(player, "error-player-not-found", args());
                return;
            }

            Instant date = Utils.parsePeriod(args[1], TimeUnit.DAYS);

            if (date == null) {
                bundle.send(player, "error-wrong-period-format", args());
                return;
            }

            Date unbanDate = new Date(Time.millis() + date.toEpochMilli());
            var info = netServer.admins.getInfoOptional(target.uuid);
            String ip = info != null ? info.lastIP : null;

            NetSock.post(new SocketEvents.KickBannedPlayer(target.uuid, ip));

            BanData result = BanData.builder()
                    .name(target.nickname)
                    .uuid(target.uuid)
                    .ip(ip)
                    .adminName(player.name)
                    .reason(args.length > 2 ? args[2] : "Not Specified")
                    .expireDate(unbanDate)
                    .build();

            NetSock.post(result);
            result.save();

            bundle.send(player, "commands-ban-success", args(
                    "nickname", target.nickname
            ));
        });

        register("unban", true, (args, player) -> {
            var id = Strings.parseInt(args[0]);

            if (id < 1) {
                bundle.send(player, "error-invalid-id", args());
                return;
            }

            var target = database.getPlayerDatas().getById(id);

            if (target == null) {
                bundle.send(player, "error-player-not-found", args());
                return;
            }

            database.getBanDatas().delete(target.uuid, null);
            bundle.send(player, "commands-unban-success", args(
                    "nickname", target.nickname,
                    "pid", target.pid
            ));
        });

        register("mute", true, (args, player) -> {
            var id = Strings.parseInt(args[0]);

            if (id < 1) {
                bundle.send(player, "error-invalid-id", args());
                return;
            }
            var target = database.getCachedOrDb(id);

            if (target == null) {
                bundle.send(player, "error-player-not-found", args());
                return;
            }

            if (target.adminData() != null && target.adminData().adminConfirmed) {
                bundle.send(player, "error-access-denied", args());
                return;
            }

            Instant date = Utils.parsePeriod(args[1], TimeUnit.HOURS);

            if (date == null) {
                bundle.send(player, "error-wrong-period-format", args());
                return;
            }

            String reason = args.length > 2 ? args[2] : "Not Specified";
            database.muteDatas.save(MuteData.builder()
                    .uuid(target.uuid)
                    .name(target.nickname)
                    .adminName(player.name)
                    .reason(reason)
                    .expireDate(new Date(Time.millis() + date.toEpochMilli()))
                    .build());

            target.save();
            bundle.send(player, "commands-mute-success", args(
                    "nickname", target.nickname));
            Duration duration = Duration.ofMillis(date.toEpochMilli());

            Optional.ofNullable(Find.playerByUuid(target.uuid)).ifPresent(p ->
                    bundle.send(p, "you-are-muted-by", args(
                            "adminName", player.coloredName(),
                            "reason", reason,
                            "remainMinutes", duration.toMinutes(),
                            "remainSeconds", duration.toSecondsPart())));
        });

        register("unmute", true, (args, player) -> {
            var id = Strings.parseInt(args[0]);

            if (id < 1) {
                bundle.send(player, "error-invalid-id", args());
                return;
            }

            var target = database.getCachedOrDb(id);

            if (target == null) {
                bundle.send(player, "error-player-not-found", args());
                return;
            }

            database.muteDatas.delete(target.uuid);

            bundle.send(player, "commands-unmute-success", args("nickname", target.nickname));
        });
        register("artv", true, (args, player) -> {
            var map = args.length > 0 ? Utils.findMap(args[0]) : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);

            if (map == null) {
                bundle.send(player, "error-map-not-found", args());
                return;
            }

            Timer.schedule(() -> reloadWorld(() -> world.loadMap(map, map.applyRules(Gamemode.valueOf(Core.settings.getString("lastServerMode"))))), mapLoadDelay);

            bundle.send("commands-artv-map-skipped", args("nickname", player.coloredName()));
        });

        register("login", (args, player) -> {
            var password = args[0];

            if (password.length() < 4) {
                bundle.send(player, "error-admin-password-too-short", args());
                return;
            }

            PlayerData data = database.getCached(player.uuid());
            AdminData adminData = data.adminData();
            if (adminData.password.isEmpty()) {
                bundle.send(player, "commands-login-admin-password-created", args());
                adminData.hashPassword(password);
                adminData.save();
            }

            if (adminData.verifyPassword(password)) {
                if (adminData.adminConfirmed) {
                    player.admin(true);
                    netServer.admins.adminPlayer(player.uuid(), player.getInfo().adminUsid);
                    bundle.send(player, "commands-login-success", args());
                } else {
                    bundle.send(player, "commands-login-request-approval-discord", args());
                    NetSock.post(new SocketEvents.AdminRequestEvent(data.pid, config.server));
                }
                return;
            }

            bundle.send(player, "error.wrong-admin-password", args());
        });
    }

    public static void register(String name, CommandHandler.CommandRunner<Player> runner) {
        register(name, false, runner);
    }

    public static void register(String name, boolean admin, CommandHandler.CommandRunner<Player> runner) {
        if (config.disabledCommands.contains(name)) return;

        netServer.clientCommands.<Player>register(name,
                bundle.format(bundle.defaultLocale, "commands-" + name + "-params", "", args()),
                bundle.format(bundle.defaultLocale, "commands-" + name + "-description", "", args()),
                (args, player) -> {
                    if (admin && !player.admin) {
                        bundle.send(player, "error-access-denied", args());
                        return;
                    }

                    runner.accept(args, player);
                });
    }
}
