package org.xcore.plugin;

import arc.net.Server;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Timer;
import io.avaje.inject.BeanScope;
import mindustry.Vars;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.mod.Plugin;
import mindustry.net.ArcNetProvider;
import mindustry.net.Packets;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.event.NetEventService;
import org.xcore.plugin.map.SmartMapSelector;
import org.xcore.plugin.service.ServerDiscoveryService;

import static mindustry.Vars.netServer;

public class XcorePlugin extends Plugin {

    @Override
    public void init() {
        BeanScope beanScope = BeanScope.builder()
                .classLoader(getClass().getClassLoader())
                .build();

        var mapDataRepository = beanScope.get(MapDataRepository.class);
        initMapDecayScheduler(mapDataRepository);

        var mapSelector = beanScope.get(SmartMapSelector.class);
        Reflect.set(Vars.maps, "shuffler", mapSelector);

        var netEvents = beanScope.get(NetEventService.class);
        var discoveryService = beanScope.get(ServerDiscoveryService.class);
        initNetworkHooks(netEvents, discoveryService);

        PLog.info("Plugin initialized.");
    }

    private void initMapDecayScheduler(MapDataRepository mapDataRepository) {
        try {
            mapDataRepository.checkMapDecay();
        } catch (Exception e) {
            Log.err("Failed to check map decay on init", e);
        }
        Timer.schedule(() -> {
            try {
                mapDataRepository.checkMapDecay();
            } catch (Exception e) {
                Log.err("Failed to check map decay", e);
            }
        }, 60 * 60, 60 * 60);
    }

    private void initNetworkHooks(NetEventService netEvents, ServerDiscoveryService discoveryService) {
        ArcNetProvider provider = Reflect.get(Vars.net, "provider");
        Server server = Reflect.get(provider, "server");

        server.setConnectFilter(netEvents::connectFilter);
        server.setDiscoveryHandler((address, handler) -> {
            var buffer = java.nio.ByteBuffer.allocate(500);
            discoveryService.handleDiscovery(buffer);
            handler.respond(buffer);
        });

        netServer.admins.addChatFilter(netEvents::chat);
        Vars.net.handleServer(AdminRequestCallPacket.class, netEvents::adminRequest);
        Vars.net.handleServer(Packets.Connect.class, netEvents::connect);
        Vars.net.handleServer(Packets.ConnectPacket.class, netEvents::connectPacket);
    }
}
