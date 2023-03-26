package org.xcore.plugin.modules;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.JsonValue;
import mindustry.gen.Call;
import mindustry.io.JsonIO;
import mindustry.net.Administration;
import org.xcore.plugin.modules.history.History;
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
                    .ip(global ? ip : "")
                    .name(name)
                    .adminName(player.name)
                    .reason(reason)
                    .server(global ? "global" : config.server)
                    .unbanDate(Time.millis() + TimeUnit.DAYS.toMillis(duration))
                    .build();
            ban.generateBid();

            JavelinCommunicator.sendEvent(ban);
        });

        netServer.addPacketHandler("info_request", (player, content) -> {
            Log.debug("AR "+content);
            if (player.admin) {
                ObjectSet<Administration.PlayerInfo> infos = netServer.admins.findByName(content);
                Seq<PlayerInfoPacket> packets = new Seq<>();
                infos.each(i->{
                    packets.add(new PlayerInfoPacket(i.lastName,i.lastIP,i.id,i.admin,i.names,i.ips));
                });
                packets.each(p->{
                    String res = p.generate().toString().replace("\"~","").replace("~\"","").replace("\\t","\"");
                    Log.debug(res);
                    Call.clientPacketUnreliable(player.con, "info_data", res);
                });
            }
        });

        netServer.addPacketHandler("bans_request", (player, content) -> {
            Log.debug("BR "+content);
            if (player.admin) {
                Seq<BanData> bans = Database.getBanned(false);
                Seq<BanInfoPacket> banInfoPackets = new Seq<>();
                bans.each(b -> banInfoPackets.add(new BanInfoPacket(b.name, b.uuid, b.adminName, b.reason, b.unbanDate)));
                banInfoPackets.each(p -> {
                    Log.debug(p.generate().toString());
                    Call.clientPacketUnreliable(player.con, "ban_data", p.generate().toString());
                });
            }
        });
    }
}
