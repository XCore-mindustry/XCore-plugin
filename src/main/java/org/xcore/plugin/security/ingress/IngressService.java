package org.xcore.plugin.security.ingress;

import org.xcore.plugin.common.PLog;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.metrics.Tags;
import org.xcore.plugin.metrics.XcoreMetrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Orchestrates all ingress security checks for incoming connections.
 * <p>
 * Built-in checks are injected via DI; companion plugins may attach their own
 * checks at runtime through {@link #register(IngressCheck)}.
 */
@Singleton
public class IngressService {

    private final List<IngressCheck> fastChecks = new CopyOnWriteArrayList<>();
    private final List<IngressCheck> slowChecks = new CopyOnWriteArrayList<>();
    private final ExecutorService virtualExecutor;
    private final MetricsService metricsService;

    public IngressService(List<IngressCheck> checks, MetricsService metricsService) {
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.metricsService = metricsService;

        checks.forEach(this::attach);

        PLog.infoTag("Ingress", "Ready: @ fast checks, @ slow checks",
                fastChecks.size(), slowChecks.size());
    }

    /**
     * Registers an externally provided check (e.g., from a companion plugin).
     * Thread-safe; affects subsequent validations only.
     */
    public synchronized void register(IngressCheck check) {
        attach(check);
        PLog.infoTag("Ingress", "Registered external check '@' (@)",
                check.name(), check.priority() < 0 ? "fast" : "slow");
    }

    /**
     * Unregisters a previously registered check.
     * Thread-safe; no-op if the check was never registered.
     */
    public synchronized void unregister(IngressCheck check) {
        if (fastChecks.remove(check) | slowChecks.remove(check)) {
            PLog.infoTag("Ingress", "Unregistered check '@'", check.name());
        }
    }

    private void attach(IngressCheck check) {
        (check.priority() < 0 ? fastChecks : slowChecks).add(check);
        fastChecks.sort(Comparator.comparingInt(IngressCheck::priority));
        slowChecks.sort(Comparator.comparingInt(IngressCheck::priority));
    }

    @PreDestroy
    void shutdown() {
        virtualExecutor.shutdownNow();
    }

    public AccessResult validate(NetConnection con, ConnectPacket packet) {
        for (IngressCheck check : fastChecks) {
            try {
                AccessResult result = check.check(con, packet);
                if (result instanceof AccessResult.Denied denied) {
                    recordDenied(check, denied);
                    PLog.debugTag("Ingress", "'@' denied: @", check.name(), denied.reason());
                    return denied;
                }
            } catch (Exception e) {
                recordCheckError(check, "fast");
                PLog.errTag("Ingress", "'@' error", check.name());
                PLog.errTag("Ingress", e);
            }
        }

        if (!slowChecks.isEmpty()) {
            return runParallelChecks(con, packet);
        }

        return AccessResult.Allowed.INSTANCE;
    }

    private AccessResult runParallelChecks(NetConnection con, ConnectPacket packet) {
        CompletionService<CheckOutcome> completionService = new ExecutorCompletionService<>(virtualExecutor);
        List<IngressCheck> checks = List.copyOf(slowChecks);
        List<Future<CheckOutcome>> futures = new ArrayList<>(checks.size());
        CheckOutcome deniedResult = null;

        for (IngressCheck check : checks) {
            futures.add(completionService.submit(() -> {
                try {
                    return new CheckOutcome(check, check.check(con, packet));
                } catch (Exception e) {
                    recordCheckError(check, "slow");
                    PLog.err("[Ingress] '@' error", check.name());
                    PLog.err(e);
                    return new CheckOutcome(check, AccessResult.Allowed.INSTANCE);
                }
            }));
        }

        try {
            for (int i = 0; i < checks.size(); i++) {
                Future<CheckOutcome> completedFuture = completionService.poll(5, TimeUnit.SECONDS);

                if (completedFuture != null) {
                    CheckOutcome outcome = completedFuture.get();
                    if (outcome.result() instanceof AccessResult.Denied denied) {
                        if (deniedResult == null) {
                            recordDenied(outcome.check(), denied);
                            deniedResult = outcome;
                        }
                    }
                } else {
                    PLog.warnTag("Ingress", "A parallel check timed out");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AccessResult.Denied("Interrupted", true);
        } catch (ExecutionException e) {
            PLog.errTag("Ingress", "Check execution failed", e);
        } finally {
            cancelRemaining(futures);
        }

        if (deniedResult != null) {
            return deniedResult.result();
        }

        return AccessResult.Allowed.INSTANCE;
    }

    private void cancelRemaining(List<Future<CheckOutcome>> futures) {
        for (Future<CheckOutcome> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private void recordDenied(IngressCheck check, AccessResult.Denied denied) {
        metricsService.increment(
                XcoreMetrics.INGRESS_DENIALS_TOTAL,
                Tags.of("check", check.name(), "silent", Boolean.toString(denied.silent()))
        );
    }

    private void recordCheckError(IngressCheck check, String phase) {
        metricsService.increment(
                XcoreMetrics.INGRESS_CHECK_ERRORS_TOTAL,
                Tags.of("check", check.name(), "phase", phase)
        );
    }

    private record CheckOutcome(IngressCheck check, AccessResult result) {
    }
}
