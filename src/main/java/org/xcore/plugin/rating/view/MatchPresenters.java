package org.xcore.plugin.rating.view;

import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** The {@link MatchPresenter} of each ladder; modes register theirs next to the ladder. */
@Singleton
public class MatchPresenters {
    private final Map<String, MatchPresenter> presenters = new ConcurrentHashMap<>();
    private final MatchPresenter fallback = new StandardMatchPresenter();

    public void register(String ladderId, MatchPresenter presenter) {
        presenters.put(Objects.requireNonNull(ladderId, "ladderId"), Objects.requireNonNull(presenter, "presenter"));
    }

    /** The ladder's presenter, or the standard one for a ladder that registered none. */
    public MatchPresenter of(@Nullable String ladderId) {
        MatchPresenter presenter = ladderId == null ? null : presenters.get(ladderId);
        return presenter != null ? presenter : fallback;
    }
}
