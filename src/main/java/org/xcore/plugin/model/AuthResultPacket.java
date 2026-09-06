package org.xcore.plugin.model;

public class AuthResultPacket {
    public int requestId;
    public String status;
    public String message;
    public String token;

    public AuthResultPacket(int requestId, String status, String message, String token) {
        this.requestId = requestId;
        this.status = status;
        this.message = message;
        this.token = token;
    }

    public AuthResultPacket(int requestId, String status, String message) {
        this(requestId, status, message, null);
    }

    public AuthResultPacket() {}
}
