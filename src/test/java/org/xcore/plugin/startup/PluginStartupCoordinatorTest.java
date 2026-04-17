package org.xcore.plugin.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.migration.MigrationService;

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
        when(migrationService.run()).thenReturn(true);

        PluginStartupCoordinator coordinator = new PluginStartupCoordinator(
                migrationService,
                mapDecayScheduler,
                mapSelectorInstaller,
                runtimeHookRegistrar
        );

        boolean started = coordinator.start();

        assertThat(started).isTrue();
        var startupOrder = inOrder(migrationService, mapDecayScheduler, mapSelectorInstaller, runtimeHookRegistrar);
        startupOrder.verify(migrationService).run();
        startupOrder.verify(mapDecayScheduler).initialize();
        startupOrder.verify(mapSelectorInstaller).install();
        startupOrder.verify(runtimeHookRegistrar).register();
    }

    @Test
    @DisplayName("start stops after failed migrations and skips later startup steps")
    void start_stopsWhenMigrationsFail() {
        MigrationService migrationService = mock(MigrationService.class);
        MapDecayScheduler mapDecayScheduler = mock(MapDecayScheduler.class);
        MapSelectorInstaller mapSelectorInstaller = mock(MapSelectorInstaller.class);
        RuntimeHookRegistrar runtimeHookRegistrar = mock(RuntimeHookRegistrar.class);
        when(migrationService.run()).thenReturn(false);

        PluginStartupCoordinator coordinator = new PluginStartupCoordinator(
                migrationService,
                mapDecayScheduler,
                mapSelectorInstaller,
                runtimeHookRegistrar
        );

        boolean started = coordinator.start();

        assertThat(started).isFalse();
        verifyNoInteractions(mapDecayScheduler, mapSelectorInstaller, runtimeHookRegistrar);
    }

    @Test
    @DisplayName("start invokes each successful startup collaborator exactly once")
    void start_invokesEachCollaboratorOnce() {
        MigrationService migrationService = mock(MigrationService.class);
        MapDecayScheduler mapDecayScheduler = mock(MapDecayScheduler.class);
        MapSelectorInstaller mapSelectorInstaller = mock(MapSelectorInstaller.class);
        RuntimeHookRegistrar runtimeHookRegistrar = mock(RuntimeHookRegistrar.class);
        when(migrationService.run()).thenReturn(true);

        PluginStartupCoordinator coordinator = new PluginStartupCoordinator(
                migrationService,
                mapDecayScheduler,
                mapSelectorInstaller,
                runtimeHookRegistrar
        );

        boolean started = coordinator.start();

        assertThat(started).isTrue();
        verify(migrationService, times(1)).run();
        verify(mapDecayScheduler, times(1)).initialize();
        verify(mapSelectorInstaller, times(1)).install();
        verify(runtimeHookRegistrar, times(1)).register();
    }
}
