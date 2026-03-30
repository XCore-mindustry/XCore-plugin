package org.xcore.plugin.event;

import arc.Events;
import arc.func.Boolf;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import mindustry.game.EventType;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.xcore.plugin.event.net.admin.AdminRequestHandler;
import org.xcore.plugin.event.net.chat.ChatMessageHandler;
import org.xcore.plugin.event.net.connect.ConnectPacketHandler;
import org.xcore.plugin.event.net.connect.ConnectionFilterService;

@Singleton
public class NetEventService {

    @Getter @Setter
    public Boolf<String> ipAcceptor = (ip) -> true;
    public int blockedIPs = 0;
    public int blockedIPsPerMinute = 0;

    private final ChatMessageHandler chatMessageHandler;
    private final AdminRequestHandler adminRequestHandler;
    private final ConnectPacketHandler connectPacketHandler;
    private final ConnectionFilterService connectionFilterService;

    @Inject
    public NetEventService(ChatMessageHandler chatMessageHandler,
                           AdminRequestHandler adminRequestHandler,
                           ConnectPacketHandler connectPacketHandler,
                           ConnectionFilterService connectionFilterService) {
        this.chatMessageHandler = chatMessageHandler;
        this.adminRequestHandler = adminRequestHandler;
        this.connectPacketHandler = connectPacketHandler;
        this.connectionFilterService = connectionFilterService;
    }


    public String chat(Player author, String text) {
        return chatMessageHandler.handle(author, text);
    }

    public boolean connectFilter(String address) {
        var result = connectionFilterService.filter(address, ipAcceptor);
        blockedIPs += result.blockedIpDelta();
        blockedIPsPerMinute += result.blockedIpsPerMinuteDelta();
        return result.allowed();
    }

    public void connect(NetConnection con, Packets.Connect packet) {
        Events.fire(new EventType.ConnectionEvent(con));
    }

    public void adminRequest(NetConnection con, AdminRequestCallPacket packet) {
        adminRequestHandler.handle(con, packet);
    }

    public void connectPacket(NetConnection con, Packets.ConnectPacket packet) {
        connectPacketHandler.handle(con, packet);
    }
}
