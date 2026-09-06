package org.xcore.plugin.model;

public class AuthLogoutPacket {
    public String token;

    public AuthLogoutPacket(String token) {
        this.token = token;
    }

    public AuthLogoutPacket() {}
}
