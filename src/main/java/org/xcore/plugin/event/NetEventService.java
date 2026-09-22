package org.xcore.plugin.event;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.xcore.plugin.event.net.admin.AdminRequestHandler;
import org.xcore.plugin.event.net.chat.ChatMessageHandler;

@Singleton
public class NetEventService {

    private final ChatMessageHandler chatMessageHandler;
    private final AdminRequestHandler adminRequestHandler;

    @Inject
    public NetEventService(ChatMessageHandler chatMessageHandler,
                           AdminRequestHandler adminRequestHandler) {
        this.chatMessageHandler = chatMessageHandler;
        this.adminRequestHandler = adminRequestHandler;
    }

    public String chat(Player author, String text) {
        return chatMessageHandler.handle(author, text);
    }

    public void adminRequest(NetConnection con, AdminRequestCallPacket packet) {
        adminRequestHandler.handle(con, packet);
    }
}
