package org.xcore.plugin.modules;

import arc.util.Log;
import arc.util.Time;
import mindustry.gen.Call;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.BanRequestData;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;

import static org.xcore.plugin.PluginVars.*;

import static useful.Bundle.format;
import static useful.Bundle.send;

public class AdminModIntegration {
    public static void init() {
        netServer.addPacketHandler("take_ban_data", (player, content) -> {
            if (!player.admin) return;

            BanRequestData req = rawGson.fromJson(content, BanRequestData.class);

            Instant date = Utils.parsePeriod(req.duration, TimeUnit.DAYS);

            if (req.uuid == null || req.uuid.isBlank()) {
                player.sendMessage("UUID cannot be blank.");
                return;
            }

            if (date == null) {
                send(player, "error.wrong-period-format", format("days", player));
                Call.clientPacketReliable(player.con, "give_ban_data", content);
                return;
            }

            netServer.admins.unbanPlayerID(req.uuid);

            var ban = BanData.builder()
                    .name(req.name)
                    .uuid(req.uuid)
                    .ip(req.ip)
                    .adminName(player.name)
                    .reason(req.reason)
                    .unbanDate(new Date(Time.millis() + date.toEpochMilli()))
                    .build();
            NetSock.post(ban);
            ban.save();
        });
        netServer.addPacketHandler("cancel_ban_data", (player, content) -> {
            if (!player.admin) return;

            BanRequestData req = rawGson.fromJson(content, BanRequestData.class);

            if (req.uuid == null || req.uuid.isBlank()) {
                player.sendMessage("UUID cannot be blank.");
                return;
            }

            netServer.admins.unbanPlayerID(req.uuid);
        });

        netServer.addPacketHandler("adm_mod_end", (player, content) -> {
            var data = database.getCached(player.uuid());

            if (data == null || data.adminModVersion != null) return;
            Log.info("Player @ joined with the Admin mod version '@'", player.plainName(), content);

            if (content.isBlank() || content.isEmpty()) {
                player.con.kick("Update Admin Mod", 0);
                return;
            }
            data.adminModVersion = content;
        });
    }
}
