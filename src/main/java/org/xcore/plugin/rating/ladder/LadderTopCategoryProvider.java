package org.xcore.plugin.rating.ladder;

import org.jspecify.annotations.Nullable;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.RatingLeague;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/** The {@code /top} category of a ladder: the current season's standings, best rating first. */
public final class LadderTopCategoryProvider implements TopCategoryProvider {
    private final String id;
    private final int priority;
    private final Ladder ladder;
    @Nullable
    private final PlayerDataRepository players;

    public LadderTopCategoryProvider(String id, int priority, Ladder ladder, @Nullable PlayerDataRepository players) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        this.priority = priority;
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.players = players;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName(Localization local) {
        return local.t(ladder.definition().displayNameKey());
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public LeaderboardPage loadPage(LeaderboardPageRequest request) {
        int pageSize = Math.min(request.pageSize(), LadderStore.MAX_PAGE_SIZE);
        StandingPage page = ladder.top(pageSize, request.cursor());
        Map<String, PlayerData> profiles = profiles(page.standings());

        List<LeaderboardEntry> entries = new ArrayList<>(page.standings().size());
        int rank = (request.page() - 1) * pageSize + 1;
        for (LadderStanding standing : page.standings()) {
            PlayerData profile = profiles.get(standing.uuid());
            RatingLeague league = standing.league();
            Map<String, String> attributes = LeaderboardEntry.profileAttributes(profile);
            attributes.put("leagueIcon", league.icon());
            attributes.put("leagueName", league.name());
            entries.add(new LeaderboardEntry(
                    standing.uuid(),
                    rank++,
                    profile != null ? profile.nickname : null,
                    String.valueOf(standing.rating()),
                    attributes,
                    ""
            ));
        }

        Integer selfRank = null;
        String selfValue = null;
        String viewerUuid = request.viewerData() != null ? request.viewerData().uuid : null;
        OptionalLong viewerRank = ladder.rankOf(viewerUuid);
        if (viewerRank.isPresent()) {
            selfRank = Math.toIntExact(viewerRank.getAsLong());
            selfValue = String.valueOf(ladder.fetchStanding(viewerUuid).rating());
        }

        return new LeaderboardPage(request.page(), entries, page.hasNext(), page.nextCursor(),
                ladder.count(), selfRank, selfValue);
    }

    private Map<String, PlayerData> profiles(List<LadderStanding> standings) {
        Map<String, PlayerData> profiles = new HashMap<>();
        if (players == null || standings.isEmpty()) {
            return profiles;
        }
        for (PlayerData profile : players.findByUuids(standings.stream().map(LadderStanding::uuid).toList())) {
            profiles.put(profile.uuid, profile);
        }
        return profiles;
    }
}
