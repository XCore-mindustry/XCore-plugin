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

import static org.assertj.core.api.Assertions.assertThat;

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
        XcorePlugin.container = scope;
        var storage = scope.get(StorageExecutor.class);

        Events.on(EventType.DisposeEvent.class, e -> XcorePlugin.teardown());

        Events.fire(new EventType.DisposeEvent());

        assertThat(storage.isShutdown()).isTrue();
        assertThat(XcorePlugin.container).isNull();
    }

    private static final class StorageTestModule implements AvajeModule {
        @Override
        public Class<?>[] classes() {
            return new Class<?>[]{StorageExecutor.class};
        }

        @Override
        public void build(Builder builder) {
            StorageExecutor$DI.build(builder);
        }
    }
}
