package org.xcore.plugin.rating.season;

/**
 * What the season lifecycle needs from whoever hands out prizes. Declared here so that the
 * lifecycle does not depend on the prize package, which depends on seasons.
 */
public interface SeasonPrizes {

    /** Prizes are not handed out at all. */
    SeasonPrizes NONE = new SeasonPrizes() {
        @Override
        public void validate(SeasonPrize prize) {
        }

        @Override
        public void award(Season archived) {
        }
    };

    /** @throws SeasonException when the prize cannot be delivered, so it is refused before it is stored */
    void validate(SeasonPrize prize);

    /**
     * Blocking. Creates the grants of an archived season's podium and delivers the automatic ones.
     * Safe to repeat: a grant that exists is never created twice or delivered twice.
     */
    void award(Season archived);
}
