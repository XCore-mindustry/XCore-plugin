package org.xcore.plugin.service;

import arc.util.Timer;
import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.session.SessionService;

import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerActivityServiceAvajeTest {
    @Test
    void closingScopeCancelsTimerAndDisablesAnAlreadyQueuedTick() {
        var task = mock(Timer.Task.class);
        var tick = new AtomicReference<Runnable>();
        try (var timer = mockStatic(Timer.class)) {
            timer.when(() -> Timer.schedule(any(Runnable.class), eq(0f), eq(60f))).thenAnswer(invocation -> {
                tick.set(invocation.getArgument(0));
                return task;
            });
            var scope = BeanScope.builder().bean(TomlSecretsConfig.class, new TomlSecretsConfig())
                    .modules(new AvajeModule() {
                        @Override
                        public Class<?>[] classes() { return new Class<?>[]{PlayerActivityService.class}; }

                        @Override
                        public void build(Builder builder) { PlayerActivityService$DI.build(builder); }
                    }).forTesting()
                    .mock(SessionService.class)
                    .mock(FindService.class)
                    .build();

            scope.close();
            tick.get().run();

            verify(task).cancel();
            verifyNoInteractions(scope.get(SessionService.class), scope.get(FindService.class));
        }
    }
}
