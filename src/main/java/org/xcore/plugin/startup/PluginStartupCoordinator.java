package org.xcore.plugin.startup;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.migration.MigrationService;

@Singleton
public class PluginStartupCoordinator {

    private final MigrationService migrationService;
    private final MapDecayScheduler mapDecayScheduler;
    private final MapSelectorInstaller mapSelectorInstaller;
    private final RuntimeHookRegistrar runtimeHookRegistrar;

    @Inject
    public PluginStartupCoordinator(MigrationService migrationService,
                                    MapDecayScheduler mapDecayScheduler,
                                    MapSelectorInstaller mapSelectorInstaller,
                                    RuntimeHookRegistrar runtimeHookRegistrar) {
        this.migrationService = migrationService;
        this.mapDecayScheduler = mapDecayScheduler;
        this.mapSelectorInstaller = mapSelectorInstaller;
        this.runtimeHookRegistrar = runtimeHookRegistrar;
    }

    public boolean start() {
        if (!migrationService.run()) {
            return false;
        }

        mapDecayScheduler.initialize();
        mapSelectorInstaller.install();
        runtimeHookRegistrar.register();
        return true;
    }
}
