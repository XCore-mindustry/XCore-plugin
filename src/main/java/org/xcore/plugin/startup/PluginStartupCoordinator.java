package org.xcore.plugin.startup;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.migration.MigrationService;
import org.xcore.plugin.metrics.MainThreadMetricSampler;
import org.xcore.plugin.metrics.MetricsSnapshotPublisher;
import org.xcore.plugin.service.AutoHostService;
import org.xcore.plugin.session.SessionService;

@Singleton
public class PluginStartupCoordinator {

    private final MigrationService migrationService;
    private final MapDecayScheduler mapDecayScheduler;
    private final MapSelectorInstaller mapSelectorInstaller;
    private final RuntimeHookRegistrar runtimeHookRegistrar;
    private final MainThreadMetricSampler mainThreadMetricSampler;
    private final MetricsSnapshotPublisher metricsSnapshotPublisher;
    private final AutoHostService autoHostService;
    private final SessionService sessionService;

    @Inject
    public PluginStartupCoordinator(MigrationService migrationService,
                                    MapDecayScheduler mapDecayScheduler,
                                    MapSelectorInstaller mapSelectorInstaller,
                                    RuntimeHookRegistrar runtimeHookRegistrar,
                                    MainThreadMetricSampler mainThreadMetricSampler,
                                    MetricsSnapshotPublisher metricsSnapshotPublisher,
                                    AutoHostService autoHostService,
                                    SessionService sessionService) {
        this.migrationService = migrationService;
        this.mapDecayScheduler = mapDecayScheduler;
        this.mapSelectorInstaller = mapSelectorInstaller;
        this.runtimeHookRegistrar = runtimeHookRegistrar;
        this.mainThreadMetricSampler = mainThreadMetricSampler;
        this.metricsSnapshotPublisher = metricsSnapshotPublisher;
        this.autoHostService = autoHostService;
        this.sessionService = sessionService;
    }

    public boolean start() {
        if (!migrationService.run()) {
            return false;
        }

        sessionService.clearStalePresenceFlags();

        mapDecayScheduler.initialize();
        mapSelectorInstaller.install();
        runtimeHookRegistrar.register();
        autoHostService.initialize();
        return true;
    }
}
