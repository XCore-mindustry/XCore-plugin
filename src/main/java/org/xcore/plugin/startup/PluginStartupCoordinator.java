package org.xcore.plugin.startup;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.migration.MigrationService;
import org.xcore.plugin.metrics.MainThreadMetricSampler;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.metrics.MetricsSnapshotPublisher;
import org.xcore.plugin.metrics.Tags;
import org.xcore.plugin.metrics.XcoreMetrics;
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
    private final MetricsService metricsService;

    @Inject
    public PluginStartupCoordinator(MigrationService migrationService,
                                    MapDecayScheduler mapDecayScheduler,
                                    MapSelectorInstaller mapSelectorInstaller,
                                    RuntimeHookRegistrar runtimeHookRegistrar,
                                    MainThreadMetricSampler mainThreadMetricSampler,
                                    MetricsSnapshotPublisher metricsSnapshotPublisher,
                                    AutoHostService autoHostService,
                                    SessionService sessionService,
                                    MetricsService metricsService) {
        this.migrationService = migrationService;
        this.mapDecayScheduler = mapDecayScheduler;
        this.mapSelectorInstaller = mapSelectorInstaller;
        this.runtimeHookRegistrar = runtimeHookRegistrar;
        this.mainThreadMetricSampler = mainThreadMetricSampler;
        this.metricsSnapshotPublisher = metricsSnapshotPublisher;
        this.autoHostService = autoHostService;
        this.sessionService = sessionService;
        this.metricsService = metricsService;
    }

    public boolean start() {
        if (!migrationService.run()) {
            return false;
        }

        sessionService.clearStalePresenceFlags();

        // Log-once is right for a person reading a log and wrong for a dashboard: a site
        // that fires once at startup looks identical to one firing thousands of times under
        // load. GameThread counts repeats internally; this is what makes that count visible.
        org.xcore.plugin.concurrent.GameThread.setViolationListener((site, threadName) ->
                metricsService.increment(
                        XcoreMetrics.THREAD_AFFINITY_VIOLATIONS_TOTAL,
                        Tags.of("site", site, "thread", threadName)
                ));

        mapDecayScheduler.initialize();
        mapSelectorInstaller.install();
        runtimeHookRegistrar.register();
        autoHostService.initialize();
        return true;
    }
}
