package org.xcore.plugin.commands;

import arc.Core;
import arc.math.Mathf;
import arc.struct.*;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.ui.Menus;

import org.xcore.plugin.PluginVars;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.hexed.HexMember;
import org.xcore.plugin.modules.hexed.HexedRanks;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.modules.votes.VoteRtv;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.modules.hexed.MiniHexed.killTeam;
import static org.xcore.plugin.modules.hexed.MiniHexed.members;
import static org.xcore.plugin.utils.Find.findTranslatorLanguage;
import static org.xcore.plugin.utils.Security.*;
import static org.xcore.plugin.utils.Utils.reloadWorld;
import static org.xcore.plugin.utils.Utils.voteChoice;

public class ClientCommands {
    private int infoMenuId;
    
    public static void register(CommandHandler handler) {
        int infoMenuId = Menus.registerMenu((player, option) -> {
            switch (option) {
                case 0:
                    Call.openURI(player.con, discordUrl);
                    break;
                case 1:
                    Call.openURI(player.con, githubUrl);
                    break;
                case 2:
                    Call.openURI(player.con, donatelloUrl);
                    break;
                case 3:
                    Call.openURI(player.con, discordRedVSBlueUrl);
                    break;
                default:
                    break;
            }
        });

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

        CommandHandler.CommandRunner<Player> likeRunner = (args, player) -> {
            mindustry.maps.Map map = Vars.state.map;
            if (map == null) return;

            String mapName = map.plainName();
            MapData mData = database.mapDatas.get(mapName, map.author(), Vars.state.rules.mode().name());
            String mapIdStr = String.valueOf(mData.id);

            PlayerData pData = database.getCached(player.uuid());
            Boolean previousVote = pData.mapVotes.get(mapIdStr);

            if (Boolean.TRUE.equals(previousVote)) {
                bundle.send(player, "error-already-voted", args());
                return;
            }

            if (previousVote == null) {
                mData.reputation += 1;
                mData.popularity += 2.0;
                bundle.send(player, "commands-like-success", args());
            } else {
                mData.reputation += 2;
                mData.popularity += 4.0;
                bundle.send(player, "commands-like-changed", args());
            }

            pData.mapVotes.put(mapIdStr, true);
            pData.save();
            mData.save();
        };

        CommandHandler.CommandRunner<Player> dislikeRunner = (args, player) -> {
            mindustry.maps.Map map = Vars.state.map;
            if (map == null) return;

            String mapName = map.plainName();
            MapData mData = database.mapDatas.get(mapName, map.author(), Vars.state.rules.mode().name());
            String mapIdStr = String.valueOf(mData.id);

            PlayerData pData = database.getCached(player.uuid());
            Boolean previousVote = pData.mapVotes.get(mapIdStr);

            if (Boolean.FALSE.equals(previousVote)) {
                bundle.send(player, "error-already-voted", args());
                return;
            }

            if (previousVote == null) {
                mData.reputation -= 1;
                mData.popularity -= 2.0;
                bundle.send(player, "commands-dislike-success", args());
            } else {
                mData.reputation -= 2;
                mData.popularity -= 4.0;
                bundle.send(player, "commands-dislike-changed", args());
            }

            pData.mapVotes.put(mapIdStr, false);
            pData.save();
            mData.save();
        };

        CommandHandler.CommandRunner<Player> mapStatsRunner = (args, player) -> {
            boolean isManualSelection = args.length > 0;

            var map = isManualSelection
                    ? Utils.findMap(args[0])
                    : Vars.state.map;

            if (map == null) {
                bundle.send(player, "error-map-not-found", args());
                return;
            }

            MapData mapData = database.mapDatas.get(map.plainName(), map.author(), Vars.state.rules.mode().name());

            long minMins = mapData.minimumGameTime / 60000;
            long avgMins = mapData.averageGameTime / 60000;
            long maxMins = mapData.maximumGameTime / 60000;

            String lastPlayed;
            long now = System.currentTimeMillis();

            if (mapData.playedTimes == 0) {
                lastPlayed = bundle.format(bundle.locale(player), "never", args());
            } else {
                long diffMins = (now - mapData.lastPlayedTime) / 60000;
                if (diffMins < 1) lastPlayed = "<1m";
                else if (diffMins > 1440) lastPlayed = (diffMins / 1440) + "d";
                else lastPlayed = diffMins + "m";
            }

            var msg = bundle.format(
                bundle.locale(player), "commands-map-stats-content", args(
                            "mapName", mapData.name,
                            "mapAuthor", mapData.author,
                            "mapDescription", map.description().equals("unknown") ? "" : map.description(),
                            "mapWidth", map.width,
                            "mapHeight", map.height,
                            "mapReputation", mapData.reputation,
                            "mapPopularity", String.format("%.1f", mapData.popularity),
                            "mapInterest", String.format("%.1f", mapData.interest),
                            "mapPlayedTimes", mapData.playedTimes,
                            "mapPlayedTimesYear", mapData.playedTimesYear,
                            "mapLastPlayed", lastPlayed,
                            "mapMin", minMins,
                            "mapAvg", avgMins,
                            "mapMax", maxMins
                    ));

            Call.infoMessage(player.con, msg);
        };

        CommandHandler.CommandRunner<Player> infoRunner = (args, player) -> Call.menu(player.con, infoMenuId,
                bundle.format(bundle.locale(player), "commands-info-title", args()),
                bundle.format(bundle.locale(player), "commands-info-text", args("xcoreVersion", xcoreVersion)),
                new String[][]{
                        {"Discord", "GitHub", "Donatello"},
                        {"RedVSBlue"},
                        {bundle.format(bundle.locale(player), "close", args())}
                }
        );

        register("like", likeRunner);
        registerAlias("like", "+", likeRunner);

        register("dislike", dislikeRunner);
        registerAlias("dislike", "-", dislikeRunner);

        register("map-stats", mapStatsRunner);
        registerAlias("map-stats", "map", mapStatsRunner);

        register("information", infoRunner);
        registerAlias("information", "info", infoRunner);


        register("discord", (args, player) -> Call.openURI(player.con, discordUrl));

        register("t", withMuteCheck((args, player) -> {
            Groups.player.each(other -> other.team() == player.team(),
                    p -> p.sendMessage(
                            bundle.format(
                                    bundle.locale(p),
                                    "commands-t-chat", args(
                                            "color", player.team().color,
                                            "name", player.coloredName(),
                                            "message", args[0]
                                    )),
                            player
                    )
            );
        }));

        register("g", withMuteCheck(
                withPlayTimeCheck(PluginVars.globalChatPlayTime,
                        "error-globalchat-total-playtime", (args, player) -> {

            NetSock.post(new SocketEvents.GlobalChatEvent(player.coloredName(), args[0], config.server));
            NetSock.post(new SocketEvents.MessageEvent(player.plainName(), "[" + config.server + "] " + args[0].replace("`", "*"), "global"));
        })));

        register("rtv", (args, player) -> {
            if (vote != null) {
                bundle.send(player, "error-vote-in-progress", args());
                return;
            }

            boolean isManualSelection = args.length > 0;

            var map = isManualSelection
                    ? Utils.findMap(args[0])
                    : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);

            if (map == null) {
                bundle.send(player, "error-map-not-found", args());
                return;
            }

            vote = new VoteRtv(map, isManualSelection);
            vote.vote(player, 1);
        });

