package org.xcore.plugin.cases;

import arc.util.Log;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.startup.ShutdownOrder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class CaseNoticeService {
    private static final int MAX_IN_FLIGHT = 512;
    private static final Set<String> KINDS = Set.of("report_received", "report_resolved", "report_rejected",
            "report_not_accepted", "appeal_accepted", "appeal_shortened", "appeal_unmuted", "appeal_mute_shortened",
            "badge_granted", "badge_rejected",
            "map_accepted", "map_rejected", "complaint_closed");
    /** Rejection reason codes of a report; the bundle selects the reporter-facing sentence by them. */
    private static final Set<String> REPORT_REJECTION_REASONS = Set.of("no_violation", "no_evidence",
            "already_handled", "duplicate", "abuse");
    private final CasesFeature feature;
    private final CaseNoticeRepository repository;
    private final TomlSecretsConfig secrets;
    private final TomlXcoreConfig config;
    private final SessionService sessions;
    private final Async async;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean stopped;

    public CaseNoticeService(CasesFeature feature, CaseNoticeRepository repository, TomlSecretsConfig secrets,
                             TomlXcoreConfig config, SessionService sessions, Async async) {
        this.feature = feature;
        this.repository = repository;
        this.secrets = secrets;
        this.config = config;
        this.sessions = sessions;
        this.async = async;
    }

    /** Worker only. Failures cannot prevent a normal game login. Does not acquire leases. */
    public List<CaseNotice> prepareJoin(String uuid) {
        if (!enabled() || secrets.cases.notices.maxPerJoin <= 0) return List.of();
        try {
            return repository.findPending(uuid, Math.clamp(secrets.cases.notices.maxPerJoin, 1, 100));
        } catch (RuntimeException error) {
            Log.warn("Could not load case notices: @", error.toString());
            return List.of();
        }
    }

    /** Game thread, after the session is registered. A disconnect before this point acquires no claim. */
    public void completeJoin(Session session, List<CaseNotice> pending) {
        GameThread.require("CaseNoticeService.completeJoin");
        if (!enabled() || session == null || session.data == null) return;
        pending.stream().limit(Math.clamp(secrets.cases.notices.maxPerJoin, 0, 100))
                .filter(notice -> notice.playerUuid().equals(session.data.uuid))
                .forEach(notice -> deliver(session, notice.id()));
    }

    /** Game thread, after transport has marshalled the wake-up. */
    public void wakeUp(String uuid, String noticeId) {
        GameThread.require("CaseNoticeService.wakeUp");
        if (!enabled()) return;
        var session = sessions.getActive(uuid);
        if (session != null) deliver(session, noticeId);
    }

    private void deliver(Session session, String id) {
        if (session.data == null || !Async.isPlayerOnline(session.player)
                || sessions.getActive(session.data.uuid) != session
                || inFlight.size() >= MAX_IN_FLIGHT || !inFlight.add(id)) return;
        String uuid = session.data.uuid;
        try {
            async.supply(() -> enabled() ? repository.claim(id, uuid, config.server.name) : null)
                    .thenMain((notice, error) -> {
                        if (error != null || notice == null) {
                            inFlight.remove(id);
                            if (error != null) Log.warn("Could not claim case notice: @", error.toString());
                            return;
                        }
                        boolean shown = false;
                        try {
                            if (enabled() && sessions.getActive(uuid) == session && Async.isPlayerOnline(session.player)
                                    && notice.claimUntil() != null && Instant.now().isBefore(notice.claimUntil())
                                    && KINDS.contains(notice.kind()) && session.locale() != null) {
                                session.locale().send(notice.bundleKey(), arguments(notice));
                                shown = true;
                            }
                        } catch (RuntimeException failure) {
                            Log.warn("Could not show case notice: @", failure.toString());
                        }
                        finish(notice, shown);
                    }).exceptionally(error -> {
                        inFlight.remove(id);
                        // If the main dispatcher is gone, the durable lease expires for the next join.
                        Log.warn("Could not dispatch case notice: @", error.toString());
                        return null;
                    });
        } catch (RuntimeException rejected) {
            inFlight.remove(id);
            Log.warn("Could not schedule case notice: @", rejected.toString());
        }
    }

    static HashMap<String, Object> arguments(CaseNotice notice) {
        var args = new HashMap<String, Object>();
        // Only bundle arguments; arbitrary identifiers/network fields from params never reach chat.
        for (String key : List.of("number", "badge", "map")) {
            String value = notice.params().get(key);
            if (value == null) continue;
            if (key.equals("number")) {
                args.put(key, Long.parseLong(value));
            } else {
                args.put(key, CaseText.plain(value, 256));
            }
        }
        if (notice.kind().equals("report_rejected")) {
            // A known code only selects a bundle variant. Anything else, or no reason at all, becomes the
            // constant that selects the generic sentence: the selector must always have a value.
            String reason = notice.params().get("reason");
            args.put("reason", reason != null && REPORT_REJECTION_REASONS.contains(reason) ? reason : "other");
        }
        return args;
    }

    private void finish(CaseNotice notice, boolean shown) {
        try {
            async.supply(() -> {
                try {
                    if (shown) {
                        if (!repository.acknowledge(notice, config.server.name)) {
                            Log.warn("Case notice acknowledgement lost its lease");
                        }
                    } else {
                        repository.release(notice);
                    }
                } finally {
                    inFlight.remove(notice.id());
                }
                return null;
            }).future().exceptionally(error -> {
                inFlight.remove(notice.id());
                Log.warn("Case notice persistence failed: @", error.toString());
                return null;
            });
        } catch (RuntimeException rejected) {
            inFlight.remove(notice.id());
            Log.warn("Could not persist case notice delivery: @", rejected.toString());
        }
    }

    private boolean enabled() {
        return !stopped && feature.enabled();
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        stopped = true;
        inFlight.clear();
    }
}
