package org.xcore.plugin.command.controller.server;

import arc.struct.ObjectSet;
import arc.util.Log;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.net.Administration.PlayerInfo;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.service.PlayerSessionService;

import static mindustry.Vars.netServer;

@Singleton
public class ServerInformationController implements CloudServerController {

    private final PlayerSessionService playerSessionService;

    @Inject
    public ServerInformationController(PlayerSessionService playerSessionService) {
        this.playerSessionService = playerSessionService;
    }

    @Command("players")
    @CommandDescription("Lists all online players with their internal IDs and IPs.")
    public void players(XCoreSender sender) {
        if (Groups.player.isEmpty()) {
            Log.info("No players online.");
            return;
        }
        Log.info("Online players (@):", Groups.player.size());
        Groups.player.each(p -> {
            PlayerInfo i = p.getInfo();
            var d = playerSessionService.get(p.uuid());
            Log.info(" @&lm @ #@ / IP: @", i.admin ? "&r[A]&c" : "&b[P]&c", i.plainLastName(), d.pid, i.lastIP);
        });
    }

    @Command("info <query>")
    @CommandDescription("Finds detailed player info by Name, IP, UUID, or #ID.")
    public void info(XCoreSender sender, @Argument("query") String q) {

        ObjectSet<PlayerInfo> set;
        if (q.startsWith("#")) {
            var d = playerSessionService.getOrLoadFromDb(Strings.parseInt(q.substring(1)));
            set = (d != null) ? ObjectSet.with(netServer.admins.getInfoOptional(d.uuid)) : new ObjectSet<>();
        } else {
            set = netServer.admins.findByName(q);
        }

        if (set.isEmpty()) {
            Log.info("Nobody found.");
            return;
        }
        set.each(i -> {
            Log.info("[@] Trace for '@' / UUID: @", set.size, i.plainLastName(), i.id);
            Log.info("  Names: @ | IPs: @ | Joined: @", i.names, i.ips, i.timesJoined);
        });
    }
}