package org.xcore.plugin.listeners;

import arc.util.Timer;
import mindustry.gen.Groups;
import mindustry.net.Packets;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.modules.discord.Bot;
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
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.database;
import static useful.Bundle.send;

public class SocketEvents {
    public static void init() {
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
    }

    public record MessageEvent(String authorName, String message, String server) {
    }

    public record ServerActionEvent(String message, String server) {
    }

    public record PlayerJoinLeaveEvent(String playerName, String server, Boolean join) {
    }

    public record DiscordMessageEvent(String authorName, String message, String server) {
    }

    public record AdminRequestEvent(String uuid, String name, String server) {
    }

    public record AdminRequestConfirmEvent(String uuid, String server) {
    }

    public record KickBannedPlayer(String uuid, String ip) {
    }

    public record SyncPlayerData(PlayerData data) {
    }
}