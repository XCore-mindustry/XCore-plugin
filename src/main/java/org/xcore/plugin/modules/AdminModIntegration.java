package org.xcore.plugin.modules;

import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.JsonValue;
import mindustry.gen.Call;
import mindustry.io.JsonIO;
import org.xcore.plugin.modules.packets.BanInfoPacket;
import org.xcore.plugin.modules.packets.PlayerInfoPacket;
import org.xcore.plugin.utils.JavelinCommunicator;
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

            var builder = UUIDBanData.builder()
                    .uuid(uuid)
                    .name(name)
                    .adminName(player.name)
                    .server(global ? "global" : config.server);

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

                JavelinCommunicator.sendEvent(ban);
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
                JavelinCommunicator.sendEvent(ban);
                database.getBanDataExecutor().saveUUIDBan(ban);
            }
        });

        netServer.addPacketHandler("adm_mod_end", (player, content) -> {
            var data = database.getCached(player.uuid());
            if (data == null || data.adminMod) return;

            data.adminMod = true;
            database.setCached(data);
        });

        netServer.addPacketHandler("info_request", (player, content) -> {
            var data = database.getCached(player.uuid());
            if (data == null || !data.consolePanelAccess) return;

            Log.debug("AR " + content);
            if (player.admin) {
                Seq<PlayerInfoPacket> packets = netServer.admins.findByName(content).toSeq().map(i ->
                        new PlayerInfoPacket(i.lastName, i.lastIP, i.id, i.admin, i.names, i.ips));

                packets.each(p -> Call.clientPacketUnreliable(player.con, "info_data", JsonIO.write(p)));
            }
        });

        netServer.addPacketHandler("bans_request", (player, content) -> {
            var data = database.getCached(player.uuid());
            if (data == null || !data.consolePanelAccess) return;

            Log.debug("BR " + content);
            if (player.admin) {
                Seq<BanInfoPacket> packets = database.getBanDataExecutor().getUUIDBanned().map(b ->
                        new BanInfoPacket(b.name, b.uuid, b.adminName, b.reason, b.unbanDate));
                packets.each(p -> Call.clientPacketUnreliable(player.con, "ban_data", JsonIO.write(p)));
            }
        });
    }
}
