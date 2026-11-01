package org.xcore.protocol.packet.auth;

public class AuthLoginPacket {
    public int requestId;
    public String password;
    public boolean rememberDevice;

    public AuthLoginPacket() {}

    public AuthLoginPacket(int requestId, String password) {
        this(requestId, password, false);
    }

    public AuthLoginPacket(int requestId, String password, boolean rememberDevice) {
        this.requestId = requestId;
        this.password = password;
        this.rememberDevice = rememberDevice;
    }
}
