package org.xcore.plugin.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.migration.MigrationService;
import org.xcore.plugin.metrics.MainThreadMetricSampler;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.metrics.MetricsSnapshotPublisher;
import org.xcore.plugin.service.AutoHostService;
import org.xcore.plugin.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PluginStartupCoordinatorTest {

    @Test
    @DisplayName("start runs collaborators in startup order after successful migrations")
    void start_runsCollaboratorsInStartupOrder() {
        MigrationService migrationService = mock(MigrationService.class);
        MapDecayScheduler mapDecayScheduler = mock(MapDecayScheduler.class);
        MapSelectorInstaller mapSelectorInstaller = mock(MapSelectorInstaller.class);
        RuntimeHookRegistrar runtimeHookRegistrar = mock(RuntimeHookRegistrar.class);
        MainThreadMetricSampler mainThreadMetricSampler = mock(MainThreadMetricSampler.class);
        MetricsSnapshotPublisher metricsSnapshotPublisher = mock(MetricsSnapshotPublisher.class);
        AutoHostService autoHostService = mock(AutoHostService.class);
        SessionService sessionService = mock(SessionService.class);
        when(migrationService.run()).thenReturn(true);

        PluginStartupCoordinator coordinator = new PluginStartupCoordinator(
                migrationService,
                mapDecayScheduler,
                mapSelectorInstaller,
                runtimeHookRegistrar,
                mainThreadMetricSampler,
                metricsSnapshotPublisher,
                autoHostService,
                sessionService,
                mock(MetricsService.class)
        );

        boolean started = coordinator.start();

        assertThat(started).isTrue();
        var startupOrder = inOrder(migrationService, mapDecayScheduler, mapSelectorInstaller, runtimeHookRegistrar, sessionService, autoHostService);
        startupOrder.verify(migrationService).run();
        startupOrder.verify(sessionService).clearStalePresenceFlags();
        startupOrder.verify(mapDecayScheduler).initialize();
        startupOrder.verify(mapSelectorInstaller).install();
        startupOrder.verify(runtimeHookRegistrar).register();
        startupOrder.verify(autoHostService).initialize();
    }

    @Test
    @DisplayName("start stops after failed migrations and skips later startup steps")
    void start_stopsWhenMigrationsFail() {
        MigrationService migrationService = mock(MigrationService.class);
        MapDecayScheduler mapDecayScheduler = mock(MapDecayScheduler.class);
        MapSelectorInstaller mapSelectorInstaller = mock(MapSelectorInstaller.class);
        RuntimeHookRegistrar runtimeHookRegistrar = mock(RuntimeHookRegistrar.class);
        MainThreadMetricSampler mainThreadMetricSampler = mock(MainThreadMetricSampler.class);
        MetricsSnapshotPublisher metricsSnapshotPublisher = mock(MetricsSnapshotPublisher.class);
        AutoHostService autoHostService = mock(AutoHostService.class);
        SessionService sessionService = mock(SessionService.class);
        when(migrationService.run()).thenReturn(false);

        PluginStartupCoordinator coordinator = new PluginStartupCoordinator(
                migrationService,
                mapDecayScheduler,
                mapSelectorInstaller,
                runtimeHookRegistrar,
                mainThreadMetricSampler,
                metricsSnapshotPublisher,
                autoHostService,
                sessionService,
                mock(MetricsService.class)
        );

        boolean started = coordinator.start();

        assertThat(started).isFalse();
        verifyNoInteractions(mapDecayScheduler, mapSelectorInstaller, runtimeHookRegistrar, autoHostService, sessionService);
    }

    @Test
    @DisplayName("start invokes each successful startup collaborator exactly once")
    void start_invokesEachCollaboratorOnce() {
        MigrationService migrationService = mock(MigrationService.class);
        MapDecayScheduler mapDecayScheduler = mock(MapDecayScheduler.class);
        MapSelectorInstaller mapSelectorInstaller = mock(MapSelectorInstaller.class);
        RuntimeHookRegistrar runtimeHookRegistrar = mock(RuntimeHookRegistrar.class);
        MainThreadMetricSampler mainThreadMetricSampler = mock(MainThreadMetricSampler.class);
        MetricsSnapshotPublisher metricsSnapshotPublisher = mock(MetricsSnapshotPublisher.class);
        AutoHostService autoHostService = mock(AutoHostService.class);
        SessionService sessionService = mock(SessionService.class);
        when(migrationService.run()).thenReturn(true);

        PluginStartupCoordinator coordinator = new PluginStartupCoordinator(
                migrationService,
                mapDecayScheduler,
                mapSelectorInstaller,
                runtimeHookRegistrar,
                mainThreadMetricSampler,
                metricsSnapshotPublisher,
                autoHostService,
                sessionService,
                mock(MetricsService.class)
        );

        boolean started = coordinator.start();

        assertThat(started).isTrue();
        verify(migrationService, times(1)).run();
        verify(sessionService, times(1)).clearStalePresenceFlags();
        verify(mapDecayScheduler, times(1)).initialize();
        verify(mapSelectorInstaller, times(1)).install();
        verify(runtimeHookRegistrar, times(1)).register();
        verify(autoHostService, times(1)).initialize();
    }
}
