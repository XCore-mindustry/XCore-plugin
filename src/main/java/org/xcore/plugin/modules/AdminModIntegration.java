package org.xcore.plugin.modules;

import arc.util.Log;
import arc.util.Time;
import arc.util.Timer;
import arc.util.serialization.JsonValue;
import mindustry.gen.Call;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.database;
import static org.xcore.plugin.PluginVars.reader;
import static useful.Bundle.format;
import static useful.Bundle.send;

public class AdminModIntegration {
    public static void init() {
        netServer.addPacketHandler("take_ban_data", (player, content) -> {
            if (!player.admin) return;

            JsonValue json = reader.parse(content);

            String uuid = json.get("uuid").asString();
            String ip = json.get("ip").asString();
            String name = json.get("name").asString();
            String reason = json.get("reason").asString();

            Instant date = Utils.parsePeriod(json.get("duration").asString(), TimeUnit.DAYS);

            if (uuid == null || uuid.isBlank()) {
                player.sendMessage("UUID cannot be blank.");
                return;
            }

            if (reason == null || reason.isBlank()) {
                reason = "<unknown>";
            }

            if (date == null) {
                send(player, "error.wrong-period-format", format("days", player));
                Timer.schedule(() -> Call.clientPacketReliable(player.con, "give_ban_data", content), 5);
                return;
            }

            netServer.admins.unbanPlayerID(uuid);

            var ban = BanData.builder()
                    .name(name)
                    .uuid(uuid)
                    .ip(ip)
                    .adminName(player.name)
                    .reason(reason)
                    .unbanDate(new Date(Time.millis() + date.toEpochMilli()))
                    .build();
            SockCommunicator.sendEvent(ban);
            ban.save();
        });

        netServer.addPacketHandler("adm_mod_end", (player, content) -> {
            var data = database.getCached(player.uuid());

            if (data == null || data.adminModVersion != null) return;
            Log.info("Admin @ joined with the Admin mod version '@'", player.plainName(), content);

            if (content.isBlank() || content.isEmpty()) {
                player.con.kick("Update Admin Mod", 0);
                return;
            }
            data.adminModVersion = content;
        });
    }
}
