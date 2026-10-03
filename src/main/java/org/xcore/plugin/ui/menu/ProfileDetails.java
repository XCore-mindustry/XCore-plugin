package org.xcore.plugin.ui.menu;

import org.xcore.plugin.integration.profile.ProfileSectionView;
import org.xcore.plugin.model.PlayerStatsOverview;

import java.util.List;

/**
 * The part of a player profile that is read from storage after the dialog has opened.
 *
 * @param stats        match telemetry, {@code null} when none could be read
 * @param hexedTopRank position in the legacy Hexed leaderboard, {@code null} when unranked
 * @param sections     blocks contributed by this server's modes, in display order
 */
public record ProfileDetails(PlayerStatsOverview stats, Integer hexedTopRank, List<ProfileSectionView> sections) {
    public ProfileDetails {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
