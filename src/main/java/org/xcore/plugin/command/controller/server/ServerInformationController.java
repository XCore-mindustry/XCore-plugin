package org.xcore.plugin.command.controller.server;

import arc.struct.ObjectSet;
import arc.util.Log;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.net.Administration.PlayerInfo;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ServerContext;
import org.xcore.plugin.database.DatabaseService;

import static mindustry.Vars.netServer;

@Singleton
public class ServerInformationController {

    private final DatabaseService database;

    @Inject
    public ServerInformationController(DatabaseService database) {
        this.database = database;
    }

    @Command(name = "players", description = "List online players with status.")
    public void players(ServerContext ctx) {
        if (Groups.player.isEmpty()) {
            Log.info("No players online.");
            return;
        }
        Log.info("Online players (@):", Groups.player.size());
        Groups.player.each(p -> {
            PlayerInfo i = p.getInfo();
            var d = database.getCached(p.uuid());
            Log.info(" @&lm @ #@ / IP: @", i.admin ? "&r[A]&c" : "&b[P]&c", i.plainLastName(), d.pid, i.lastIP);
        });
    }

    @Command(name = "info", params = "<IP/UUID/#id/name...>", description = "Find detailed player traces.")
    public void info(ServerContext ctx) {
        ObjectSet<PlayerInfo> set;
        String q = ctx.arg(0);
        if (q.startsWith("#")) {
            var d = database.getCachedOrDb(Strings.parseInt(q.substring(1)));
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
