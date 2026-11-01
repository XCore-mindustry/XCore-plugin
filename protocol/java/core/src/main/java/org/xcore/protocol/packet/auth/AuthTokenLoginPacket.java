package org.xcore.protocol.packet.auth;

public class AuthTokenLoginPacket {
    public int requestId;
    public String token;

    public AuthTokenLoginPacket() {}

    public AuthTokenLoginPacket(int requestId, String token) {
        this.requestId = requestId;
        this.token = token;
    }
}
