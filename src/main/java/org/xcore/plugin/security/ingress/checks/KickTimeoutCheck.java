package org.xcore.plugin.security.ingress.checks;

import arc.util.Time;
import com.ospx.flubundle.Args;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.FailureMode;
import org.xcore.plugin.security.ingress.IngressCheck;

import java.time.Duration;

import static mindustry.Vars.netServer;

/**
 * Checks if player was recently kicked and still in timeout.
 * Priority -60: Fast admin lookup, synchronous.
 */
@Singleton
public class KickTimeoutCheck implements IngressCheck {

    private final Bundle bundle;

    public KickTimeoutCheck(Bundle bundle) {
        this.bundle = bundle;
    }

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        long kickTime = netServer.admins.getKickTime(packet.uuid, con.address);

        if (Time.millis() < kickTime) {
            Duration remain = Duration.ofMillis(kickTime - Time.millis());

            // The player's selected language when it is already known; this check runs on the
            // game thread, so the resolver does not go to the database for it.
            String reason = bundle.format(bundle.locale(packet), "kick-recently-kicked",
                    Args.of("remaining", Math.max(0, remain.toSeconds())));

            return new AccessResult.Denied(reason, false, 0);
        }

        return AccessResult.Allowed.INSTANCE;
    }

    @Override
    public int priority() {
        return -60;
    }

    /** Local and pure: no I/O, no shared state, so a defect here is not a security event. */
    @Override
    public FailureMode failureMode() {
        return FailureMode.FAIL_OPEN;
    }

    @Override
    public String name() {
        return "KickTimeoutCheck";
    }
}
