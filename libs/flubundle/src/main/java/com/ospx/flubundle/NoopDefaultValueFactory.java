package com.ospx.flubundle;

import java.util.Locale;

/**
 * A default value factory that returns the key as the default value.
 */
public class NoopDefaultValueFactory implements DefaultValueFactory {
    @Override
    public String getDefaultValue(String key, Locale locale) {
        return key;
    }
}
