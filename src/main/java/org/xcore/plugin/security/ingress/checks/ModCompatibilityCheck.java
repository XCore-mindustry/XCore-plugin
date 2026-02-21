package org.xcore.plugin.security.ingress.checks;

import arc.struct.Seq;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;

import static mindustry.Vars.mods;

/**
 * Checks for mod compatibility between client and server.
 * Priority -30: Fast mod list comparison, synchronous.
 */
@Singleton
public class ModCompatibilityCheck implements IngressCheck {

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        Seq<String> extraMods = packet.mods.copy();
        Seq<String> missingMods = mods.getIncompatibility(extraMods);

        if (extraMods.isEmpty() && missingMods.isEmpty()) {
            return AccessResult.Allowed.INSTANCE;
        }

        StringBuilder result = new StringBuilder("[accent]Incompatible mods![]\n\n");

        if (!missingMods.isEmpty()) {
            result.append("Missing:[lightgray]\n> ")
                    .append(missingMods.toString("\n> "))
                    .append("[]\n");
        }

        if (!extraMods.isEmpty()) {
            result.append("Unnecessary mods:[lightgray]\n> ")
                    .append(extraMods.toString("\n> "));
        }

        return new AccessResult.Denied(result.toString(), false, 0);
    }

    @Override
    public int priority() {
        return -30;
    }

    @Override
    public String name() {
        return "ModCompatibilityCheck";
    }
}
