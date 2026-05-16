package org.xcore.plugin.security.ingress;

import org.xcore.plugin.common.PLog;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;

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

    public IngressService(List<IngressCheck> checks) {
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
                    PLog.debugTag("Ingress", "'@' denied: @", check.name(), denied.reason());
                    return denied;
                }
            } catch (Exception e) {
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
        CompletionService<AccessResult> completionService = new ExecutorCompletionService<>(virtualExecutor);
        List<Future<AccessResult>> futures = new ArrayList<>(slowChecks.size());
        AccessResult.Denied deniedResult = null;

        for (IngressCheck check : slowChecks) {
            futures.add(completionService.submit(() -> {
                try {
                    return check.check(con, packet);
                } catch (Exception e) {
                    PLog.err("[Ingress] '@' error", check.name());
                    PLog.err(e);
                    return AccessResult.Allowed.INSTANCE;
                }
            }));
        }

        try {
            for (int i = 0; i < slowChecks.size(); i++) {
                Future<AccessResult> completedFuture = completionService.poll(5, TimeUnit.SECONDS);

                if (completedFuture != null) {
                    AccessResult result = completedFuture.get();
                    if (result instanceof AccessResult.Denied denied) {
                        if (deniedResult == null) {
                            deniedResult = denied;
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
            return deniedResult;
        }

        return AccessResult.Allowed.INSTANCE;
    }

    private void cancelRemaining(List<Future<AccessResult>> futures) {
        for (Future<AccessResult> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }
}
