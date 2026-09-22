package org.xcore.plugin.event;

import arc.func.Boolf;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.xcore.plugin.event.net.admin.AdminRequestHandler;
import org.xcore.plugin.event.net.chat.ChatMessageHandler;
import org.xcore.plugin.event.net.connect.ConnectionFilterService;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class NetEventService {

    private volatile Boolf<String> ipAcceptor = (ip) -> true;
    private final AtomicInteger blockedIPs = new AtomicInteger();
    private final AtomicInteger blockedIPsPerMinute = new AtomicInteger();

    public Boolf<String> getIpAcceptor() {
        return ipAcceptor;
    }

    public void setIpAcceptor(Boolf<String> ipAcceptor) {
        this.ipAcceptor = ipAcceptor != null ? ipAcceptor : (ip) -> true;
    }

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
        if (address == null) return false;
        try {
            var result = connectionFilterService.filter(address, ipAcceptor);
            blockedIPs.addAndGet(result.blockedIpDelta());
            blockedIPsPerMinute.addAndGet(result.blockedIpsPerMinuteDelta());
            return result.allowed();
        } catch (Throwable t) {
            Log.err("Error evaluating connectFilter for " + address, t);
            return false;
        }
    }

    public void adminRequest(NetConnection con, AdminRequestCallPacket packet) {
        adminRequestHandler.handle(con, packet);
    }
}
