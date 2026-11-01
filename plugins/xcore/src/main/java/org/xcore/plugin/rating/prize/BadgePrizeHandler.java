package org.xcore.plugin.rating.prize;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.season.SeasonException;
import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerBadgeInventoryChangedCommandV1;

import java.util.HashSet;
import java.util.List;

/** Unlocks a badge for the player. The unlock is an atomic add, so repeating it is harmless. */
@Singleton
public class BadgePrizeHandler implements PrizeHandler {
    private final PlayerDataRepository players;
    private final NetworkService network;
    private final TomlXcoreConfig config;
    private final SessionService sessions;
    private final PlayerDisplayService display;
    private final Async async;

    @Inject
    public BadgePrizeHandler(PlayerDataRepository players,
                             NetworkService network,
                             TomlXcoreConfig config,
                             SessionService sessions,
                             PlayerDisplayService display,
                             Async async) {
        this.players = players;
        this.network = network;
        this.config = config;
        this.sessions = sessions;
        this.display = display;
        this.async = async;
    }

    @Override
    public PrizeKind kind() {
        return PrizeKind.BADGE;
    }

    @Override
    public void validate(SeasonPrize prize) {
        Badge badge = Badge.byId(prize.value());
        if (badge == null) {
            throw new SeasonException("Badge '" + prize.value() + "' was not found");
        }
        if (badge.system()) {
            throw new SeasonException("Badge '" + badge.id() + "' is a system badge and cannot be a prize");
        }
    }

    @Override
    public PrizeOutcome deliver(PrizeGrant grant) {
        Badge badge = Badge.byId(grant.value());
        if (badge == null || badge.system()) {
            return PrizeOutcome.failed("Badge '" + grant.value() + "' cannot be granted");
        }
        PlayerData player = players.findByUuid(grant.playerUuid());
        if (player == null) {
            return PrizeOutcome.failed("Player " + grant.playerUuid() + " no longer exists");
        }

        players.addUnlockedBadge(grant.playerUuid(), badge.id());
        refresh(grant.playerUuid(), badge);
        return PrizeOutcome.granted("Badge " + badge.id() + " unlocked");
    }

    /** Re-reads the inventory and tells every server, so an online winner sees the badge at once. */
    private void refresh(String uuid, Badge badge) {
        PlayerData fresh = players.findByUuid(uuid);
        if (fresh == null) return;
        HashSet<String> unlocked = fresh.unlockedBadges == null ? new HashSet<>() : new HashSet<>(fresh.unlockedBadges);
        unlocked.add(badge.id());
        String active = fresh.activeBadge == null ? "" : fresh.activeBadge;

        network.post(new PlayerBadgeInventoryChangedCommandV1(uuid, active, List.copyOf(unlocked), config.server.name));
        async.main(() -> {
            Session session = sessions.get(uuid);
            if (session == null || session.data == null) return;
            session.data.unlockedBadges = unlocked;
            display.refresh(session);
        });
    }
}
