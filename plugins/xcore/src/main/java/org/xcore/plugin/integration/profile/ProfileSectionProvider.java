package org.xcore.plugin.integration.profile;

import org.xcore.plugin.model.PlayerData;

import java.util.Optional;

/**
 * Service Provider Interface for blocks a mode adds to the player profile ({@code /stats}).
 */
public interface ProfileSectionProvider {

    /** Unique identifier of the section. Must be non-null and non-blank. */
    String id();

    /** Higher numbers are shown first. Ties are broken by registration order. */
    default int priority() {
        return 0;
    }

    /**
     * Reads what the section shows about {@code target}. May block on storage: the profile
     * calls it off the game thread.
     *
     * @return empty when there is nothing to show for this player
     */
    Optional<ProfileSectionView> load(PlayerData target);
}
