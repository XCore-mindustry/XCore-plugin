package org.xcore.plugin.rating.season;

/**
 * Hears about season changes as this server notices them. Every server on the network
 * observes each change once, on the Mindustry main thread.
 */
@FunctionalInterface
public interface SeasonObserver {
    void seasonChanged(SeasonChange change);
}
