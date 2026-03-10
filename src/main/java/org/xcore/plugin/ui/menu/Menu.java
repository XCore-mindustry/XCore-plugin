package org.xcore.plugin.ui.menu;

import mindustry.gen.Player;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

public class Menu {
    protected final Config config;
    protected final GlobalConfig globalConfig;
    protected final SessionService sessionService;

    public Menu(Config config, GlobalConfig globalConfig, SessionService sessionService) {
        this.config = config;
        this.globalConfig = globalConfig;
        this.sessionService = sessionService;
    }

    public void sender(XCoreSender sender) {
        sessionService.get(sender.player().uuid()).sender = sender;
    }

    public void sender(XCoreSender sender, String uuid) {
        sessionService.get(uuid).sender = sender;
    }

    public String getUuid(Player player) {
        return player.uuid();
    }

    public String getUuid(PlayerData player) {
        return player.uuid;
    }

    public String getUuid(XCoreSender sender) {
        return sender.player().uuid();
    }

    public String getUuid(Session session) {
        return session.data.uuid;
    }

    public String formatTime(long millis, Session session) {
        if (millis <= 0) return session.locale().t("never");

        var df = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        return df.format(new java.util.Date(millis));
    }

    public String timeLeft(long endMillis, Session session) {
        long remaining = endMillis - System.currentTimeMillis();
        if (remaining <= 0) return session.locale().t("finished");

        long mins = (remaining / 60000) % 60;
        long hours = (remaining / 3600000);

        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    public String formatPlayTime(int totalMinutes, Localization local) {
        if (totalMinutes <= 0) {
            return local.t("player-menu-time-minutes", args("value", 0));
        }

        int days = totalMinutes / (60 * 24);
        int hours = (totalMinutes / 60) % 24;
        int minutes = totalMinutes % 60;

        StringBuilder result = new StringBuilder();
        appendDurationPart(result, local, "player-menu-time-days", days);
        appendDurationPart(result, local, "player-menu-time-hours", hours);
        appendDurationPart(result, local, "player-menu-time-minutes", minutes);

        if (result.isEmpty()) {
            appendDurationPart(result, local, "player-menu-time-minutes", 0);
        }

        return result.toString();
    }

    private void appendDurationPart(StringBuilder result, Localization local, String key, int value) {
        if (value <= 0) {
            return;
        }

        if (!result.isEmpty()) {
            result.append(' ');
        }
        result.append(local.t(key, args("value", value)));
    }
}
