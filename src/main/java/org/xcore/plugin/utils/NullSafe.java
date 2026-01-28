package org.xcore.plugin.utils;

import java.util.Objects;

public final class NullSafe {
    private NullSafe() {}

    /**
     * Returns value if non-null, otherwise returns defaultValue (which can be null).
     * Unlike {@link Objects#requireNonNullElse}, this allows null as default.
     */
    public static <T> T orElse(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }
}
