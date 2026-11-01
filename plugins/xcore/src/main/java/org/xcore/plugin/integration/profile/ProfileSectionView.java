package org.xcore.plugin.integration.profile;

import org.xcore.plugin.localization.Localization;

/**
 * What a {@link ProfileSectionProvider} loaded about one player. Holds data only, so the
 * same view can be rendered for any viewer, on the game thread, without touching storage.
 */
@FunctionalInterface
public interface ProfileSectionView {
    ProfileSection render(Localization local);
}
