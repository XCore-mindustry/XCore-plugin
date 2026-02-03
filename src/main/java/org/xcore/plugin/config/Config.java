package org.xcore.plugin.config;

import mindustry.gen.Groups;

import java.util.Set;

public class Config {
    public String server = "server";
    public SockType sockType = SockType.CLIENT;
    public boolean consoleEnabled = true;

    public int playerLimit = 30;
    public String globalConfigDirectory = null;
    public boolean gameStartedTimer = true;
    public Set<String> disabledCommands = Set.of(); // todo: implement in new annotation based commands

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

    public boolean isEvent() {
        return server.equals("event");
    }

    public boolean isEventHubMap = false;
    public String eventHubMapID = "";

    public enum SockType {
        CLIENT, SERVER
    }
}
