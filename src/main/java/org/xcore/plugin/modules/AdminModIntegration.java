package org.xcore.plugin.modules;

import arc.util.Time;
import arc.util.serialization.JsonValue;
import fr.xpdustry.javelin.JavelinPlugin;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.utils.JavelinCommunicator;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;

import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.*;

public class AdminModIntegration {

    /**
     * Инициализация возможностоей мода для упрощения администрирования
     */
    public static void init() {
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
                ban.generateBid();
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
            ban.generateBid();

            JavelinCommunicator.sendEvent(ban, b -> {
                if (b.full) {
                    Utils.temporaryBan(b);
                } else {
                    Bot.sendBanEvent(b);
                }
            });
        });
    }
}
