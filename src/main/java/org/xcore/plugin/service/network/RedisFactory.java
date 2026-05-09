package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Named;

@Factory
public class RedisFactory {

    @Bean
    @Named("redis")
    public Gson redisGson() {
        return RedisNetworkBackend.createRedisGson();
    }

    @Bean
    public RedisProtocolRouteAdapter redisProtocolRouteAdapter() {
        return new RedisProtocolRouteAdapter();
    }

    @Bean
    public RedisStreamRouter redisStreamRouter() {
        return new RedisStreamRouter(redisProtocolRouteAdapter());
    }
}
