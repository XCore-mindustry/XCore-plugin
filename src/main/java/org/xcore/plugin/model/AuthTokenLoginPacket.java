package org.xcore.plugin.model;

public class AuthTokenLoginPacket {
    public int requestId;
    public String token;

    public AuthTokenLoginPacket(int requestId, String token) {
        this.requestId = requestId;
        this.token = token;
    }

    public AuthTokenLoginPacket() {}
}
