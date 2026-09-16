package org.xcore.plugin.event;

import java.util.concurrent.atomic.AtomicInteger;
import arc.Events;
import arc.func.Boolf;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.xcore.plugin.event.net.admin.AdminRequestHandler;
import org.xcore.plugin.event.net.chat.ChatMessageHandler;
import org.xcore.plugin.event.net.connect.ConnectionFilterService;

@Singleton
public class NetEventService {

    @Getter @Setter
    public Boolf<String> ipAcceptor = (ip) -> true;
    private final AtomicInteger blockedIPs = new AtomicInteger();
    private final AtomicInteger blockedIPsPerMinute = new AtomicInteger();

    public int getBlockedIPs() {
        return blockedIPs.get();
    }

    public int getBlockedIPsPerMinute() {
        return blockedIPsPerMinute.get();
    }

    private final ChatMessageHandler chatMessageHandler;
    private final AdminRequestHandler adminRequestHandler;
    private final ConnectionFilterService connectionFilterService;

    @Inject
    public NetEventService(ChatMessageHandler chatMessageHandler,
                           AdminRequestHandler adminRequestHandler,
                           ConnectionFilterService connectionFilterService) {
        this.chatMessageHandler = chatMessageHandler;
        this.adminRequestHandler = adminRequestHandler;
        this.connectionFilterService = connectionFilterService;
    }


    public String chat(Player author, String text) {
        return chatMessageHandler.handle(author, text);
    }

    public boolean connectFilter(String address) {
        var result = connectionFilterService.filter(address, ipAcceptor);
        blockedIPs.addAndGet(result.blockedIpDelta());
        blockedIPsPerMinute.addAndGet(result.blockedIpsPerMinuteDelta());
        return result.allowed();
    }

    public void adminRequest(NetConnection con, AdminRequestCallPacket packet) {
        adminRequestHandler.handle(con, packet);
    }
}
