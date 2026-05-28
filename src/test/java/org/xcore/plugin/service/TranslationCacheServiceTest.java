package org.xcore.plugin.service;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TranslationCacheServiceTest {

    @Test
    @DisplayName("put and get round trip stores cached translation with configured ttl")
    void putAndGet_roundTripStoresCachedTranslationWithConfiguredTtl() {
        Map<String, String> store = new HashMap<>();
        AtomicReference<SetArgs> setArgsRef = new AtomicReference<>();
        RedisCommands<String, String> commands = redisCommands(store, setArgsRef);
        RedisNetworkBackend backend = backend(commands);

        TomlXcoreConfig config = config("mini-pvp");
        config.translation.cache.ttlSeconds = 42;
        TranslationCacheService service = new TranslationCacheService(backend, new Gson(), config);

        assertThat(service.put("auto", "ru", "hello", "google", "привет", "pipeline")).isTrue();

        TranslationCacheService.CachedTranslation cached = service.get("auto", "ru", "hello", "google");
        assertThat(cached).isNotNull();
        assertThat(cached.translatedText()).isEqualTo("привет");
        assertThat(cached.providerId()).isEqualTo("pipeline");
        assertThat(secondsTtl(setArgsRef.get())).isEqualTo(42L);
    }

    @Test
    @DisplayName("cache key includes server name and normalized hashed payload")
    void cacheKey_includesServerNameAndNormalizedHashedPayload() {
        Map<String, String> store = new HashMap<>();
        RedisCommands<String, String> commands = redisCommands(store, new AtomicReference<>());
        RedisNetworkBackend backend = backend(commands);
        TranslationCacheService service = new TranslationCacheService(backend, new Gson(), config("alpha"));

        assertThat(service.put(" EN ", "Ru", "Hello", " OpenAI ", "Привет", "pipeline")).isTrue();

        String payload = "en|ru|openai|Hello";
        String expectedKey = "xcore:translation:cache:alpha:" + sha256(payload);
        assertThat(store).containsKey(expectedKey);
    }

    @Test
    @DisplayName("disabled translation or cache skips backend access")
    void disabledTranslationOrCache_skipsBackendAccess() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);

        TomlXcoreConfig translationDisabled = config("mini-pvp");
        translationDisabled.translation.enabled = false;
        TranslationCacheService translationDisabledService = new TranslationCacheService(backend, new Gson(), translationDisabled);

        assertThat(translationDisabledService.get("auto", "ru", "hello", "google")).isNull();
        assertThat(translationDisabledService.put("auto", "ru", "hello", "google", "привет", "pipeline")).isFalse();

        TomlXcoreConfig cacheDisabled = config("mini-pvp");
        cacheDisabled.translation.cache.enabled = false;
        TranslationCacheService cacheDisabledService = new TranslationCacheService(backend, new Gson(), cacheDisabled);

        assertThat(cacheDisabledService.get("auto", "ru", "hello", "google")).isNull();
        assertThat(cacheDisabledService.put("auto", "ru", "hello", "google", "привет", "pipeline")).isFalse();

        verifyNoInteractions(backend);
    }

    @Test
    @DisplayName("over-limit input is not cacheable")
    void overLimitInput_isNotCacheable() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        TomlXcoreConfig config = config("mini-pvp");
        config.translation.cache.maxTextLength = 4;
        TranslationCacheService service = new TranslationCacheService(backend, new Gson(), config);

        assertThat(service.get("auto", "ru", "hello", "google")).isNull();
        assertThat(service.put("auto", "ru", "hello", "google", "привет", "pipeline")).isFalse();

        verifyNoInteractions(backend);
    }

    @Test
    @DisplayName("backend failure returns service fallbacks")
    void backendFailure_returnsServiceFallbacks() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.withCommands(any(Function.class), any())).thenAnswer(invocation -> invocation.getArgument(1));
        TranslationCacheService service = new TranslationCacheService(backend, new Gson(), config("mini-pvp"));

        assertThat(service.get("auto", "ru", "hello", "google")).isNull();
        assertThat(service.put("auto", "ru", "hello", "google", "привет", "pipeline")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static RedisNetworkBackend backend(RedisCommands<String, String> commands) {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.withCommands(any(Function.class), any())).thenAnswer(invocation -> {
            Function<RedisCommands<String, String>, Object> operation = invocation.getArgument(0);
            return operation.apply(commands);
        });
        return backend;
    }

    @SuppressWarnings("unchecked")
    private static RedisCommands<String, String> redisCommands(Map<String, String> store,
                                                                AtomicReference<SetArgs> setArgsRef) {
        RedisCommands<String, String> commands = mock(RedisCommands.class);
        when(commands.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            SetArgs setArgs = invocation.getArgument(2);
            setArgsRef.set(setArgs);
            store.put(key, value);
            return "OK";
        }).when(commands).set(anyString(), anyString(), any(SetArgs.class));
        return commands;
    }

    private static TomlXcoreConfig config(String server) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = server;
        return config;
    }

    private static long secondsTtl(SetArgs setArgs) {
        try {
            Field exField = SetArgs.class.getDeclaredField("ex");
            exField.setAccessible(true);
            return ((Number) exField.get(setArgs)).longValue();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect Redis TTL", e);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
