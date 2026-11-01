package org.xcore.plugin.rating.prize;

/** What a {@link PrizeHandler} did with a grant. */
public record PrizeOutcome(PrizeStatus status, String note) {

    public static PrizeOutcome granted(String note) {
        return new PrizeOutcome(PrizeStatus.GRANTED, note);
    }

    /** The prize cannot be given and retrying will not help; a person has to look at it. */
    public static PrizeOutcome failed(String note) {
        return new PrizeOutcome(PrizeStatus.FAILED, note);
    }

    /** Nothing was done; a person gives the prize and confirms it. */
    public static PrizeOutcome pending() {
        return new PrizeOutcome(PrizeStatus.PENDING, "");
    }
}
