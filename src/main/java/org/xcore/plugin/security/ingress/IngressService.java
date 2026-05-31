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

/**
 * Orchestrates all ingress security checks for incoming connections.
 */
@Singleton
public class IngressService {

    private final List<IngressCheck> fastChecks;
    private final List<IngressCheck> slowChecks;
    private final ExecutorService virtualExecutor;
    private final MetricsService metricsService;

    public IngressService(List<IngressCheck> checks, MetricsService metricsService) {
        List<IngressCheck> sorted = checks.stream()
                .sorted(Comparator.comparingInt(IngressCheck::priority))
                .toList();

        this.fastChecks = sorted.stream()
                .filter(c -> c.priority() < 0)
                .toList();

        this.slowChecks = sorted.stream()
                .filter(c -> c.priority() >= 0)
                .toList();

        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.metricsService = metricsService;

        PLog.infoTag("Ingress", "Ready: @ fast checks, @ slow checks",
                fastChecks.size(), slowChecks.size());
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
        List<Future<CheckOutcome>> futures = new ArrayList<>(slowChecks.size());
        CheckOutcome deniedResult = null;

        for (IngressCheck check : slowChecks) {
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
            for (int i = 0; i < slowChecks.size(); i++) {
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
