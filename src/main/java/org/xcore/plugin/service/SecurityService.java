package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.database.repository.MuteDataRepository;
import org.xcore.plugin.model.MuteData;

import java.time.Duration;
import java.time.Instant;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class SecurityService {

    private final MuteDataRepository muteDataRepository;
    private final BundleService bundle;

    @Inject
    public SecurityService(MuteDataRepository muteDataRepository, BundleService bundle) {
        this.muteDataRepository = muteDataRepository;
        this.bundle = bundle;
    }

    public boolean isMuted(Player player) {
        MuteData mute = muteDataRepository.findByUuid(player.uuid());

        if (mute == null) return false;

        if (!mute.expired()) {
            Duration remain = Duration.between(Instant.now(), mute.expireDate);

            bundle.send(player, "you-are-muted",
                    args("adminName", mute.adminName,
                            "reason", mute.reason,
                            "remainMinutes", remain.toMinutes(),
                            "remainSeconds", remain.toSecondsPart()
                    )
            );
            return true;
        }

        muteDataRepository.delete(player.uuid());
        return false;
    }
}