        register("stats", (args, player) -> {
            PlayerData data = args.length > 0 ? database.getCachedOrDb(Strings.parseInt(args[0])) : database.getCached(player.uuid());

            if (data == null) {
                bundle.send(player, "error-player-not-found", args());
                return;
            }

            var msg = bundle.format(
                bundle.locale(player),
                "commands-stats-content", args(
                    "nickname", data.nickname,
                    "pid", data.pid,
                    "totalPlayTime", data.totalPlayTime,
                    "hexedRankTag", data.hexedRank().tag,
                    "hexedRankName", data.hexedRank().name(),
                    "pvpRating", data.pvpRating
                )
            );

            Call.infoMessage(player.con, msg);
        });

        register("map-stats", (args, player) -> {
            boolean isManualSelection = args.length > 0;

            var map = isManualSelection
                    ? Utils.findMap(args[0])
                    : Vars.state.map;

            if (map == null) {
                bundle.send(player, "error-map-not-found", args());
                return;
            }

            MapData mapData = database.mapDatas.get(map.plainName(), map.author(), Vars.state.rules.mode().name());

            long minMins = mapData.minimumGameTime / 60000;
            long avgMins = mapData.averageGameTime / 60000;
            long maxMins = mapData.maximumGameTime / 60000;

            String lastPlayed;
            long now = System.currentTimeMillis();

            if (mapData.playedTimes == 0) {
                lastPlayed = bundle.format(bundle.locale(player), "never", args());
            } else {
                long diffMins = (now - mapData.lastPlayedTime) / 60000;
                if (diffMins < 1) lastPlayed = "<1";
                else if (diffMins > 1440) lastPlayed = (diffMins / 1440) + "d";
                else lastPlayed = diffMins + "m";
            }

            var msg = bundle.format(
                bundle.locale(player), "commands-map-stats-content", args(
                            "mapName", mapData.name,
                            "mapAuthor", mapData.author,
                            "mapDescription", map.description(),
                            "mapWidth", map.width,
                            "mapHeight", map.height,
                            "mapReputation", mapData.reputation,
                            "mapPopularity", mapData.popularity,
                            "mapInterest", mapData.interest,
                            "mapPlayedTimes", mapData.playedTimes,
                            "mapPlayedTimesYear", mapData.playedTimesYear,
                            "mapLastPlayed", lastPlayed,
                            "mapMin", minMins,
                            "mapAvg", avgMins,
                            "mapMax", maxMins
                    ));

            Call.infoMessage(player.con, msg);
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

            bundle.send(
                player,
                "commands-tr-success",
                args("translatorLanguage", translatorLanguages.get(data.translatorLanguage))
            );

            data.save();
        });

