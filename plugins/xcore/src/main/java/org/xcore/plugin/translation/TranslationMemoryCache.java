package org.xcore.plugin.translation;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * First-level translation cache: a bounded LRU in the JVM, so the phrases a server repeats
 * all day never reach Redis or a provider. It also remembers the detected language of a text,
 * which lets later messages skip targets that are already the source language.
 */
@Singleton
public class TranslationMemoryCache {

    private final TomlXcoreConfig config;
    private final LongSupplier nanoTime;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > config.translation.cache.memoryEntries;
        }
    };

    @Inject
    public TranslationMemoryCache(TomlXcoreConfig config) {
        this(config, System::nanoTime);
    }

    TranslationMemoryCache(TomlXcoreConfig config, LongSupplier nanoTime) {
        this.config = config;
        this.nanoTime = nanoTime;
    }

    public String translation(String pipelineSignature, String targetLanguage, String text) {
        return get(translationKey(pipelineSignature, targetLanguage, text));
    }

    public void putTranslation(String pipelineSignature, String targetLanguage, String text, String translatedText) {
        put(translationKey(pipelineSignature, targetLanguage, text), translatedText);
    }

    public String detectedLanguage(String text) {
        return get("d\u0000" + text);
    }

    public void putDetectedLanguage(String text, String language) {
        put("d\u0000" + text, language);
    }

    private synchronized String get(String key) {
        if (!isEnabled()) {
            return null;
        }

        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }

        if (nanoTime.getAsLong() - entry.storedAt() > TimeUnit.SECONDS.toNanos(config.translation.cache.memoryTtlSeconds)) {
            entries.remove(key);
            return null;
        }

        return entry.value();
    }

    private synchronized void put(String key, String value) {
        if (!isEnabled() || value == null || value.isBlank()) {
            return;
        }

        entries.put(key, new Entry(value, nanoTime.getAsLong()));
    }

    private boolean isEnabled() {
        return config.translation.cache.enabled && config.translation.cache.memoryEntries > 0;
    }

    private static String translationKey(String pipelineSignature, String targetLanguage, String text) {
        return "t\u0000" + pipelineSignature + "\u0000" + targetLanguage + "\u0000" + text;
    }

    private record Entry(String value, long storedAt) {
    }
}
