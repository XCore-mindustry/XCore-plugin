package org.xcore.plugin.modules.models;

import fr.xpdustry.javelin.JavelinEvent;

public class BanData implements JavelinEvent {
    public String uuid;
    public String ip;

    public String name = "<unknown>";
    public String adminName;
    public String reason;
    public String server;
    public long unbanDate;

    public boolean full = true;

    @SuppressWarnings("unused")
    public BanData() {
    }

    public BanData(String uuid, String ip, String name, String adminName, String server) {
        this.uuid = uuid;
        this.ip = ip;
        this.name = name;
        this.adminName = adminName;
        this.server = server;
        this.full = false;
    }

    public BanData(String uuid, String ip, String name, String adminName, String reason, String server, long unbanDate) {
        this.uuid = uuid;
        this.ip = ip;
        this.name = name;
        this.adminName = adminName;
        this.reason = reason;
        this.server = server;
        this.unbanDate = unbanDate;
    }
}