        register("maps", (args, player) -> {
            if (args.length == 1 && !Strings.canParseInt(args[0])) {
                bundle.send(player, "commands-maps-page-must-number", args());
                return;
            }

            Seq<Map> list = Utils.getAvailableMaps();
            int lines = 10;
            int page = args.length == 1 ? Strings.parseInt(args[0]) : 1;
            int pageCount = Mathf.ceil((float) list.size / lines);

            if (page < 1 || page > pageCount) {
                bundle.send(player, "error-page-between", args("pageCount", pageCount));
                return;
            }

            StringBuilder builder = new StringBuilder(
                    bundle.format(bundle.locale(player), "commands-maps-start-content", args(
                            "mapName", Vars.state.map.name(),
                            "page", page,
                            "pageCount", pageCount)));

            int startIndex = (page - 1) * lines;
            int endIndex = Math.min(startIndex + lines, list.size);

            long now = System.currentTimeMillis();

            for (int i = startIndex; i < endIndex; i++) {
                Map map = list.get(i);
                MapData mapData = database.mapDatas.get(map.plainName(), map.author(), Vars.state.rules.mode().name());

                String lastPlayed;
                if (mapData.playedTimes == 0) {
                    lastPlayed = bundle.format(bundle.locale(player), "never", args());
                } else {
                    long diffMins = (now - mapData.lastPlayedTime) / 60000;
                    if (diffMins < 1) lastPlayed = "<1";
                    else if (diffMins > 1440) lastPlayed = (diffMins / 1440) + "d";
                    else lastPlayed = diffMins + "m";
                }

                builder.append(
                        bundle.format(bundle.locale(player), "commands-maps-content", args(
                                "index", i + 1,
                                "mapName", mapData.name,
                                "mapAuthor", mapData.author,
                                "mapWidth", map.width,
                                "mapHeight", map.height,
                                "mapReputation", mapData.reputation,
                                "mapLastPlayed", lastPlayed
                        )));
            }

            player.sendMessage(builder.toString());
        });

        register("votekick", withPlayTimeCheck(PluginVars.votekickPlayTime,
                "error-votekick-total-playtime", (args, player) -> {
            if (voteKick != null) {
                bundle.send(player, "error-vote-in-progress", args());
                return;
            }

            Player found = Find.player(args[0]);
            if (found == null) {
                bundle.send(player, "error-player-not-found", args());
                return;
            }

            if (found.admin) {
                player.kick(bundle.format(bundle.locale(player), "error-player-admin", args()), 5 * 60 * 1000);
            }

            if (found.team() != player.team()) {
                bundle.send(player, "error-player-not-teammate", args());
                return;
            }

            voteKick = new VoteKick(player, found, args[1]);
            voteKick.vote(player, 1);
        }));

