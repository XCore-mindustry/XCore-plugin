package org.xcore.plugin.utils.models;

public class BanRequestData {
    public int pid;
    public String name;
    public String reason = "Not Specified", duration = "0";

    public BanRequestData(int pid, String name) {
        this.pid = pid;
        this.name = name;
    }

    public BanRequestData() {}
}
