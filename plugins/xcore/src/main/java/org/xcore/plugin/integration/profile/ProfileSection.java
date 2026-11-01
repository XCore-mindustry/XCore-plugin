package org.xcore.plugin.integration.profile;

import java.util.List;
import java.util.Objects;

/**
 * One block of a player's profile, already written in the viewer's language.
 *
 * @param headline what the block is about and its main figure
 * @param details  short facts that follow the headline on a wide screen and wrap under it
 *                 on a narrow one
 * @param lines    further rows shown only in the full profile card
 */
public record ProfileSection(String headline, List<String> details, List<String> lines) {
    public ProfileSection {
        Objects.requireNonNull(headline, "headline");
        details = details == null ? List.of() : List.copyOf(details);
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
