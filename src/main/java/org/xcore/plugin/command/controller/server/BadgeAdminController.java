package org.xcore.plugin.command.controller.server;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Singleton
public class BadgeAdminController implements CloudServerController {

    private static final Pattern PID_PATTERN = Pattern.compile("#\\d+");

    private final SessionService sessionService;
    private final NetworkService network;
    private final PlayerDisplayService playerDisplayService;
    private final PlayerDataRepository playerDataRepository;

    @Inject
    public BadgeAdminController(SessionService sessionService,
                                NetworkService network,
                                PlayerDisplayService playerDisplayService,
                                PlayerDataRepository playerDataRepository) {
        this.sessionService = sessionService;
        this.network = network;
        this.playerDisplayService = playerDisplayService;
        this.playerDataRepository = playerDataRepository;
    }

    @Command("badge grant <player> <id>")
    public void grant(XCoreSender sender,
                      @Argument("player") String player,
                      @Argument("id") String id) {
        changeBadge(sender, player, id, true);
    }

    @Command("badge revoke <player> <id>")
    public void revoke(XCoreSender sender,
                       @Argument("player") String player,
                       @Argument("id") String id) {
        changeBadge(sender, player, id, false);
    }

    @Command("badge list")
    public void list(XCoreSender sender) {
        String badges = Badge.selectableManualBadges().stream()
                .map(badge -> badge.id() + " - " + badge.id())
                .collect(Collectors.joining(", "));

        Log.info("Grantable badges: @", badges);
    }

    private void changeBadge(XCoreSender sender, String playerRef, String badgeId, boolean grant) {
        PlayerData target = resolveTarget(playerRef);
        if (target == null) {
            Log.err("Player not found.");
            return;
        }

        Badge badge = Badge.byId(badgeId);
        if (badge == null) {
            Log.err("Badge '@' was not found.", badgeId);
            return;
        }

        if (badge.system()) {
            Log.err("Badge '@' cannot be granted or selected manually.", badge.id());
            return;
        }

        if (target.unlockedBadges == null) {
            target.unlockedBadges = new HashSet<>();
        }

        Set<String> updatedBadges = new HashSet<>(target.unlockedBadges);
        boolean changed = grant ? updatedBadges.add(badge.id()) : updatedBadges.remove(badge.id());

        String updatedActiveBadge = target.activeBadge;
        if (!grant && badge.id().equals(updatedActiveBadge)) {
            updatedActiveBadge = "";
            changed = true;
        }

        if (!changed) {
            if (grant) {
                Log.info("Player @ already has badge '@'.", target.nickname, badge.id());
            } else {
                Log.info("Player @ does not have badge '@'.", target.nickname, badge.id());
            }
            return;
        }

        Session session = sessionService.get(target.uuid);
        if (session != null) {
            applyBadgeState(session.data, updatedBadges, updatedActiveBadge);
            persistBadgeState(session.data);
            playerDisplayService.refresh(session);
        } else {
            applyBadgeState(target, updatedBadges, updatedActiveBadge);
            persistBadgeState(target);
        }

        network.post(new TransportEvents.PlayerBadgeInventoryChanged(
                target.uuid,
                updatedActiveBadge,
                copyBadges(updatedBadges)
        ));
        if (grant) {
            Log.info("Granted badge '@' to @ (#@).", badge.id(), target.nickname, target.pid);
        } else {
            Log.info("Revoked badge '@' from @ (#@).", badge.id(), target.nickname, target.pid);
        }
    }

    private void applyBadgeState(PlayerData target, Set<String> unlockedBadges, String activeBadge) {
        target.unlockedBadges = unlockedBadges;
        target.activeBadge = activeBadge;
    }

    private void persistBadgeState(PlayerData target) {
        playerDataRepository.replaceUnlockedBadges(target.uuid, copyBadges(target.unlockedBadges));
        playerDataRepository.setActiveBadge(target.uuid, target.activeBadge == null ? "" : target.activeBadge);
    }

    private Set<String> copyBadges(Set<String> badges) {
        return badges == null ? Set.of() : Set.copyOf(badges);
    }

    private PlayerData resolveTarget(String playerRef) {
        if (playerRef == null || playerRef.isBlank()) return null;

        if (PID_PATTERN.matcher(playerRef).matches()) {
            return sessionService.getOrLoadFromDb(Integer.parseInt(playerRef.substring(1)));
        }

        for (Session session : sessionService.getAllCachedSnapshot()) {
            if (session == null || session.data == null) continue;
            if (playerRef.equalsIgnoreCase(session.data.uuid)
                    || playerRef.equalsIgnoreCase(session.data.nickname)
                    || playerRef.equalsIgnoreCase(session.data.customNickname)) {
                return session.data;
            }
        }

        return sessionService.getOrLoadFromDb(playerRef);
    }
}
