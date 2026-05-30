package org.xcore.plugin.security.ingress.checks;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.xcore.plugin.config.TomlSecretsConfig;
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
    private final TomlSecretsConfig secretsConfig;

    /**
     * Constructs an IpReputationCheck that evaluates connection IPs against an IP reputation service.
     *
     * @param ipReputationService service used to determine whether an IP is blocked (e.g., proxy/VPN/TOR)
     * @param bundle               localization bundle used to format denial messages
     * @param secretsConfig        configuration providing values included in denial messages (notably `discordUrl`)
     */
    public IpReputationCheck(IpReputationService ipReputationService, Bundle bundle, TomlSecretsConfig secretsConfig) {
        this.ipReputationService = ipReputationService;
        this.bundle = bundle;
        this.secretsConfig = secretsConfig;
    }

    /**
     * Checks the connection's IP against the reputation service and denies access when the IP is blocked.
     *
     * If the connection address is null or blank the check allows. When the IP is blocked this returns
     * an AccessResult.Denied containing a localized denial reason (locale resolved from the connect
     * packet). If an error occurs while querying the reputation service the method fails open and
     * returns an allow result.
     *
     * @param con    the network connection whose IP address will be evaluated
     * @param packet the connect packet whose locale is used to localize the denial reason
     * @return       an AccessResult.Denied with a localized reason when the IP is blocked, `AccessResult.Allowed.INSTANCE` otherwise
     */
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
                        "discordUrl", secretsConfig.externalLinks.discordUrl
                ));
                return new AccessResult.Denied(reason, false, 0);
            }
        } catch (Exception e) {
            PLog.errTag("IpReputationCheck", "Reputation service failed open", e);
            return AccessResult.Allowed.INSTANCE;
        }

        return AccessResult.Allowed.INSTANCE;
    }

    /**
     * Execution priority of this ingress check.
     *
     * @return `10` indicating the check's execution order relative to others; lower values run earlier.
     */
    @Override
    public int priority() {
        return 10;
    }

    /**
     * Identifies this ingress check implementation.
     *
     * @return the name of the check, "IpReputationCheck"
     */
    @Override
    public String name() {
        return "IpReputationCheck";
    }
}
