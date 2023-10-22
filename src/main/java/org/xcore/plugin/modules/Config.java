package org.xcore.plugin.modules;

import mindustry.gen.Groups;
import mindustry.net.Administration;
import org.xcore.plugin.XcorePlugin;

import java.util.List;
import java.util.Set;

import static org.xcore.plugin.PluginVars.*;

public class Config {
    public String server = "server";
    public SockType sockType = SockType.CLIENT;
    public boolean consoleEnabled = true;

    public int playerLimit = 30;
    public String globalConfigDirectory = null;
    public boolean gameStartedTimer = true;
    public Set<String> disabledCommands = Set.of();

    public static void init() {
        if (configFile.exists()) {
            config = prettyGson.fromJson(configFile.reader(), Config.class);
            XcorePlugin.info("Config loaded.");
        } else {
            configFile.writeString(prettyGson.toJson(config = new Config()));
            XcorePlugin.info("Config generated.");
        }

        Administration.Config.showConnectMessages.set(false);
    }

    public int getNoAdminPlayerLimit() {
        return this.playerLimit + Groups.player.count(p -> p.admin);
    }

    public boolean isMiniPvP() {
        return server.equals("mini-pvp");
    }

    public boolean isMiniHexed() {
        return server.equals("mini-hexed");
    }

    public boolean isLastStanding() {
        return server.equals("the-last-standing");
    }

    public enum SockType {
        CLIENT, SERVER
    }
}
