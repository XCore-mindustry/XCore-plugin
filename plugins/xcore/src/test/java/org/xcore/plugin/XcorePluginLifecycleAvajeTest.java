package org.xcore.plugin;

import arc.Events;
import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import mindustry.game.EventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.concurrent.StorageExecutor$DI;
import org.xcore.plugin.startup.PluginScopes;
import org.xcore.plugin.startup.PluginScopes$DI;
import org.xcore.plugin.startup.PluginStartupCoordinator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class XcorePluginLifecycleAvajeTest {

    private BeanScope scope;

    @BeforeEach
    void setUp() {
        XcorePlugin.resetForTesting();
        scope = BeanScope.builder()
                .modules(new StorageTestModule())
                .forTesting()
                .build();
    }

    @AfterEach
    void tearDown() {
        XcorePlugin.teardown();
        if (scope != null) {
            try {
                scope.close();
            } catch (Throwable ignored) {
            }
        }
        XcorePlugin.resetForTesting();
    }

    @Test
    @DisplayName("closing Avaje BeanScope runs @PreDestroy on StorageExecutor and shuts it down")
    void closingBeanScopeShutsDownStorageExecutor() {
        var storage = scope.get(StorageExecutor.class);
        assertThat(storage.isShutdown()).isFalse();

        scope.close();

        assertThat(storage.isShutdown()).isTrue();
    }

    @Test
    @DisplayName("XcorePlugin.teardown() closes container, executes @PreDestroy, and nulls static field")
    void teardownClosesContainerAndNullsField() {
        XcorePlugin.container = scope;
        var storage = scope.get(StorageExecutor.class);
        assertThat(storage.isShutdown()).isFalse();

        XcorePlugin.teardown();

        assertThat(storage.isShutdown()).isTrue();
        assertThat(XcorePlugin.container).isNull();
    }

    @Test
    @DisplayName("XcorePlugin.teardown() is idempotent when called multiple times")
    void teardownIsIdempotent() {
        XcorePlugin.container = scope;
        var storage = scope.get(StorageExecutor.class);

        XcorePlugin.teardown();
        assertThat(storage.isShutdown()).isTrue();
        assertThat(XcorePlugin.container).isNull();

        // Second call should be a no-op and not throw
        XcorePlugin.teardown();
        assertThat(XcorePlugin.container).isNull();
    }

    @Test
    @DisplayName("Mindustry DisposeEvent triggers XcorePlugin teardown")
    void disposeEventTriggersTeardown() {
        var coordinator = mock(PluginStartupCoordinator.class);
        when(coordinator.start()).thenReturn(true);
        var startupScope = spy(scope);
        doReturn(coordinator).when(startupScope).get(PluginStartupCoordinator.class);
        var storage = scope.get(StorageExecutor.class);
        new XcorePlugin() {
            @Override
            protected BeanScope createScope() { return startupScope; }
        }.init();

        Events.fire(new EventType.DisposeEvent());

        assertThat(storage.isShutdown()).isTrue();
        assertThat(XcorePlugin.container).isNull();
    }

    @Test
    void startupExceptionClosesBuiltScope() {
        var coordinator = mock(PluginStartupCoordinator.class);
        var failingScope = mock(BeanScope.class);
        when(failingScope.get(PluginStartupCoordinator.class)).thenReturn(coordinator);
        var failure = new IllegalStateException("startup failed");
        when(coordinator.start()).thenThrow(failure);
        var plugin = new XcorePlugin() {
            @Override
            protected BeanScope createScope() { return failingScope; }
        };

        assertThatThrownBy(plugin::init).isSameAs(failure);

        verify(failingScope).close();
        assertThat(XcorePlugin.container).isNull();
    }

    @Test
    void failedMigrationsCloseBuiltScope() {
        var coordinator = mock(PluginStartupCoordinator.class);
        var failingScope = mock(BeanScope.class);
        when(failingScope.get(PluginStartupCoordinator.class)).thenReturn(coordinator);
        var plugin = new XcorePlugin() {
            @Override
            protected BeanScope createScope() { return failingScope; }
        };

        plugin.init();

        verify(failingScope).close();
        assertThat(XcorePlugin.container).isNull();
    }

    @Test
    void childScopeClosesWhileParentStorageIsAvailable() {
        var storage = scope.get(StorageExecutor.class);
        var childClosedWithStorageAvailable = new AtomicBoolean();
        var child = BeanScope.builder().parent(scope).modules(new AvajeModule() {
            @Override
            public Class<?>[] classes() { return new Class<?>[0]; }

            @Override
            public void build(Builder builder) {
                builder.addPreDestroy(() -> childClosedWithStorageAvailable.set(!storage.isShutdown()));
            }
        }).build();
        scope.get(PluginScopes.class).register(child);

        scope.close();

        assertThat(childClosedWithStorageAvailable).isTrue();
        assertThat(storage.isShutdown()).isTrue();
    }

    @Test
    void generatedStorageCleanupDrainsWorkBeforeClosingResources() {
        var release = new CountDownLatch(1);
        var work = new CompletableFuture<Void>();
        var resourceClosedAfterDrain = new AtomicBoolean();
        try (var orderedScope = BeanScope.builder().modules(new AvajeModule() {
            @Override
            public Class<?>[] classes() { return new Class<?>[]{StorageExecutor.class}; }

            @Override
            public void build(Builder builder) {
                StorageExecutor$DI.build(builder);
                var storage = builder.get(StorageExecutor.class);
                // Register resources after storage, as the production graph does.
                builder.addPreDestroy(() -> resourceClosedAfterDrain.set(storage.isShutdown() && work.isDone()));
                builder.addPreDestroy(release::countDown, 200);
            }
        }).forTesting().build()) {
            orderedScope.get(StorageExecutor.class).execute(() -> {
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("cleanup did not release work");
                    work.complete(null);
                } catch (Exception error) {
                    work.completeExceptionally(error);
                }
            });
        }

        assertThat(work).isCompletedWithValue(null);
        assertThat(resourceClosedAfterDrain).isTrue();
    }

    private static final class StorageTestModule implements AvajeModule {
        @Override
        public Class<?>[] classes() {
            return new Class<?>[]{StorageExecutor.class, PluginScopes.class};
        }

        @Override
        public void build(Builder builder) {
            StorageExecutor$DI.build(builder);
            PluginScopes$DI.build(builder);
        }
    }
}
