package org.xcore.plugin.rating.prize;

/**
 * Where a prize grant stands.
 *
 * <ul>
 *   <li>{@link #PENDING} - created, waiting to be given (an automatic prize awaiting its handler,
 *       or a custom one awaiting a person);</li>
 *   <li>{@link #GRANTED} - given by the plugin itself;</li>
 *   <li>{@link #DELIVERED} - a person confirmed that they handed it over;</li>
 *   <li>{@link #FAILED} - the handler could not give it; it is kept for a person to resolve.</li>
 * </ul>
 */
public enum PrizeStatus {
    PENDING,
    GRANTED,
    DELIVERED,
    FAILED;

    /** Nothing more is expected to happen to the grant. */
    public boolean settled() {
        return this == GRANTED || this == DELIVERED;
    }
}
