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
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.net.admin.AdminRequestHandler;
import org.xcore.plugin.event.net.chat.ChatMessageHandler;
import org.xcore.plugin.event.net.connect.ConnectPacketHandler;
import org.xcore.plugin.event.net.connect.ConnectionFilterService;
import org.xcore.plugin.service.*;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.security.ingress.IngressService;
import org.xcore.plugin.vote.VoteService;

@Singleton
public class NetEventService {

    @Getter @Setter
    public Boolf<String> ipAcceptor = (ip) -> true;
    public int blockedIPs = 0;
    public int blockedIPsPerMinute = 0;

    private final SessionService sessionService;
    private final Config config;
    private final TranslatorService translatorService;
    private final NetworkService network;
    private final VoteService voteService;
    private final SecurityService securityService;
    private final IngressService ingressService;
    private final ChatMessageHandler chatMessageHandler;
    private final AdminRequestHandler adminRequestHandler;
    private final ConnectPacketHandler connectPacketHandler;
    private final ConnectionFilterService connectionFilterService;

    @Inject
    public NetEventService(SessionService sessionService, Config config,
                           TranslatorService translatorService, NetworkService network,
                           VoteService voteService,
                           SecurityService securityService,
                           IngressService ingressService,
                           ChatMessageHandler chatMessageHandler,
                           AdminRequestHandler adminRequestHandler,
                           ConnectPacketHandler connectPacketHandler,
                           ConnectionFilterService connectionFilterService) {
        this.sessionService = sessionService;
        this.config = config;
        this.translatorService = translatorService;
        this.network = network;
        this.voteService = voteService;
        this.securityService = securityService;
        this.ingressService = ingressService;
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
