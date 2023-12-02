package com.ospx.flubundle;

import java.util.Locale;

public class NoopDefaultValueFactory implements DefaultValueFactory {
    @Override
    public String getDefaultValue(String key, Locale locale) {
        return key;
    }
}
