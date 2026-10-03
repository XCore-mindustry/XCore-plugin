package org.xcore.plugin.rating.ladder;

import arc.util.Log;
import mindustry.gen.Player;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.integration.PlayerDisplayProvider;
import org.xcore.plugin.integration.PlayerDisplayRefreshService;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.RatingLeague;

import java.util.Objects;

/**
 * Shows the league a player holds on one ladder as an icon next to their name.
 *
 * <p>Names are resolved on the game thread, so the icon comes from the ladder's cache:
 * the mode calls {@link #preload(Player)} when a player joins, and until that finishes
 * the player is shown in the starting league.</p>
 */
public final class LadderLeagueDisplay implements PlayerDisplayProvider {
    private final String id;
    private final int priority;
    private final Ladder ladder;
    private final PlayerDisplayRefreshService refreshService;
    private final Async async;

    public LadderLeagueDisplay(String id, int priority, Ladder ladder,
                               PlayerDisplayRefreshService refreshService, Async async) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        this.priority = priority;
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.refreshService = Objects.requireNonNull(refreshService, "refreshService");
        this.async = Objects.requireNonNull(async, "async");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public String resolve(PlayerData data, Player player) {
        String uuid = player != null && player.uuid() != null && !player.uuid().isBlank() ? player.uuid()
                : (data != null ? data.uuid : null);
        if (uuid == null || uuid.isBlank()) return "";
        return RatingLeague.fromRating(ladder.cachedRating(uuid)).icon();
    }

    /** Loads a joining player's standing off the game thread, then redraws their name. */
    public void preload(Player player) {
        if (player == null || player.uuid() == null || player.uuid().isBlank()) return;
        String uuid = player.uuid();
        if (ladder.cachedStanding(uuid).isPresent()) return;

        async.supply(() -> ladder.standing(uuid)).thenMain((standing, error) -> {
            if (error != null) {
                Log.warn("Failed to load @ standing for @: @", ladder.id(), uuid, error.getMessage());
                return;
            }
            refreshService.refresh(uuid);
        });
    }
}
