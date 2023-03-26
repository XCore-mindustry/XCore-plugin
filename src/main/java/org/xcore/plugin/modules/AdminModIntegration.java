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
import org.xcore.plugin.utils.models.BanData;

import java.util.concurrent.TimeUnit;

import static mindustry.Vars.*;
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
            boolean skipToDiscord = json.get("skip_to_discord").asBoolean();
            short duration = json.get("duration").asShort();

            if (uuid == null || uuid.isBlank()) {
                player.sendMessage("UUID cannot be blank.");
                return;
            }

            if (reason == null || reason.isBlank()) {
                reason = "<unknown>";
            }

            if (skipToDiscord) {
                BanData ban = BanData.builder()
                        .uuid(uuid)
                        .name(name)
                        .adminName(player.name)
                        .server(global ? "global" : config.server)
                        .full(false)
                        .build();
                ban.generateBid();
                JavelinCommunicator.sendEvent(ban);
                return;
            }

            if (duration == 0) {
                return;
            }
            BanData ban = BanData.builder()
                    .uuid(uuid)
                    .ip(global ? ip : null)
                    .name(name)
                    .adminName(player.name)
                    .reason(reason)
                    .server(global ? "global" : config.server)
                    .unbanDate(Time.millis() + TimeUnit.DAYS.toMillis(duration))
                    .build();
            ban.generateBid();

            JavelinCommunicator.sendEvent(ban);
        });

        netServer.addPacketHandler("adm_mod_end", (player, content) -> {
            var data = Database.getCached(player.uuid());
            if (data == null || data.adminMod) return;

            data.adminMod = true;
            Database.setCached(data);
        });

        netServer.addPacketHandler("unban", (player, content) -> {
            var data = Database.getCached(player.uuid());
            if (data == null || !data.consolePanelAccess) return;

            netServer.admins.unbanPlayerID(content);
            Database.unBan(content, null);
        });

        netServer.addPacketHandler("info_request", (player, content) -> {
            var data = Database.getCached(player.uuid());
            if (data == null || !data.consolePanelAccess) return;

            Log.debug("AR " + content);
            if (player.admin) {
                Seq<PlayerInfoPacket> packets = netServer.admins.findByName(content).toSeq().map(i ->
                        new PlayerInfoPacket(i.lastName, i.lastIP, i.id, i.admin, i.names, i.ips));

                packets.each(p -> Call.clientPacketUnreliable(player.con, "info_data", JsonIO.write(p)));
            }
        });

        netServer.addPacketHandler("bans_request", (player, content) -> {
            var data = Database.getCached(player.uuid());
            if (data == null || !data.consolePanelAccess) return;

            Log.debug("BR " + content);
            if (player.admin) {
                Seq<BanInfoPacket> packets = Database.getBanned(false).map(b ->
                        new BanInfoPacket(b.name, b.uuid, b.adminName, b.reason, b.unbanDate));
                packets.each(p -> Call.clientPacketUnreliable(player.con, "ban_data", JsonIO.write(p)));
            }
        });
    }
}
