package org.xcore.cloud.mindustry.selector.exception;

public class TooManyTargetsException extends RuntimeException {
    private final String selectorInput;

    public TooManyTargetsException(String selectorInput, String message) {
        super(message);
        this.selectorInput = selectorInput;
    }

    public String selectorInput() {
        return selectorInput;
    }
}
