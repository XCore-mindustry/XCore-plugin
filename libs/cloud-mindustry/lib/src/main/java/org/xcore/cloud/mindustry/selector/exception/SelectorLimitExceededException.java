package org.xcore.cloud.mindustry.selector.exception;

public class SelectorLimitExceededException extends RuntimeException {
    private final int count;
    private final int limit;

    public SelectorLimitExceededException(int count, int limit) {
        super("Selector matched " + count + " entities, exceeding maximum limit of " + limit);
        this.count = count;
        this.limit = limit;
    }

    public int count() {
        return count;
    }

    public int limit() {
        return limit;
    }
}
