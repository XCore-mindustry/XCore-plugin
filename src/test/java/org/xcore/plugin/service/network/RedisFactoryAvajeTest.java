package org.xcore.plugin.service.network;

import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RedisFactoryAvajeTest {
    @Test
    void routerUsesTheAdapterProvidedToTheScope() {
        var adapter = mock(RedisProtocolRouteAdapter.class);
        when(adapter.isMutatingType(String.class)).thenReturn(true);
        try (var scope = BeanScope.builder().bean(RedisProtocolRouteAdapter.class, adapter)
                .modules(new AvajeModule() {
                    @Override
                    public Class<?>[] classes() { return new Class<?>[]{RedisFactory.class, RedisStreamRouter.class}; }

                    @Override
                    public void build(Builder builder) {
                        RedisFactory$DI.build(builder);
                        RedisFactory$DI.build_redisProtocolRouteAdapter(builder);
                        RedisFactory$DI.build_redisStreamRouter(builder);
                    }
                }).build()) {
            assertThat(scope.get(RedisStreamRouter.class).isMutatingType(String.class)).isTrue();
            verify(adapter).isMutatingType(String.class);
        }
    }
}