        register("vote", (args, player) -> {
            if (voteKick == null) {
                bundle.send(player, "error-no-voting", args());
                return;
            }

            if (Utils.stripFooCharacters(args[0].toLowerCase()).equals("c")) {
                if (!player.admin) {
                    bundle.send(player, "error-access-denied", args());
                } else {
                    voteKick.cancelByAdmin(player);
                }
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

                bundle.infoMessage(
                    player,
                    "commands-rank-content", args(
                        "nickname", target.name,
                        "rankTag", rank.tag,
                        "rankName", bundle.format(bundle.locale(player), "hexed-ranks-" + rank.name(), args()),
                        "points", data.hexedPoints,
                        "requiredPoints", rank.next.requirements.wins()
                    )
                );
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

                    builder.append(bundle.format(
                        bundle.locale(player),
                            "commands-top-hexed-content", args(
                                "index", i + 1,
                                "nickname", data.nickname,
                                "rankName", bundle.format(bundle.locale(player), "hexed-ranks-" + data.hexedRank().name(), args()),
                                "points", data.hexedPoints)
                    )).append("\n");
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

        register("ban", withAdminCheck((args, player) -> {
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

            Instant period = Utils.parsePeriod(args[1], TimeUnit.DAYS);

            if (period == null) {
                bundle.send(player, "error-wrong-period-format", args());
                return;
            }

            Instant unbanDate = Instant.now().plusMillis(period.toEpochMilli());
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

            bundle.send(
                    player,
                    "commands-ban-success",
                    args("nickname", target.nickname)
            );
        }));

        register("unban", withAdminCheck((args, player) -> {
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
            bundle.send(
                player,
                "commands-unban-success", args(
                    "nickname", target.nickname,
                    "pid", target.pid
                )
            );
        }));

        register("mute", withAdminCheck((args, player) -> {
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

            Instant period = Utils.parsePeriod(args[1], TimeUnit.HOURS);

            if (period == null) {
                bundle.send(player, "error-wrong-period-format", args());
                return;
            }

            String reason = args.length > 2 ? args[2] : "Not Specified";
            Instant expireDate = Instant.now().plusMillis(period.toEpochMilli());

            database.muteDatas.save(MuteData.builder()
                    .uuid(target.uuid)
                    .name(target.nickname)
                    .adminName(player.name)
                    .reason(reason)
                    .expireDate(expireDate)
                    .build());

            target.save();
            bundle.send(player, "commands-mute-success", args(
                    "nickname", target.nickname));
            Duration duration = Duration.ofMillis(period.toEpochMilli());

            Optional.ofNullable(Find.playerByUuid(target.uuid)).ifPresent(p ->
                    bundle.send(
                            p, "you-are-muted-by", args(
                                    "adminName", player.coloredName(),
                                    "reason", reason,
                                    "remainMinutes", duration.toMinutes(),
                                    "remainSeconds", duration.toSecondsPart()
                            )
                    )
            );
        }));

        register("unmute", withAdminCheck((args, player) -> {
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
        }));

        register("artv", withAdminCheck((args, player) -> {
            var map = args.length > 0 ? Utils.findMap(args[0]) : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);

            if (map == null) {
                bundle.send(player, "error-map-not-found", args());
                return;
            }

            Timer.schedule(() -> reloadWorld(() -> world.loadMap(map, map.applyRules(Gamemode.valueOf(Core.settings.getString("lastServerMode"))))), mapLoadDelay);

            bundle.send("commands-artv-map-skipped", args("nickname", player.coloredName()));
        }));

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

            bundle.send(player, "error-wrong-admin-password", args());
        });

        register("logout", (args, player) ->  {
            if (player.admin) {
                player.admin(false);
                netServer.admins.unAdminPlayer(player.uuid());

                bundle.send(player, "commands-logout-successful", args());
            }
        });
    }

    public static void register(String name, CommandHandler.CommandRunner<Player> runner) {
        registerAlias(name, name, runner);
    }

    public static void registerAlias(String originalName, String alias, CommandHandler.CommandRunner<Player> runner) {
        if (config.disabledCommands.contains(alias)) return;

        netServer.clientCommands.<Player>register(
            alias,
            bundle.format(bundle.defaultLocale, "commands-" + originalName + "-params", "", args()),
            bundle.format(bundle.defaultLocale, "commands-" + originalName + "-description", "", args()),
            runner);
    }
}
