package org.xcore.plugin.modules;

import arc.util.Time;
import arc.util.serialization.JsonValue;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.models.IPBanData;
import org.xcore.plugin.utils.models.UUIDBanData;

import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.*;

public class AdminModIntegration {
    public static void init() {
        netServer.addPacketHandler("take_ban_data", (player, content) -> {
            if (!player.admin) return;

            JsonValue json = reader.parse(content);

            String uuid = json.get("uuid").asString();
            String ip = json.get("ip").asString();
            String name = json.get("name").asString();
            String reason = json.get("reason").asString();

            boolean global = json.get("global").asBoolean();
            short duration = json.get("duration").asShort();

            if (uuid == null || uuid.isBlank()) {
                player.sendMessage("UUID cannot be blank.");
                return;
            }

            if (reason == null || reason.isBlank()) {
                reason = "<unknown>";
            }

            if (duration == 0) {
                duration = 1000;
            }

            netServer.admins.unbanPlayerID(uuid);
            if (global) {
                var ban = IPBanData.builder()
                        .ip(ip)
                        .name(name)
                        .adminName(player.name)
                        .reason(reason)
                        .unbanDate(Time.millis() + TimeUnit.DAYS.toMillis(duration))
                        .build();

                SockCommunicator.sendEvent(ban);
                database.getBanDataExecutor().saveIPBan(ban);
            } else {
                var ban = UUIDBanData.builder()
                        .name(name)
                        .uuid(uuid)
                        .adminName(player.name)
                        .server(config.server)
                        .reason(reason)
                        .unbanDate(Time.millis() + TimeUnit.DAYS.toMillis(duration))
                        .build();
                SockCommunicator.sendEvent(ban);
                database.getBanDataExecutor().saveUUIDBan(ban);
            }
        });

        netServer.addPacketHandler("adm_mod_end", (player, content) -> {
            var data = database.getCached(player.uuid());
            if (data == null || data.adminMod) return;

            data.adminMod = true;
            database.setCached(data);
        });
    }
}
