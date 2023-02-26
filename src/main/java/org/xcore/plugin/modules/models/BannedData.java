package org.xcore.plugin.modules.models;

import arc.struct.Seq;

public class BannedData {
    public String uuid;
    public Seq<String> names;
    public Seq<String> ips;

    public BannedData(String uuid, Seq<String> names, Seq<String> ips) {
        this.uuid = uuid;
        this.names = names;
        this.ips = ips;
    }
}
