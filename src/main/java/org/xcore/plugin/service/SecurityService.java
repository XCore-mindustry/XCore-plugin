package org.xcore.plugin.service;

import arc.util.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.database.repository.MuteDataRepository;
import org.xcore.plugin.model.MuteData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class SecurityService {

    static final long UNMUTED_CACHE_TTL_MS = 30_000L;

    private final MuteDataRepository muteDataRepository;
    private final Provider<SessionService> sessionService;
    private final Map<String, CachedMute> muteCache = new ConcurrentHashMap<>();

    @Inject
    public SecurityService(MuteDataRepository muteDataRepository, Provider<SessionService> sessionService) {
        this.muteDataRepository = muteDataRepository;
        this.sessionService = sessionService;
    }

    public record MuteCheckResult(boolean muted, @Nullable MuteData muteData, Duration remaining) {}

    public MuteCheckResult checkMute(Player player) {
        if (player == null || player.uuid() == null || player.uuid().isBlank()) {
            return new MuteCheckResult(false, null, Duration.ZERO);
        }

        String uuid = player.uuid();
        CachedMute cached = muteCache.get(uuid);
        if (cached != null) {
            if (cached.data == null) {
                if (System.currentTimeMillis() - cached.cachedAt < UNMUTED_CACHE_TTL_MS) {
                    return new MuteCheckResult(false, null, Duration.ZERO);
                }
            } else {
                if (!cached.data.expired()) {
                    Duration remaining = Duration.between(Instant.now(), cached.data.expireDate);
                    return new MuteCheckResult(true, cached.data, remaining);
                } else {
                    muteCache.put(uuid, new CachedMute(null, System.currentTimeMillis()));
                    muteDataRepository.delete(uuid);
                    return new MuteCheckResult(false, null, Duration.ZERO);
                }
            }
        }

        MuteData mute = muteDataRepository.findByUuid(uuid);
        if (mute == null) {
            muteCache.put(uuid, new CachedMute(null, System.currentTimeMillis()));
            return new MuteCheckResult(false, null, Duration.ZERO);
        }
        if (mute.expired()) {
            muteCache.put(uuid, new CachedMute(null, System.currentTimeMillis()));
            muteDataRepository.delete(uuid);
            return new MuteCheckResult(false, null, Duration.ZERO);
        }

        muteCache.put(uuid, new CachedMute(mute, System.currentTimeMillis()));
        Duration remaining = Duration.between(Instant.now(), mute.expireDate);
        return new MuteCheckResult(true, mute, remaining);
    }

    public CompletionStage<MuteCheckResult> checkMuteAsync(Player player) {
        if (player == null || player.uuid() == null || player.uuid().isBlank()) {
            return CompletableFuture.completedFuture(new MuteCheckResult(false, null, Duration.ZERO));
        }

        String uuid = player.uuid();
        CachedMute cached = muteCache.get(uuid);
        if (cached != null) {
            if (cached.data == null) {
                if (System.currentTimeMillis() - cached.cachedAt < UNMUTED_CACHE_TTL_MS) {
                    return CompletableFuture.completedFuture(new MuteCheckResult(false, null, Duration.ZERO));
                }
            } else {
                if (!cached.data.expired()) {
                    Duration remaining = Duration.between(Instant.now(), cached.data.expireDate);
                    return CompletableFuture.completedFuture(new MuteCheckResult(true, cached.data, remaining));
                } else {
                    muteCache.put(uuid, new CachedMute(null, System.currentTimeMillis()));
                    muteDataRepository.deleteAsync(uuid);
                    return CompletableFuture.completedFuture(new MuteCheckResult(false, null, Duration.ZERO));
                }
            }
        }

        return muteDataRepository.findByUuidAsync(uuid).thenApply(mute -> {
            if (mute == null) {
                muteCache.put(uuid, new CachedMute(null, System.currentTimeMillis()));
                return new MuteCheckResult(false, null, Duration.ZERO);
            }
            if (mute.expired()) {
                muteCache.put(uuid, new CachedMute(null, System.currentTimeMillis()));
                muteDataRepository.deleteAsync(uuid);
                return new MuteCheckResult(false, null, Duration.ZERO);
            }

            muteCache.put(uuid, new CachedMute(mute, System.currentTimeMillis()));
            Duration remaining = Duration.between(Instant.now(), mute.expireDate);
            return new MuteCheckResult(true, mute, remaining);
        });
    }

    public void setMuted(String uuid, MuteData muteData) {
        if (uuid != null && !uuid.isBlank()) {
            muteCache.put(uuid, new CachedMute(muteData, System.currentTimeMillis()));
        }
    }

    public void clearMute(String uuid) {
        if (uuid != null && !uuid.isBlank()) {
            muteCache.put(uuid, new CachedMute(null, System.currentTimeMillis()));
        }
    }

    public void invalidateMute(String uuid) {
        if (uuid != null && !uuid.isBlank()) {
            muteCache.remove(uuid);
        }
    }

    public boolean isMuted(Player player) {
        return checkMute(player).muted();
    }

    public boolean checkAndNotifyMuted(Player player) {
        var result = checkMute(player);
        if (!result.muted()) return false;
        Session session = sessionService.get().get(player);
        if (session != null && session.data != null) {
            session.locale().send("you-are-muted", muteMessageArgs(result.muteData().adminName, result.muteData().reason, result.remaining()));
        }
        return true;
    }

    public static Map<String, Object> muteMessageArgs(String adminName, String reason, Duration duration) {
        return Map.of(
                "adminName", adminName,
                "reason", reason,
                "duration", Math.max(0, duration.toSeconds())
        );
    }

    record CachedMute(@Nullable MuteData data, long cachedAt) {}
}
