package org.xcore.plugin.startup;

import arc.net.Server;
import arc.util.Reflect;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.net.ArcNetProvider;
import mindustry.net.Packets;
import org.xcore.plugin.event.NetEventService;
import org.xcore.plugin.service.ServerDiscoveryService;

import java.nio.ByteBuffer;

import static mindustry.Vars.netServer;

@Singleton
public class RuntimeHookRegistrar {

    private final NetEventService netEvents;
    private final ServerDiscoveryService discoveryService;

    @Inject
    public RuntimeHookRegistrar(NetEventService netEvents, ServerDiscoveryService discoveryService) {
        this.netEvents = netEvents;
        this.discoveryService = discoveryService;
    }

    public void register() {
        ArcNetProvider provider = Reflect.get(Vars.net, "provider");
        Server server = Reflect.get(provider, "server");

        server.setConnectFilter(netEvents::connectFilter);
        server.setDiscoveryHandler((_, handler) -> {
            ByteBuffer buffer = ByteBuffer.allocate(500);
            discoveryService.handleDiscovery(buffer);
            handler.respond(buffer);
        });

        netServer.admins.addChatFilter(netEvents::chat);
        Vars.net.handleServer(Packets.Connect.class, netEvents::connect);
        Vars.net.handleServer(Packets.ConnectPacket.class, netEvents::connectPacket);
        Vars.net.handleServer(AdminRequestCallPacket.class, netEvents::adminRequest);
    }
}
