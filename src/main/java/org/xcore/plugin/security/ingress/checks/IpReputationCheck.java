package org.xcore.plugin.security.ingress.checks;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationService;

import static com.ospx.flubundle.Bundle.args;

/**
 * Checks whether the connecting IP address has a bad reputation
 * (proxy / VPN / TOR) using the configured IP reputation service.
 * <p>
 * Priority 10: Slow check that may consult cache or external provider,
 * runs in parallel with other slow checks.
 * <p>
 * Fail-open: any internal error during evaluation results in Allowed.
 */
@Singleton
public class IpReputationCheck implements IngressCheck {

    private final IpReputationService ipReputationService;
    private final Bundle bundle;
    private final GlobalConfig globalConfig;

    public IpReputationCheck(IpReputationService ipReputationService, Bundle bundle, GlobalConfig globalConfig) {
        this.ipReputationService = ipReputationService;
        this.bundle = bundle;
        this.globalConfig = globalConfig;
    }

    @Override
    public AccessResult check(NetConnection con, Packets.ConnectPacket packet) {
        String ip = con.address;
        if (ip == null || ip.isBlank()) {
            return AccessResult.Allowed.INSTANCE;
        }

        String normalized = ip.trim();

        try {
            if (ipReputationService.isBlocked(normalized)) {
                Localization local = new Localization(bundle, bundle.resolveLocale(packet.locale));
                String reason = local.format("ip-reputation-denied", args(
                        "discordUrl", globalConfig.discordUrl
                ));
                return new AccessResult.Denied(reason, false, 0);
            }
        } catch (Exception e) {
            PLog.errTag("IpReputationCheck", "Reputation service failed open", e);
            return AccessResult.Allowed.INSTANCE;
        }

        return AccessResult.Allowed.INSTANCE;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public String name() {
        return "IpReputationCheck";
    }
}
