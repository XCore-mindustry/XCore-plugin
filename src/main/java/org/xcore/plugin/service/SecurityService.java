package org.xcore.plugin.service;

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

    public boolean isMuted(Player player) {
        MuteData mute = muteDataRepository.findByUuid(player.uuid());

        if (mute == null) return false;

        if (!mute.expired()) {
            Session session = sessionService.get().get(player);
            if (session != null && session.data != null) {
                Duration remain = Duration.between(Instant.now(), mute.expireDate);
                session.locale().send("you-are-muted", muteMessageArgs(mute.adminName, mute.reason, remain));
            }
            return true;
        }

        muteDataRepository.delete(player.uuid());
        return false;
    }

    public static Map<String, Object> durationParts(Duration duration) {
        Duration safeDuration = duration.isNegative() ? Duration.ZERO : duration;

        return Map.of(
                "days", safeDuration.toDays(),
                "hours", safeDuration.toHoursPart(),
                "minutes", safeDuration.toMinutesPart(),
                "seconds", safeDuration.toSecondsPart()
        );
    }

    public static Map<String, Object> muteMessageArgs(String adminName, String reason, Duration duration) {
        var args = new LinkedHashMap<String, Object>();
        args.put("adminName", adminName);
        args.put("reason", reason);
        args.putAll(durationParts(duration));
        return args;
    }
}
