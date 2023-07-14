package org.xcore.plugin.utils.models;

public class BanRequestData {
    public String uuid, ip, name, reason = "Not Specified", duration = "0";

    public BanRequestData(String uuid, String ip, String name) {
        this.uuid = uuid;
        this.ip = ip;
        this.name = name;
    }

    public BanRequestData() {}
}
