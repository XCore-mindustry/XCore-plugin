package tilelogger;

import arc.util.Log;
import io.avaje.inject.BeanScope;
import mindustry.mod.Plugin;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.localization.BundleService;
import tilelogger.command.HistoryController;
import tilelogger.command.RollbackController;
import tilelogger.command.TileLoggerController;
import tilelogger.command.TileLoggerServerController;

public class TileLoggerPlugin extends Plugin {

    @Override
    public void init() {
        Log.info("[TileLogger] Initializing...");

        var xContainer = XcorePlugin.container;
        if (xContainer == null) {
            throw new RuntimeException("XCore container is null! XCore must be loaded first.");
        }

        BeanScope scope = BeanScope.builder()
                .classLoader(getClass().getClassLoader())
                .parent(xContainer)
                .modules(new TileLoggerModule())
                .build();

        var bundleService = scope.get(BundleService.class);
        bundleService.getBundle().addSource(getClass());

        var cloudService = scope.get(CloudService.class);

        cloudService.registerClient(scope.get(TileLoggerController.class));
        cloudService.registerClient(scope.get(HistoryController.class));
        cloudService.registerClient(scope.get(RollbackController.class));

        cloudService.registerServer(scope.get(TileLoggerController.class));
        cloudService.registerServer(scope.get(TileLoggerServerController.class));
        cloudService.registerServer(scope.get(RollbackController.class));


        Log.info("[TileLogger] Started successfully.");
    }
}
