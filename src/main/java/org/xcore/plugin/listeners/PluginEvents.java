package org.xcore.plugin.listeners;

import arc.Events;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.game.EventType;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.modules.hexed.HexedRanks;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static mindustry.Vars.netServer;
import static mindustry.Vars.state;
import static org.xcore.plugin.PluginVars.*;
import static useful.Bundle.send;

public class PluginEvents {
    public static void init() {
        Events.on(ServerLoadEvent.class, event -> {
            if (SockCommunicator.isSocketServer()) {
                Bot.connect();

                SockCommunicator.onEvent(SocketEvents.MessageEvent.class, e ->
                        Bot.sendMessageEvent(e.authorName(), e.message(), e.server()));
                SockCommunicator.onEvent(SocketEvents.ServerActionEvent.class, e ->
                        Bot.getServerLogChannel(e.server()).createMessage(e.message()).subscribe());
                SockCommunicator.onEvent(SocketEvents.PlayerJoinLeaveEvent.class, e ->
                        Bot.sendJoinLeaveEventMessage(e.playerName(), e.server(), e.join()));
                SockCommunicator.onEvent(SocketEvents.AdminRequestEvent.class, e ->
                        Bot.sendAdminRequestEvent(e.uuid(), e.name(), e.server()));
                SockCommunicator.onEvent(BanData.class, Utils::temporaryBan);

                Timer.schedule(() -> {
                    var datas = database.getPlayerDataExecutor().getPlayersData(netServer.admins.getAdmins());
                    Bot.sendAdminPlayTimeMessage(datas);

                    for (PlayerData data : datas) {
                        data.playTime = 0;
                        database.getPlayerDataExecutor().setPlayerData(data);
                        SockCommunicator.sendEvent(new SocketEvents.SyncPlayerData(data));
                    }
                }, Duration.between(LocalDateTime.now(), LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT)).toSeconds(), 60 * 60 * 24);

            } else {
                SockCommunicator.onEvent(SocketEvents.DiscordMessageEvent.class, e -> {
                    if (!e.server().equals(config.server)) return;

                    XcorePlugin.sendMessageFromDiscord(e.authorName(), e.message());
                });
            }

            SockCommunicator.onEvent(SocketEvents.AdminRequestConfirmEvent.class, e -> {
                if (!e.server().equals(config.server)) return;

                var info = Find.playerInfo(e.uuid());
                var player = Find.playerByUuid(e.uuid());

                if (info == null) return;
                if (player != null) {
                    player.admin = true;
                    send(player, "commands.login.confirmed");
                }

                netServer.admins.adminPlayer(e.uuid(), info.adminUsid);
            });

            SockCommunicator.onEvent(SocketEvents.KickBannedPlayer.class, e ->
                    Groups.player.each(p -> p.uuid().equals(e.uuid()) || p.ip().equals(e.ip()), p -> p.kick(Packets.KickReason.banned)));

            SockCommunicator.onEvent(SocketEvents.SyncPlayerData.class, e -> {
                if (database.cachedPlayerData.containsKey(e.data().uuid)) database.setCached(e.data());
            });

            SockCommunicator.sendEvent(new SocketEvents.ServerActionEvent("Server loaded", config.server));
        });
        Events.on(EventType.PlayerConnect.class, event -> {
            var info = netServer.admins.getInfo(event.player.uuid());
            if (!info.ips.contains(event.player.con.address)) {
                event.player.admin = false;
                netServer.admins.unAdminPlayer(event.player.uuid());
            }
        });
        Events.on(PlayerJoin.class, event -> {
            if (event.player.getInfo().timesJoined < 5)
                Call.openURI(event.player.con, discordUrl);

            var data = database.getPlayerDataExecutor().getPlayerData(event.player).setNickname(event.player.coloredName());
            HexedRanks.updateRank(event.player, data);
            database.setCached(data);

            Call.clientPacketReliable(event.player.con, "adm_mod_begin", "");

            if (data.translatorLanguage.equals("off")) {
                send(event.player, "recommendation.tr");
            }

            SockCommunicator.sendEvent(
                    new SocketEvents.PlayerJoinLeaveEvent(event.player.plainName(), config.server, true));
        });

        Events.on(PlayerLeave.class, event -> {
            Player player = event.player;

            database.removeCached(event.player.uuid());

            if (vote != null) vote.left(event.player);
            if (voteKick != null) voteKick.left(event.player);

            SockCommunicator.sendEvent(
                    new SocketEvents.PlayerJoinLeaveEvent(player.plainName(), config.server, false));
        });

        Events.on(GameOverEvent.class, event -> {
            String message = "Game over!";

            if (state.rules.waves) {
                message = Strings.format(
                        "Game over! Reached wave @ with @ players online on map @.", state.wave, Groups.player.size(), Strings.capitalize(Strings.stripColors(state.map.name())));
            } else if (state.rules.pvp && !config.isMiniHexed()) {
                message = Strings.format(
                        "Game over! Team @ is victorious with @ players online on map @.", event.winner.name, Groups.player.size(), Strings.capitalize(Strings.stripColors(state.map.name())));
            }

            SockCommunicator.sendEvent(
                    new SocketEvents.ServerActionEvent(message, config.server));
        });
    }
}
