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

import static com.ospx.flubundle.Bundle.args;

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
                session.locale().send("you-are-muted",
                        args("adminName", mute.adminName,
                                "reason", mute.reason,
                                "remainMinutes", remain.toMinutes(),
                                "remainSeconds", remain.toSecondsPart()
                        )
                );
            }
            return true;
        }

        muteDataRepository.delete(player.uuid());
        return false;
    }
}