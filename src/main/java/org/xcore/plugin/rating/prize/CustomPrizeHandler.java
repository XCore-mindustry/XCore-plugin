package org.xcore.plugin.rating.prize;

import jakarta.inject.Singleton;
import org.xcore.plugin.rating.season.PrizeKind;

/** A prize only a person can give, such as a gift code. It waits for {@code season prize delivered}. */
@Singleton
public class CustomPrizeHandler implements PrizeHandler {

    @Override
    public PrizeKind kind() {
        return PrizeKind.CUSTOM;
    }

    @Override
    public PrizeOutcome deliver(PrizeGrant grant) {
        return PrizeOutcome.pending();
    }
}
