package org.xcore.plugin.security.ingress.checks;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;

import java.time.Duration;
import java.time.Instant;

import static arc.util.Strings.stripColors;
import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

/**
 * Checks if player is banned (temporary or permanent).
 * Priority 0: Database lookup, runs in parallel.
 */
@Singleton
public class BanCheck implements IngressCheck {

    private final BanDataRepository banDataRepository;
    private final Bundle bundle;
    private final TomlSecretsConfig secretsConfig;

    public BanCheck(BanDataRepository banDataRepository, Bundle bundle, TomlSecretsConfig secretsConfig) {
        this.banDataRepository = banDataRepository;
        this.bundle = bundle;
        this.secretsConfig = secretsConfig;
    }

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        String uuid = packet.uuid;
        String ip = con.address;

        Localization local = new Localization(bundle, bundle.resolveLocale(packet.locale));

        BanData ban = banDataRepository.find(uuid, ip);

        if (ban != null) {
            if (ban.expired()) {
                netServer.admins.unbanPlayerID(uuid);
                netServer.admins.unbanPlayerIP(ip);
                banDataRepository.delete(ban.uuid, ip);
                return AccessResult.Allowed.INSTANCE;
            }

            Duration duration = Duration.between(Instant.now(), ban.expireDate);

            String reason = local.format("tempban-content", args(
                    "nickname", stripColors(ban.name == null ? "" : ban.name),
                    "adminName", stripColors(ban.adminName == null ? "" : ban.adminName),
                    "reason", ban.reason == null ? "" : ban.reason,
                    "days", duration.toDays(),
                    "hours", duration.toHoursPart(),
                    "minutes", duration.toMinutesPart(),
                    "discordUrl", secretsConfig.externalLinks.discordUrl
            ));

            return new AccessResult.Denied(reason, false, 0);
        }

        if (netServer.admins.isIPBanned(ip) ||
                netServer.admins.isSubnetBanned(ip) ||
                netServer.admins.isIDBanned(uuid)) {

            String reason = local.format("ban-content", args(
                    "nickname", stripColors(packet.name),
                    "discordUrl", secretsConfig.externalLinks.discordUrl
            ));

            return new AccessResult.Denied(reason, false, 0);
        }

        return AccessResult.Allowed.INSTANCE;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public String name() {
        return "BanCheck";
    }
}
