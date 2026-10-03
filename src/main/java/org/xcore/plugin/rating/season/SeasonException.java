package org.xcore.plugin.rating.season;

/** A season operation was refused; the message is meant for the operator who asked for it. */
public class SeasonException extends RuntimeException {
    public SeasonException(String message) {
        super(message);
    }
}
