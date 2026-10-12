package tilelogger;

import arc.util.Log;
import io.avaje.inject.BeanScope;
import com.ospx.flubundle.Bundle;
import mindustry.mod.Plugin;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.startup.PluginScopes;
import tilelogger.command.HistoryController;
import tilelogger.command.RollbackController;
import tilelogger.command.TileLoggerController;
import tilelogger.command.TileLoggerServerController;

public class TileLoggerPlugin extends Plugin {
    private PluginScopes.Registration registration;

    @Override
    public synchronized void init() {
        if (registration != null) return;
        Log.info("[TileLogger] Initializing...");

        var xContainer = XcorePlugin.container;
        if (xContainer == null) {
            throw new RuntimeException("XCore container is null! XCore must be loaded first.");
        }

        var scopes = xContainer.get(PluginScopes.class);
        BeanScope scope = BeanScope.builder()
                .classLoader(getClass().getClassLoader())
                .parent(xContainer)
                .modules(new TileLoggerModule())
                .build();

        try {
            registration = scopes.register(scope);
            var bundle = scope.get(Bundle.class);
            bundle.addSource(getClass());

            var cloudService = scope.get(CloudService.class);

            cloudService.registerClient(scope.get(TileLoggerController.class));
            cloudService.registerClient(scope.get(HistoryController.class));
            cloudService.registerClient(scope.get(RollbackController.class));

            cloudService.registerServer(scope.get(TileLoggerController.class));
            cloudService.registerServer(scope.get(TileLoggerServerController.class));
            cloudService.registerServer(scope.get(RollbackController.class));


            Log.info("[TileLogger] Started successfully.");
        } catch (RuntimeException | Error failure) {
            disable();
            scope.close();
            throw failure;
        }
    }

    public synchronized void disable() {
        if (registration == null) return;
        try {
            registration.close();
        } finally {
            registration = null;
        }
    }
}
