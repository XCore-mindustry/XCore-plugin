package com.ospx.flubundle;

import java.util.Locale;

/**
 * This interface is used to provide default values for keys that are not found in any of the
 * sources.
 */
public interface DefaultValueFactory {
    String getDefaultValue(String key, Locale locale);
}
