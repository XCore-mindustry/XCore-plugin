package org.xcore.plugin.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Named;

import java.lang.reflect.Modifier;

@Factory
public class SerializationFactory {

    @Bean
    @Named("pretty")
    public Gson prettyGson() {
        return new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.TRANSIENT, Modifier.STATIC)
                .registerTypeAdapterFactory(new BiMapTypeAdapterFactory())
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .serializeNulls()
                .create();
    }

    @Bean
    @Named("raw")
    public Gson rawGson() {
        return new GsonBuilder()
                .registerTypeAdapterFactory(new BiMapTypeAdapterFactory())
                .serializeNulls()
                .create();
    }
}
