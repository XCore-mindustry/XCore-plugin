package org.xcore.plugin.modules;

import arc.util.Time;
import arc.util.serialization.JsonValue;
import fr.xpdustry.javelin.JavelinPlugin;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.io.JsonIO;
import mindustry.net.Packet;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.modules.models.BanData;
import org.xcore.plugin.modules.models.BannedData;
import org.xcore.plugin.modules.models.PlayerData;
import org.xcore.plugin.modules.packets.BannedPlayersPacket;

import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.Utils.temporaryBan;

public class AdminModIntegration {
    public static void init() {
        netServer.addPacketHandler("give_banned_players", (player, content) -> {
            PlayerData data = Database.cachedPlayerData.get(player.uuid());
            if (!data.consolePanelAccess) return;

            BannedPlayersPacket packet = new BannedPlayersPacket();

            netServer.admins.getBanned().each(info -> {
                packet.bans.add(new BannedData(info.id, info.names, info.ips));
            });

            Call.clientPacketReliable(player.con, "take_banned_players", JsonIO.write(packet));
        });
        netServer.addPacketHandler("take_ban_data", (player, content) -> {
            if (!player.admin) return;

            JsonValue json = reader.parse(content);

            String uuid = json.get("uuid").asString();
            String ip = json.get("ip").asString();
            String name = json.get("name").asString();
            String reason = json.get("reason").asString();

            boolean skipToDiscord = json.get("skip_to_discord").asBoolean();
            short duration = json.get("duration").asShort();

            if (uuid == null || uuid.isBlank()) {
                player.sendMessage("UUID cannot be blank.");
                return;
            }

            if (reason == null || reason.isBlank()) {
                reason = "unknown";
            }

            if (skipToDiscord) {
                BanData ban = new BanData(uuid, ip, name, player.name, config.server);
                if (isSocketServer) {
                    Bot.sendBanEvent(ban);
                } else {
                    JavelinPlugin.getJavelinSocket().sendEvent(ban);
                }
                return;
            }

            if (duration == 0) {
                return;
            }

            BanData ban = new BanData(uuid, ip, name, player.name, reason, config.server, Time.millis() + TimeUnit.DAYS.toMillis(duration));
            if (isSocketServer) {
                temporaryBan(ban);
            } else {
                JavelinPlugin.getJavelinSocket().sendEvent(ban);
            }
        });
    }
}
