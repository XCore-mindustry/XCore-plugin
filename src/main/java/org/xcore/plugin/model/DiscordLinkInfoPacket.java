package org.xcore.plugin.model;

public class DiscordLinkInfoPacket {
    public boolean success;
    public String code;
    public long expiresAt;
    public String error;

    public DiscordLinkInfoPacket() {}

    public DiscordLinkInfoPacket(boolean success, String code, long expiresAt, String error) {
        this.success = success;
        this.code = code == null ? "" : code;
        this.expiresAt = expiresAt;
        this.error = error == null ? "" : error;
    }
}
