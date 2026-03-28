package org.xcore.plugin.service;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Singleton
public class TranslationCacheService {

    private final RedisNetworkBackend backend;
    private final Gson redisGson;
    private final Config config;

    @Inject
    public TranslationCacheService(RedisNetworkBackend backend, @Named("redis") Gson redisGson, Config config) {
        this.backend = backend;
        this.redisGson = redisGson;
        this.config = config;
    }

    public CachedTranslation get(String sourceLanguage,
                                 String targetLanguage,
                                 String inputText,
                                 String pipelineSignature) {
        if (!isEnabled() || !isCacheable(inputText)) {
            return null;
        }

        return backend.withCommands(commands -> {
            String payloadJson = commands.get(cacheKey(sourceLanguage, targetLanguage, inputText, pipelineSignature));
            if (payloadJson == null || payloadJson.isBlank()) {
                return null;
            }

            return redisGson.fromJson(payloadJson, CachedTranslation.class);
        }, null);
    }

    public boolean put(String sourceLanguage,
                       String targetLanguage,
                       String inputText,
                       String pipelineSignature,
                       String translatedText,
                       String providerId) {
        if (!isEnabled() || !isCacheable(inputText) || translatedText == null || translatedText.isBlank()) {
            return false;
        }

        CachedTranslation payload = new CachedTranslation(
                translatedText,
                providerId,
                System.currentTimeMillis()
        );

        return backend.withCommands(commands -> {
            commands.set(
                    cacheKey(sourceLanguage, targetLanguage, inputText, pipelineSignature),
                    redisGson.toJson(payload),
                    SetArgs.Builder.ex(config.translation.cache.ttlSeconds)
            );
            return true;
        }, false);
    }

    private boolean isEnabled() {
        return config.translation.enabled && config.translation.cache.enabled;
    }

    private boolean isCacheable(String inputText) {
        return inputText != null
                && !inputText.isBlank()
                && inputText.length() <= config.translation.cache.maxTextLength;
    }

    private String cacheKey(String sourceLanguage,
                            String targetLanguage,
                            String inputText,
                            String pipelineSignature) {
        String normalizedSource = normalize(sourceLanguage, "auto");
        String normalizedTarget = normalize(targetLanguage, "unknown");
        String normalizedPipeline = normalize(pipelineSignature, "default");
        String payload = normalizedSource + "|" + normalizedTarget + "|" + normalizedPipeline + "|" + inputText;

        return "xcore:translation:cache:" + config.server + ":" + sha256(payload);
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record CachedTranslation(
            String translatedText,
            String providerId,
            long createdAt
    ) {
    }
}
