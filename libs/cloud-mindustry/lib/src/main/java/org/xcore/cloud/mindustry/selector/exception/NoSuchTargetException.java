package org.xcore.cloud.mindustry.selector.exception;

public class NoSuchTargetException extends RuntimeException {
    private final String selectorInput;

    public NoSuchTargetException(String selectorInput) {
        super("No targets found for selector: " + selectorInput);
        this.selectorInput = selectorInput;
    }

    public String selectorInput() {
        return selectorInput;
    }
}
