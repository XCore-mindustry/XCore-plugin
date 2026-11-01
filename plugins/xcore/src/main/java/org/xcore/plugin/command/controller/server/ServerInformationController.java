package org.xcore.plugin.command.controller.server;

import arc.struct.ObjectSet;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.net.Administration.PlayerInfo;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.SessionService;

import static mindustry.Vars.netServer;

@Singleton
public class ServerInformationController implements CloudServerController {

    private final SessionService sessionService;

    @Inject
    public ServerInformationController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Command("players")
    @CommandDescription("Lists all online players with their internal IDs, Usernames, and IPs.")
    public void players(XCoreSender sender) {
        if (Groups.player.isEmpty()) {
            Log.info("No players online.");
            return;
        }
        Log.info("Online players (@):", Groups.player.size());
        Groups.player.each(p -> {
            PlayerInfo i = p.getInfo();
            var session = sessionService.get(p.uuid());
            var data = session != null ? session.data : sessionService.getOrLoadFromDb(p.uuid());
            Object pid = data != null ? data.pid : "?";
            String userTag = (data != null && data.username != null && !data.username.isBlank())
                    ? " [@" + data.username + "]"
                    : "";
            Log.info(" @&lm @ #@@ / IP: @", i.admin ? "&r[A]&c" : "&b[P]&c", i.plainLastName(), pid, userTag, i.lastIP);
        });
    }

    @Command("info <query>")
    @CommandDescription("Finds detailed player info by Name, @Username, IP, UUID, or #ID.")
    public void info(XCoreSender sender, @Argument("query") String q) {

        // Спочатку шукаємо за резолвером (username, pid, uuid)
        PlayerData data = sessionService.resolvePlayerData(q);
        PlayerInfo directInfo = (data != null && data.uuid != null) ? netServer.admins.getInfoOptional(data.uuid) : null;

        // Ініціалізуємо set ОДИН раз (тепер він effectively final і лямбда не лається)
        ObjectSet<PlayerInfo> set = (directInfo != null) ? ObjectSet.with(directInfo) : netServer.admins.findByName(q);

        if (set == null || set.isEmpty()) {
            Log.info("Nobody found.");
            return;
        }

        set.each(i -> {
            PlayerData d = sessionService.getOrLoadFromDb(i.id);
            String userTag = (d != null && d.username != null && !d.username.isBlank()) ? " | Username: @" + d.username : "";
            int pid = d != null ? d.pid : -1;

            Log.info("[@] Trace for '@' (#@)@ / UUID: @", set.size, i.plainLastName(), pid, userTag, i.id);
            Log.info("  Names: @ | IPs: @ | Joined: @", i.names, i.ips, i.timesJoined);
        });
    }
}