package org.xcore.plugin.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.MongoClientSettings;
import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MongoFactoryAvajeTest {
    @Test
    void generatedFactoryClosesClientExactlyOnce() {
        var client = mock(MongoClient.class);
        var config = new TomlSecretsConfig();
        config.database.mongoConnectionString = "mongodb://localhost:27017";
        try (var clients = mockStatic(MongoClients.class)) {
            clients.when(() -> MongoClients.create(any(MongoClientSettings.class))).thenReturn(client);
            var scope = BeanScope.builder().bean(TomlSecretsConfig.class, config)
                    .modules(new AvajeModule() {
                        @Override
                        public Class<?>[] classes() { return new Class<?>[]{MongoFactory.class, MongoClient.class}; }

                        @Override
                        public void build(Builder builder) {
                            MongoFactory$DI.build(builder);
                            MongoFactory$DI.build_mongoClient(builder);
                        }
                    }).build();

            scope.close();
            scope.close();

            verify(client).close();
            verifyNoMoreInteractions(client);
        }
    }
}
