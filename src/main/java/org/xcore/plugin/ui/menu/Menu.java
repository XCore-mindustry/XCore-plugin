package org.xcore.plugin.ui.menu;

import mindustry.gen.Player;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

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
}
