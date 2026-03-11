package org.xcore.plugin.security.ingress.checks;

import arc.util.Time;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.localization.LocalizationFactory;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;
import org.xcore.plugin.localization.BundleService;

import java.time.Duration;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

/**
 * Checks if player was recently kicked and still in timeout.
 * Priority -60: Fast admin lookup, synchronous.
 */
@Singleton
public class KickTimeoutCheck implements IngressCheck {

    private final BundleService bundle;
    private final LocalizationFactory localizationFactory;

    public KickTimeoutCheck(BundleService bundle, LocalizationFactory localizationFactory) {
        this.bundle = bundle;
        this.localizationFactory = localizationFactory;
    }

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        long kickTime = netServer.admins.getKickTime(packet.uuid, con.address);

        if (Time.millis() < kickTime) {
            Duration remain = Duration.ofMillis(kickTime - Time.millis());

            Localization local = localizationFactory.forLocale(bundle.locale(packet.locale));

            String reason = local.format(
                    "kick-recently-kicked", args(
                            "remainMinutes", remain.toMinutes(),
                            "remainSeconds", remain.toSecondsPart()
                    ));

            return new AccessResult.Denied(reason, false, 0);
        }

        return AccessResult.Allowed.INSTANCE;
    }

    @Override
    public int priority() {
        return -60;
    }

    @Override
    public String name() {
        return "KickTimeoutCheck";
    }
}
