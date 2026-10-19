package org.xcore.plugin.cases;

import arc.util.Log;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.CasesProtocolMapper;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.startup.ShutdownOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class ReportService {
    private final CasesFeature feature;
    private final TomlSecretsConfig secrets;
    private final TomlXcoreConfig config;
    private final SessionService sessions;
    private final RecentPlayers recent;
    private final CaseEvidenceService evidence;
    private final ReportRateLimiter limiter;
    private final NetworkService network;
    private final Async async;
    private volatile boolean stopped;

    public ReportService(CasesFeature feature, TomlSecretsConfig secrets, TomlXcoreConfig config,
                         SessionService sessions, RecentPlayers recent, CaseEvidenceService evidence,
                         ReportRateLimiter limiter, NetworkService network, Async async) {
        this.feature = feature;
        this.secrets = secrets;
        this.config = config;
        this.sessions = sessions;
        this.recent = recent;
        this.evidence = evidence;
        this.limiter = limiter;
        this.network = network;
        this.async = async;
    }

    public boolean enabled() {
        return !stopped && feature.enabled();
    }

    public List<ReportReason> reasons() {
        return List.of(ReportReason.values());
    }

    /** Game thread only; snapshots contain no addresses. UUIDs must stay in menu state, never rendered. */
    public List<ReportTarget> targets(Session reporter) {
        GameThread.require("ReportService.targets");
        if (!enabled() || reporter == null || reporter.data == null) return List.of();
        var targets = new ArrayList<ReportTarget>();
        if (Groups.player != null) {
            for (var player : Groups.player) {
                var session = sessions.getActive(player);
                if (session == null || session.data == null || session.player != player || !Async.isPlayerOnline(player)
                        || isStaff(session) || player.uuid().equals(reporter.data.uuid)) continue;
                targets.add(target(session));
            }
        }
        for (var left : recent.snapshot()) {
            if (!left.staff() && !left.target().uuid().equals(reporter.data.uuid)
                    && targets.stream().noneMatch(value -> value.uuid().equals(left.target().uuid()))) {
                targets.add(left.target());
            }
        }
        targets.sort(Comparator.comparing(ReportTarget::online).reversed().thenComparing(ReportTarget::name));
        return List.copyOf(targets);
    }

    /**
     * Game thread only. Results complete there after publish confirmation or rollback.
     * If the main dispatcher is unavailable during shutdown, the stage completes exceptionally.
     */
    public CompletionStage<ReportResult> submit(Session reporter, ReportDraft draft) {
        GameThread.require("ReportService.submit");
        if (!enabled()) return completed(new ReportResult.Disabled());
        if (reporter == null || reporter.data == null || reporter.player == null || draft == null
                || draft.target() == null || draft.reason() == null
                || !Objects.equals(reporter.data.uuid, draft.reporterUuid())
                || !Async.isPlayerOnline(reporter.player)
                || sessions.getActive(reporter.player) != reporter
                || Objects.equals(reporter.data.uuid, draft.target().uuid())) {
            return completed(new ReportResult.InvalidTarget());
        }
        var online = sessions.getActive(draft.target().uuid());
        if (online != null && !Async.isPlayerOnline(online.player)) online = null;
        var left = online == null ? recent.find(draft.target().uuid()) : null;
        if ((online == null && left == null) || (online != null && isStaff(online)) || (left != null && left.staff())) {
            return completed(new ReportResult.InvalidTarget());
        }
        if (reporter.data.totalPlayTime < secrets.cases.reports.minPlayTimeMinutes) {
            return completed(new ReportResult.InsufficientPlaytime(
                    (long) secrets.cases.reports.minPlayTimeMinutes - reporter.data.totalPlayTime));
        }
        ReportTarget currentTarget = online != null ? target(online) : left.target();
        String targetAddress = online != null ? online.player.ip() : left.address();
        String reporterAddress = reporter.player.ip();
        var attempt = limiter.reserve(reporter.data.uuid, currentTarget.uuid(), reporterAddress);
        if (attempt.refusal() != null) return completed(attempt.refusal());
        var result = new CompletableFuture<ReportResult>();
        try {
            var settings = secrets.cases.reports;
            var captured = evidence.capture(currentTarget.uuid(), reporter.data.uuid, settings.chatLines,
                    Math.clamp(settings.chatWindowMinutes, 0, 15) * 60, settings.blockWindowSeconds);
            var currentDraft = new ReportDraft(reporter.data.uuid, currentTarget, draft.reason(), draft.comment());
            var message = CasesProtocolMapper.toReport(UUID.randomUUID().toString(), config.server.name,
                    target(reporter), currentDraft, reporter.locale().getLocale().toLanguageTag(),
                    reporterAddress != null && !reporterAddress.isBlank() && reporterAddress.equals(targetAddress), captured);
            var publish = async.supply(() -> {
                if (stopped) throw new IllegalStateException("Reports stopped");
                network.postAwait(message);
                return new ReportResult.Sent();
            });
            // Return failed writes even if the game dispatcher has already shut down.
            async.observe(publish.future(), (sent, error) -> {
                if (error != null) limiter.release(attempt.reservation());
            });
            publish.thenMain((sent, error) -> {
                if (error != null) {
                    limiter.release(attempt.reservation());
                    Log.warn("Report publish failed: @", error.toString());
                    result.complete(new ReportResult.Failed());
                } else {
                    result.complete(sent);
                }
            }).exceptionally(error -> {
                result.completeExceptionally(error);
                return null;
            });
        } catch (RuntimeException error) {
            limiter.release(attempt.reservation());
            Log.warn("Report capture failed: @", error.toString());
            result.complete(new ReportResult.Failed());
        }
        return result;
    }

    /** Holder check, independent of whether staff have authenticated on this connection yet. */
    public static boolean isStaff(Session session) {
        if (session == null || session.player == null) return false;
        if (session.permissions.rolesEnabled()) {
            return session.permissionSet.check(PermissionNodes.find(PermissionNodes.MODERATION_BAN).orElseThrow(),
                    true, Instant.now()).granted();
        }
        return session.player.admin;
    }

    private static ReportTarget target(Session session) {
        return new ReportTarget(session.data.uuid, PlayerPids.orNull(session.data.pid),
                CaseText.name(session.player.plainName()), true, 0);
    }

    private static CompletionStage<ReportResult> completed(ReportResult result) {
        return CompletableFuture.completedFuture(result);
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        stopped = true;
    }
}
