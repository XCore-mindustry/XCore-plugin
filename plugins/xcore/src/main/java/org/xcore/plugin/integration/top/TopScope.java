package org.xcore.plugin.integration.top;

import java.util.Map;
import java.util.Objects;

/**
 * One of several leaderboards inside a category — a rating season, for instance. The
 * {@code /top} menu lets the viewer step through a category's scopes.
 *
 * @param id         identifier passed back in {@link LeaderboardPageRequest#scopeId()}
 * @param current    whether this is the scope shown when none is asked for
 * @param attributes provider-specific data for {@link TopCategoryProvider#formatScope}
 */
public record TopScope(String id, boolean current, Map<String, String> attributes) {
    public TopScope {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
