package org.xcore.protocol.packet.auth;

public class AuthResultPacket {
    public int requestId;
    public String status;
    public String messageKey;
    public String message;
    public String token;

    public AuthResultPacket() {}

    public AuthResultPacket(int requestId, String status, String message) {
        this(requestId, status, message, null);
    }

    public AuthResultPacket(int requestId, String status, String message, String token) {
        this.requestId = requestId;
        this.status = status;
        this.message = message;
        this.messageKey = message;
        this.token = token;
    }
}
