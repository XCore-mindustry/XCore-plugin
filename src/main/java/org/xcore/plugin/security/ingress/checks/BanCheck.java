package org.xcore.plugin.security.ingress.checks;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.BanDataRepository;
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
 *
 * <p>This check runs on an ingress executor, never on the game thread, so the MongoDB
 * lookup is correctly off-thread. The expired-ban cleanup below is the exception: it
 * writes to the administration registry, which is game-thread state, so it is marshalled
 * rather than applied inline. The registry is a plain hash map, and unbanning a player
 * while the game thread reads the same map is a data race, not just a style violation.
 */
@Singleton
public class BanCheck implements IngressCheck {

    private final BanDataRepository banDataRepository;
    private final Bundle bundle;
    private final TomlSecretsConfig secretsConfig;
    private final Async async;

    public BanCheck(BanDataRepository banDataRepository, Bundle bundle, TomlSecretsConfig secretsConfig, Async async) {
        this.banDataRepository = banDataRepository;
        this.bundle = bundle;
        this.secretsConfig = secretsConfig;
        this.async = async;
    }

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        String uuid = packet.uuid;
        String ip = con.address;

        BanData ban = banDataRepository.find(uuid, ip);

        if (ban != null) {
            if (ban.expired()) {
                // Expired bans are lifted asynchronously: the connection is allowed either
                // way, so there is nothing to wait for and the registry write belongs on
                // the game thread. The row delete stays here so it happens even if the
                // player disconnects before the game thread next drains.
                async.main(() -> {
                    netServer.admins.unbanPlayerID(uuid);
                    netServer.admins.unbanPlayerIP(ip);
                });
                banDataRepository.delete(ban.uuid, ip);
                return AccessResult.Allowed.INSTANCE;
            }

            Duration duration = Duration.between(Instant.now(), ban.expireDate);

            // Runs off the game thread, so the resolver may read the player's selected language
            // from the database when it is not known yet.
            String reason = bundle.format(bundle.locale(packet), "tempban-content", args(
                    "nickname", stripColors(ban.name == null ? "" : ban.name),
                    "adminName", stripColors(ban.adminName == null ? "" : ban.adminName),
                    "reason", ban.reason == null ? "" : ban.reason,
                    "duration", Math.max(0, duration.toSeconds()),
                    "expireDate", ban.expireDate,
                    "discordUrl", secretsConfig.externalLinks.discordUrl
            ));

            return new AccessResult.Denied(reason, false, 0);
        }

        // The registry reads below are the one piece of game state left on this thread, and
        // they are deliberately not marshalled.
        //
        // check() is synchronous by contract: IngressService collects the checks as futures
        // against server.ingressHandshakeBudgetMillis, but a per-check game-thread round trip
        // would put a tick-bound wait in front of every connection, and on a headless server
        // the game loop is also the netty reader. A stall there would turn this fail-closed
        // check into a connection denial.
        //
        // The exposure is bounded and does not grow: Administration is rebuilt on load and its
        // only concurrent writer is the expired-ban unban marshalled above, so the worst case
        // is this check missing an unban that lands a frame later - the player is admitted
        // and unbanned instead of being denied, which is the non-fatal direction. Tightening
        // it properly means snapshotting the admin rules onto the game thread and reading
        // that copy here; that is a deliberate change to a fail-closed security path and
        // should be reviewed on its own rather than folded into a thread-safety sweep.
        if (netServer.admins.isIPBanned(ip) ||
                netServer.admins.isSubnetBanned(ip) ||
                netServer.admins.isIDBanned(uuid)) {

            String reason = bundle.format(bundle.locale(packet), "ban-content", args(
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
