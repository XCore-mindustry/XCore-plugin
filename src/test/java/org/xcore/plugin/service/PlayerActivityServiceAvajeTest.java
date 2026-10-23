package org.xcore.plugin.service;

import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.session.SessionService;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@WithHeadlessWorld
class PlayerActivityServiceAvajeTest {
    @Test
    void closingScopeCancelsTimerAndDisablesAnAlreadyQueuedTick(HeadlessWorld world) {
        var mainLoop = world.mainLoop();
        var timer = world.timer();
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
        timer.runDue(); // the first tick is posted to the main loop and waits there
        assertThat(mainLoop.size()).isEqualTo(1);

        scope.close();
        mainLoop.runUntilIdle();
        timer.advance(120f);
        mainLoop.runUntilIdle();

        assertThat(timer.pending()).as("the 60 s tick is cancelled").isZero();
        verifyNoInteractions(scope.get(SessionService.class), scope.get(FindService.class));
    }
}
