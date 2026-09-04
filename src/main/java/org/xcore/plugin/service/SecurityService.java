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
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public class SecurityService {

    private final MuteDataRepository muteDataRepository;
    private final Provider<SessionService> sessionService;

    @Inject
    public SecurityService(MuteDataRepository muteDataRepository, Provider<SessionService> sessionService) {
        this.muteDataRepository = muteDataRepository;
        this.sessionService = sessionService;
    }

    public record MuteCheckResult(boolean muted, @Nullable MuteData muteData, Duration remaining) {}

    public MuteCheckResult checkMute(Player player) {
        MuteData mute = muteDataRepository.findByUuid(player.uuid());
        if (mute == null) {
            return new MuteCheckResult(false, null, Duration.ZERO);
        }
        if (mute.expired()) {
            muteDataRepository.delete(player.uuid());
            return new MuteCheckResult(false, null, Duration.ZERO);
        }
        Duration remaining = Duration.between(Instant.now(), mute.expireDate);
        return new MuteCheckResult(true, mute, remaining);
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
}
