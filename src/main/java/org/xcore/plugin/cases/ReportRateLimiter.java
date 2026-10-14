package org.xcore.plugin.cases;

import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.startup.ShutdownOrder;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Local sliding windows. Reservations count immediately and can be returned after a failed publish. */
@Singleton
public class ReportRateLimiter {
    static final int CAPACITY = 10_000;
    private static final long DAY = 24 * 60 * 60_000L;
    private static final long BURST = 10 * 60_000L;
    private final TomlSecretsConfig config;
    private final LongSupplier clock;
    private final ArrayList<Reservation> slots = new ArrayList<>();
    private boolean stopped;

    @Inject
    public ReportRateLimiter(TomlSecretsConfig config) {
        this(config, System::currentTimeMillis);
    }

    ReportRateLimiter(TomlSecretsConfig config, LongSupplier clock) {
        this.config = config;
        this.clock = clock;
    }

    public synchronized Attempt reserve(String reporter, String target, String address) {
        if (stopped) return new Attempt(null, new ReportResult.Disabled());
        var settings = config.cases.reports;
        long now = clock.getAsLong();
        long cooldown = settings.cooldownSeconds * 1000L;
        long sameTarget = settings.sameTargetCooldownMinutes * 60_000L;
        long retention = Math.max(DAY, Math.max(cooldown, sameTarget));
        slots.removeIf(slot -> now - slot.at >= retention);
        var own = slots.stream().filter(slot -> slot.reporter.equals(reporter)).toList();
        long remaining = own.stream().mapToLong(slot -> slot.at + cooldown - now).max().orElse(0);
        if (remaining > 0) return new Attempt(null, new ReportResult.Cooldown(seconds(remaining)));
        if (own.stream().anyMatch(slot -> slot.target.equals(target) && now - slot.at < sameTarget)) {
            return new Attempt(null, new ReportResult.SameTarget());
        }
        if (settings.dailyLimit > 0 && own.stream().filter(slot -> now - slot.at < DAY).count() >= settings.dailyLimit) {
            return new Attempt(null, new ReportResult.DailyLimit());
        }
        if (settings.networkBurstLimit > 0 && address != null && !address.isBlank()) {
            var network = slots.stream().filter(slot -> Objects.equals(slot.address, address) && now - slot.at < BURST).toList();
            if (network.size() >= settings.networkBurstLimit) {
                long next = network.stream().mapToLong(slot -> slot.at + BURST - now).min().orElse(0);
                return new Attempt(null, new ReportResult.Cooldown(seconds(next)));
            }
        }
        // Refuse rather than evict unexpired allowances and silently bypass limits.
        if (slots.size() == CAPACITY) return new Attempt(null, new ReportResult.Failed());
        var slot = new Reservation(reporter, target, address, now);
        slots.add(slot);
        return new Attempt(slot, null);
    }

    public synchronized void release(Reservation reservation) {
        slots.remove(reservation);
    }

    private static long seconds(long millis) {
        return (millis + 999) / 1000;
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public synchronized void stop() {
        stopped = true;
        slots.clear();
    }

    public record Attempt(Reservation reservation, ReportResult refusal) {}

    /** Identity, not value equality: a failed attempt cannot return another attempt's slot. */
    public static final class Reservation {
        private final String reporter;
        private final String target;
        private final String address;
        private final long at;

        private Reservation(String reporter, String target, String address, long at) {
            this.reporter = reporter;
            this.target = target;
            this.address = address;
            this.at = at;
        }
    }
}
