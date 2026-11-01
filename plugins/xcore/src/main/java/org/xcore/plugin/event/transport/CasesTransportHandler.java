package org.xcore.plugin.event.transport;

import arc.util.Log;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import org.xcore.plugin.cases.CaseEvidenceService;
import org.xcore.plugin.cases.CaseNoticeService;
import org.xcore.plugin.cases.CasesFeature;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.event.TransportSubscriptions;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.CasesProtocolMapper;
import org.xcore.plugin.startup.ShutdownOrder;
import org.xcore.protocol.generated.messages.cases.CasesMessages.CasesEvidenceRequestV1;
import org.xcore.protocol.generated.messages.cases.CasesMessages.CasesNoticeCommandV1;

import java.util.concurrent.atomic.AtomicInteger;

/** Redis callbacks carry only immutable request data across the game-thread hop. */
@Singleton
public class CasesTransportHandler {
    private static final int MAX_IN_FLIGHT = 64;
    private final NetworkService network;
    private final TomlXcoreConfig config;
    private final TomlSecretsConfig secrets;
    private final CasesFeature feature;
    private final CaseNoticeService notices;
    private final CaseEvidenceService evidence;
    private final Async async;
    private final TransportSubscriptions subscriptions;
    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile boolean stopped;

    public CasesTransportHandler(NetworkService network, TomlXcoreConfig config, TomlSecretsConfig secrets,
                                 CasesFeature feature, CaseNoticeService notices, CaseEvidenceService evidence,
                                 Async async) {
        this.network = network;
        this.config = config;
        this.secrets = secrets;
        this.feature = feature;
        this.notices = notices;
        this.evidence = evidence;
        this.async = async;
        subscriptions = new TransportSubscriptions(network);
    }

    public synchronized void registerListeners() {
        subscriptions.clear();
        // Always subscribe: the feature can be toggled at runtime, and handlers check it per message.
        if (stopped) return;
        subscriptions.subscribe(CasesNoticeCommandV1.class, this::notice);
        subscriptions.subscribe(CasesEvidenceRequestV1.class, this::evidence);
    }

    void notice(CasesNoticeCommandV1 request) {
        if (stopped || !feature.enabled() || !request.server().equals(config.server.name)) return;
        if (!acquire()) return;
        try {
            async.main(() -> {
                try {
                    if (!stopped && feature.enabled()) notices.wakeUp(request.playerUuid(), request.noticeId());
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RuntimeException error) {
            inFlight.decrementAndGet();
            Log.warn("Could not dispatch case notice wake-up: @", error.toString());
        }
    }

    void evidence(CasesEvidenceRequestV1 request) {
        if (!request.server().equals(config.server.name) || stopped) return;
        if (!feature.enabled()) {
            network.respondError(request, "REJECTED", "Reports are disabled");
            return;
        }
        if (!acquire()) {
            network.respondError(request, "REJECTED", "Evidence capture is busy");
            return;
        }
        try {
            async.main(() -> capture(request));
        } catch (RuntimeException error) {
            inFlight.decrementAndGet();
            network.respondError(request, "FAILED", "Evidence could not be dispatched");
        }
    }

    private void capture(CasesEvidenceRequestV1 request) {
        try {
            if (stopped || !feature.enabled()) {
                replyError(request, "REJECTED", "Reports are disabled");
                return;
            }
            var settings = secrets.cases.reports;
            var captured = evidence.capture(request.playerUuid(), null,
                    request.chatLines() == null ? settings.chatLines : Math.clamp(request.chatLines(), 1, 30),
                    request.windowSeconds() == null ? Math.clamp(settings.chatWindowMinutes, 0, 15) * 60
                            : Math.clamp(request.windowSeconds(), 60, 1800),
                    request.windowSeconds() == null ? settings.blockWindowSeconds : Math.clamp(request.windowSeconds(), 60, 1800));
            var response = CasesProtocolMapper.toEvidence(config.server.name, captured);
            async.run(() -> {
                try {
                    network.respond(request, response);
                } catch (RuntimeException error) {
                    Log.warn("Could not publish case evidence response: @", error.toString());
                }
            });
        } catch (RuntimeException error) {
            replyError(request, "FAILED", "Evidence could not be captured");
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private boolean acquire() {
        if (inFlight.incrementAndGet() <= MAX_IN_FLIGHT) return true;
        inFlight.decrementAndGet();
        return false;
    }

    private void replyError(CasesEvidenceRequestV1 request, String code, String message) {
        try {
            async.run(() -> network.respondError(request, code, message));
        } catch (RuntimeException error) {
            Log.warn("Could not publish case evidence error: @", error.toString());
        }
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public synchronized void stop() {
        stopped = true;
        subscriptions.close();
    }
}
