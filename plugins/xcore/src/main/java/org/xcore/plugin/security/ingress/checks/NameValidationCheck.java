// src/main/java/org/xcore/plugin/security/ingress/checks/NameValidationCheck.java
package org.xcore.plugin.security.ingress.checks;

import arc.struct.Seq;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.FailureMode;
import org.xcore.plugin.security.ingress.IngressCheck;

import static arc.util.Strings.stripColors;
import static arc.util.Strings.stripGlyphs;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.common.TextUtils.stripFooCharacters;

/**
 * Validates player name for pirated clients and empty names.
 * Priority -90: Fast string check, synchronous.
 */
@Singleton
public class NameValidationCheck implements IngressCheck {

    private static final Seq<String> BANNED_NAMES = Seq.with(
            "valve", "tuttop", "codex", "igggames", "igg-games.com",
            "igruhaorg", "freetp.org", "goldberg", "rog"
    );

    private final Bundle bundle;

    public NameValidationCheck(Bundle bundle) {
        this.bundle = bundle;
    }

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        String name = packet.name;

        if (name != null && BANNED_NAMES.contains(name.toLowerCase())) {
            String reason = bundle.format(bundle.locale(packet), "kick-pirated-game");
            return new AccessResult.Denied(reason, false, 0);
        }

        if (name == null || name.trim().isEmpty()) {
            return new AccessResult.Denied(
                    mindustry.net.Packets.KickReason.nameEmpty.name(),
                    false, 0
            );
        }

        String fixedName = netServer.fixName(name);
        if (fixedName == null || fixedName.trim().isEmpty() || normalizedPlainName(fixedName).isEmpty()) {
            return new AccessResult.Denied(
                    mindustry.net.Packets.KickReason.nameEmpty.name(),
                    false, 0
            );
        }

        packet.name = fixedName;

        return AccessResult.Allowed.INSTANCE;
    }

    @Override
    public int priority() {
        return -90;
    }

    /** Local and pure: no I/O, no shared state, so a defect here is not a security event. */
    @Override
    public FailureMode failureMode() {
        return FailureMode.FAIL_OPEN;
    }

    @Override
    public String name() {
        return "NameValidationCheck";
    }

    private String normalizedPlainName(String name) {
        return stripFooCharacters(stripColors(stripGlyphs(name))).trim();
    }
}
