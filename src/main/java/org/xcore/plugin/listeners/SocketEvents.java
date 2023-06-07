package org.xcore.plugin.listeners;

import arc.files.Fi;
import arc.util.Timer;
import mindustry.gen.Groups;
import mindustry.net.Packets;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.commands.DiscordCommands;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.database;
import static useful.Bundle.send;

public class SocketEvents {
    public static void init() {
        if (SockCommunicator.isSocketServer()) {
            Bot.connect();
            DiscordCommands.init();

            SockCommunicator.onEvent(MessageEvent.class, e ->
                    Bot.sendMessageEvent(e.authorName(), e.message(), e.server()));
            SockCommunicator.onEvent(ServerActionEvent.class, e ->
                    Bot.getServerLogChannel(e.server()).createMessage(e.message()).subscribe());
            SockCommunicator.onEvent(PlayerJoinLeaveEvent.class, e ->
                    Bot.sendJoinLeaveEventMessage(e.playerName(), e.server(), e.join()));
            SockCommunicator.onEvent(AdminRequestEvent.class, e ->
                    Bot.sendAdminRequestEvent(e.pid(), e.server()));
            SockCommunicator.onEvent(BanData.class, Bot::sendBan);

            Timer.schedule(() -> {
                var datas = database.getPlayerDataExecutor().getAdmins();
                Bot.sendAdminPlayTimeMessage(datas);

                for (PlayerData data : datas) {
                    data.playTime = 0;
                    database.getPlayerDataExecutor().setPlayerData(data);
                    SockCommunicator.sendEvent(new SocketEvents.SyncPlayerData(data));
                }
            }, Duration.between(LocalDateTime.now(), LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT)).toSeconds(), 60 * 60 * 24);

        } else {
            SockCommunicator.onEvent(DiscordMessageEvent.class, e -> {
                if (!e.server().equals(config.server)) return;

                XcorePlugin.sendMessageFromDiscord(e.authorName(), e.message());
            });
        }

        SockCommunicator.onEvent(AdminRequestConfirmEvent.class, e -> {
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

        SockCommunicator.onEvent(KickBannedPlayer.class, e ->
                Groups.player.each(p -> p.uuid().equals(e.uuid()) || p.ip().equals(e.ip()), p -> p.kick(Packets.KickReason.banned)));

        SockCommunicator.onEvent(SyncPlayerData.class, e -> {
            if (database.cachedPlayerData.containsKey(e.data().uuid)) database.setCached(e.data());
        });

        SockCommunicator.onEvent(LoadMaps.class, e -> {
            if (!config.server.equals(e.server)) return;

            for (String file : e.files) {
                new Fi(file).moveTo(customMapDirectory);
            }

            maps.reload();
        });

        SockCommunicator.sendEvent(new ServerActionEvent("Server loaded", config.server));
    }

    public record MessageEvent(String authorName, String message, String server) {
    }

    public record ServerActionEvent(String message, String server) {
    }

    public record PlayerJoinLeaveEvent(String playerName, String server, Boolean join) {
    }

    public record DiscordMessageEvent(String authorName, String message, String server) {
    }

    public record AdminRequestEvent(int pid, String server) {
    }

    public record AdminRequestConfirmEvent(String uuid, String server) {
    }

    public record KickBannedPlayer(String uuid, String ip) {
    }

    public record SyncPlayerData(PlayerData data) {
    }

    public record LoadMaps(String[] files, String server) {

    }
}