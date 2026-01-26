package org.xcore.plugin.utils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.modules.bundles.BundleService;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.utils.models.MuteData;

import java.time.Duration;
import java.time.Instant;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class SecurityService {

    private final DatabaseService database;
    private final BundleService bundle;

    @Inject
    public SecurityService(DatabaseService database, BundleService bundle) {
        this.database = database;
        this.bundle = bundle;
    }

    public boolean isMuted(Player player) {
        MuteData mute = database.getMuteDataRepository().findByUuid(player.uuid());

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

        database.getMuteDataRepository().delete(player.uuid());
        return false;
    }
}