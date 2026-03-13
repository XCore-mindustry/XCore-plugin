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
import org.xcore.plugin.database.migration.MigrationService;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.event.NetEventService;
import org.xcore.plugin.map.SmartMapSelector;
import org.xcore.plugin.service.ServerDiscoveryService;

import static mindustry.Vars.netServer;

public class XcorePlugin extends Plugin {
    public static BeanScope container; // for dependend plugins

    @Override
    public void init() {
        PLog.info("Plugin started.");

        container = BeanScope.builder()
                .classLoader(getClass().getClassLoader())
                .build();

        var migrationService = container.get(MigrationService.class);
        if (!migrationService.run()) {
            PLog.err("CRITICAL: Database migrations failed! Plugin initialization stopped.");
            return;
        }

        var mapDataRepository = container.get(MapDataRepository.class);
        initMapDecayScheduler(mapDataRepository);

        var mapSelector = container.get(SmartMapSelector.class);
        Reflect.set(Vars.maps, "shuffler", mapSelector);

        var netEvents = container.get(NetEventService.class);
        var discoveryService = container.get(ServerDiscoveryService.class);
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
        server.setDiscoveryHandler((_, handler) -> {
            var buffer = java.nio.ByteBuffer.allocate(500);
            discoveryService.handleDiscovery(buffer);
            handler.respond(buffer);
        });

        netServer.admins.addChatFilter(netEvents::chat);
        Vars.net.handleServer(Packets.Connect.class, netEvents::connect);
        Vars.net.handleServer(Packets.ConnectPacket.class, netEvents::connectPacket);
        Vars.net.handleServer(AdminRequestCallPacket.class, netEvents::adminRequest);
    }
}
