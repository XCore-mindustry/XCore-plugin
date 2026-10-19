package org.xcore.plugin.service;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
        RedisNetworkBackend backend = backend(redisCommands(store, setArgsRef));

        TomlXcoreConfig config = config("mini-pvp");
        config.translation.cache.ttlSeconds = 42;
        TranslationCacheService service = new TranslationCacheService(backend, new Gson(), config);

        assertThat(join(service.putAsync("auto", "ru", "hello", "google", "привет", "pipeline"))).isTrue();

        TranslationCacheService.CachedTranslation cached = join(service.getAsync("auto", "ru", "hello", "google"));
        assertThat(cached).isNotNull();
        assertThat(cached.translatedText()).isEqualTo("привет");
        assertThat(cached.providerId()).isEqualTo("pipeline");
        assertThat(secondsTtl(setArgsRef.get())).isEqualTo(42L);
    }

    @Test
    @DisplayName("cache key is shared across servers and hashes the normalized payload")
    void cacheKey_isSharedAcrossServers() {
        Map<String, String> store = new HashMap<>();
        RedisNetworkBackend backend = backend(redisCommands(store, new AtomicReference<>()));
        TranslationCacheService alpha = new TranslationCacheService(backend, new Gson(), config("alpha"));
        TranslationCacheService beta = new TranslationCacheService(backend, new Gson(), config("beta"));

        assertThat(join(alpha.putAsync(" EN ", "Ru", "Hello", " OpenAI ", "Привет", "pipeline"))).isTrue();

        String expectedKey = "xcore:translation:cache:v2:" + sha256("en|ru|openai|Hello");
        assertThat(store).containsOnlyKeys(expectedKey);
        assertThat(join(beta.getAsync("en", "ru", "Hello", "openai")).translatedText()).isEqualTo("Привет");
    }

    @Test
    @DisplayName("disabled translation or cache skips backend access")
    void disabledTranslationOrCache_skipsBackendAccess() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);

        TomlXcoreConfig translationDisabled = config("mini-pvp");
        translationDisabled.translation.enabled = false;
        TranslationCacheService translationDisabledService = new TranslationCacheService(backend, new Gson(), translationDisabled);

        assertThat(join(translationDisabledService.getAsync("auto", "ru", "hello", "google"))).isNull();
        assertThat(join(translationDisabledService.putAsync("auto", "ru", "hello", "google", "привет", "pipeline"))).isFalse();

        TomlXcoreConfig cacheDisabled = config("mini-pvp");
        cacheDisabled.translation.cache.enabled = false;
        TranslationCacheService cacheDisabledService = new TranslationCacheService(backend, new Gson(), cacheDisabled);

        assertThat(join(cacheDisabledService.getAsync("auto", "ru", "hello", "google"))).isNull();
        assertThat(join(cacheDisabledService.putAsync("auto", "ru", "hello", "google", "привет", "pipeline"))).isFalse();

        verifyNoInteractions(backend);
    }

    @Test
    @DisplayName("over-limit input is not cacheable")
    void overLimitInput_isNotCacheable() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        TomlXcoreConfig config = config("mini-pvp");
        config.translation.cache.maxTextLength = 4;
        TranslationCacheService service = new TranslationCacheService(backend, new Gson(), config);

        assertThat(join(service.getAsync("auto", "ru", "hello", "google"))).isNull();
        assertThat(join(service.putAsync("auto", "ru", "hello", "google", "привет", "pipeline"))).isFalse();

        verifyNoInteractions(backend);
    }

    @Test
    @DisplayName("backend unavailable returns service fallbacks")
    void backendUnavailable_returnsServiceFallbacks() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.withAsyncCommands(any(Function.class), any())).thenAnswer(invocation ->
                CompletableFuture.completedFuture(invocation.getArgument(1)));
        TranslationCacheService service = new TranslationCacheService(backend, new Gson(), config("mini-pvp"));

        assertThat(join(service.getAsync("auto", "ru", "hello", "google"))).isNull();
        assertThat(join(service.putAsync("auto", "ru", "hello", "google", "привет", "pipeline"))).isFalse();
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    @SuppressWarnings("unchecked")
    private static RedisNetworkBackend backend(RedisAsyncCommands<String, String> commands) {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.withAsyncCommands(any(Function.class), any())).thenAnswer(invocation -> {
            Function<RedisAsyncCommands<String, String>, CompletionStage<Object>> operation = invocation.getArgument(0);
            return operation.apply(commands);
        });
        return backend;
    }

    @SuppressWarnings("unchecked")
    private static RedisAsyncCommands<String, String> redisCommands(Map<String, String> store,
                                                                     AtomicReference<SetArgs> setArgsRef) {
        RedisAsyncCommands<String, String> commands = mock(RedisAsyncCommands.class);
        when(commands.get(anyString())).thenAnswer(invocation -> redisFuture(store.get(invocation.getArgument(0))));
        when(commands.set(anyString(), anyString(), any(SetArgs.class))).thenAnswer(invocation -> {
            setArgsRef.set(invocation.getArgument(2));
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return redisFuture("OK");
        });
        return commands;
    }

    @SuppressWarnings("unchecked")
    private static <T> RedisFuture<T> redisFuture(T value) {
        RedisFuture<T> future = mock(RedisFuture.class);
        when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(value));
        return future;
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
