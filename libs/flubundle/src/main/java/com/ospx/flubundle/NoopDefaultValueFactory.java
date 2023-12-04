package com.ospx.flubundle;

import java.util.Locale;
import java.util.Map;

/**
 * A default value factory that returns the key as the default value.
 */
public class NoopDefaultValueFactory implements DefaultValueFactory {
    @Override
    public String getDefaultValue(String key, Map<String, Object> args, Locale locale) {
        return key;
    }
}
