package org.xcore.plugin.rating.prize;

import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.season.SeasonException;
import org.xcore.plugin.rating.season.SeasonPrize;

/** Gives one kind of prize. Add a kind by adding a handler. */
public interface PrizeHandler {

    PrizeKind kind();

    /** @throws SeasonException when this prize could never be given, so it is refused at setup */
    default void validate(SeasonPrize prize) {
    }

    /**
     * Blocking. Gives the prize. A failure that retrying cannot fix is returned as
     * {@link PrizeOutcome#failed}; a transient one is thrown and the grant is tried again.
     */
    PrizeOutcome deliver(PrizeGrant grant);
}
