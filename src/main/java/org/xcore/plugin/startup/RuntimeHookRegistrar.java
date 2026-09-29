package org.xcore.plugin.startup;

import arc.net.Server;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.net.ArcNetProvider;
import org.xcore.plugin.event.NetEventService;
import org.xcore.plugin.service.ServerDiscoveryService;

import java.io.IOException;
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

        server.setDiscoveryHandler((_, handler) -> {
            ByteBuffer buffer = ByteBuffer.allocate(500);
            // The handler runs on a UDP thread; discoveryService hops to the game thread
            // to sample the state and then calls back here to answer.
            discoveryService.handleDiscovery(buffer, () -> {
                try {
                    handler.respond(buffer);
                } catch (IOException ex) {
                    Log.err("[Discovery] Failed to send server list response: @", Strings.getSimpleMessage(ex));
                }
            });
        });

        netServer.admins.addChatFilter(netEvents::chat);
        Vars.net.handleServer(AdminRequestCallPacket.class, netEvents::adminRequest);
    }
}
