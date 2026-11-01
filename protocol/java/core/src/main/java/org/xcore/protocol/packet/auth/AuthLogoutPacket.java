package org.xcore.protocol.packet.auth;

public class AuthLogoutPacket {
    public String token;

    public AuthLogoutPacket() {}

    public AuthLogoutPacket(String token) {
        this.token = token;
    }
}
