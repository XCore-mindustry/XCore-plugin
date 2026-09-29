package org.xcore.plugin.security.ingress;

import org.xcore.plugin.common.PLog;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.config.TomlXcoreConfig;
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

    /**
     * Sent when a check could not reach a verdict. Silent, because a connection that never
     * completed validation has nobody to explain itself to.
     */
    private static final String UNAVAILABLE_REASON = "Security check unavailable";

    private final List<IngressCheck> fastChecks = new CopyOnWriteArrayList<>();
    private final List<IngressCheck> slowChecks = new CopyOnWriteArrayList<>();
    private final ExecutorService virtualExecutor;
    private final MetricsService metricsService;
    private final TomlXcoreConfig config;
    private final long handshakeBudgetNanos;

    public IngressService(List<IngressCheck> checks, MetricsService metricsService, TomlXcoreConfig config) {
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.metricsService = metricsService;
        this.config = config;
        this.handshakeBudgetNanos = TimeUnit.MILLISECONDS.toNanos(config.server.ingressHandshakeBudgetMillis);

        checks.forEach(this::attach);

        PLog.infoTag("Ingress", "Ready: @ fast checks, @ slow checks (budget @ms, failure-mode @)",
                fastChecks.size(), slowChecks.size(),
                config.server.ingressHandshakeBudgetMillis, config.server.ingressFailureMode);
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
                PLog.errTag("Ingress", "'@' error, failure-mode=@", check.name(), failureMode(check));
                PLog.errTag("Ingress", e);
                if (failsClosed(check)) {
                    return recordFailure(check);
                }
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
                    PLog.errTag("Ingress", "'@' error, failure-mode=@", check.name(), failureMode(check));
                    PLog.errTag("Ingress", e);
                    return new CheckOutcome(check, failsClosed(check)
                            ? recordFailure(check)
                            : AccessResult.Allowed.INSTANCE);
                }
            }));
        }

        // One absolute deadline for the whole handshake. A per-check timeout, multiplied by the
        // check count and re-entered once per connecting client, let a connection wave hold the
        // tick thread for as long as the wave kept arriving.
        final long deadline = System.nanoTime() + handshakeBudgetNanos;
        boolean budgetExceeded = false;

        try {
            for (int i = 0; i < checks.size(); i++) {
                long remaining = deadline - System.nanoTime();
                Future<CheckOutcome> completedFuture = remaining > 0
                        ? completionService.poll(remaining, TimeUnit.NANOSECONDS)
                        : null;

                if (completedFuture == null) {
                    budgetExceeded = true;
                    break;
                }

                CheckOutcome outcome = completedFuture.get();
                if (outcome.result() instanceof AccessResult.Denied denied && deniedResult == null) {
                    recordDenied(outcome.check(), denied);
                    deniedResult = outcome;
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

        // A verdict we did receive outranks the budget: a definitive deny must not be softened
        // into an allow just because a sibling check overran.
        if (deniedResult != null) {
            return deniedResult.result();
        }

        if (budgetExceeded) {
            return onBudgetExceeded();
        }

        return AccessResult.Allowed.INSTANCE;
    }

    /**
     * Resolved posture for a check. The operator's global override wins outright, so a server
     * that prefers availability can opt out of fail-closed without touching every check.
     */
    private FailureMode failureMode(IngressCheck check) {
        return config.server.ingressFailsOpen() ? FailureMode.FAIL_OPEN : check.failureMode();
    }

    private boolean failsClosed(IngressCheck check) {
        return failureMode(check) == FailureMode.FAIL_CLOSED;
    }

    private AccessResult recordFailure(IngressCheck check) {
        AccessResult.Denied denied = new AccessResult.Denied(UNAVAILABLE_REASON, true);
        recordDenied(check, denied);
        return denied;
    }

    private AccessResult onBudgetExceeded() {
        metricsService.increment(
                XcoreMetrics.INGRESS_HANDSHAKE_BUDGET_EXCEEDED_TOTAL,
                Tags.empty()
        );
        if (config.server.ingressFailsOpen()) {
            PLog.warnTag("Ingress", "Handshake budget exceeded, admitting connection (failure-mode=open)");
            return AccessResult.Allowed.INSTANCE;
        }
        PLog.warnTag("Ingress", "Handshake budget exceeded, denying connection (failure-mode=closed)");
        return new AccessResult.Denied(UNAVAILABLE_REASON, true);
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
