package org.xcore.plugin.rating.season;

public enum SeasonStatus {
    /** Matches count towards this season. */
    ACTIVE,
    /** The season is over; matches that ended before the deadline may still be settling. */
    CLOSING,
    /** Final: ranks are frozen and the podium is recorded. */
    ARCHIVED
}
