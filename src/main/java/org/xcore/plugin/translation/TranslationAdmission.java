package org.xcore.plugin.translation;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;

/**
 * Keeps translation load bounded: a token bucket per author (one token per translated
 * message) and a cap on provider calls in flight. Both reject instead of queueing, because a
 * queued chat translation is useless by the time it would run; the message is delivered
 * untranslated.
 */
@Singleton
public class TranslationAdmission {

    private static final int PRUNE_THRESHOLD = 512;

    private final TomlXcoreConfig config;
    private final LongSupplier nanoTime;
    private final Semaphore slots;
    private final Map<String, Bucket> buckets = new HashMap<>();

    @Inject
    public TranslationAdmission(TomlXcoreConfig config) {
        this(config, System::nanoTime);
    }

    TranslationAdmission(TomlXcoreConfig config, LongSupplier nanoTime) {
        this.config = config;
        this.nanoTime = nanoTime;
        this.slots = new Semaphore(config.translation.limits.maxConcurrentRequests);
    }

    public synchronized boolean tryAcquireAuthor(String authorKey) {
        long now = nanoTime.getAsLong();
        if (buckets.size() > PRUNE_THRESHOLD) {
            buckets.values().removeIf(bucket -> bucket.refill(now) >= burst());
        }

        Bucket bucket = buckets.computeIfAbsent(authorKey == null ? "" : authorKey, ignored -> new Bucket(burst(), now));
        if (bucket.refill(now) < 1.0d) {
            return false;
        }

        bucket.tokens -= 1.0d;
        return true;
    }

    public boolean tryAcquireSlot() {
        return slots.tryAcquire();
    }

    public void releaseSlot() {
        slots.release();
    }

    private int burst() {
        return config.translation.limits.authorBurst;
    }

    private final class Bucket {
        private double tokens;
        private long updatedAt;

        private Bucket(double tokens, long updatedAt) {
            this.tokens = tokens;
            this.updatedAt = updatedAt;
        }

        private double refill(long now) {
            double perNano = config.translation.limits.authorRefillPerMinute / 60_000_000_000d;
            tokens = Math.min(burst(), tokens + (now - updatedAt) * perNano);
            updatedAt = now;
            return tokens;
        }
    }
}
