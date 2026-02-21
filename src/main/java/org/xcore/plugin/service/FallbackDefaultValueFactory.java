package org.xcore.plugin.service;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.DefaultValueFactory;

import java.util.Locale;
import java.util.Map;

public class FallbackDefaultValueFactory implements DefaultValueFactory {

    private final Bundle bundle;

    public FallbackDefaultValueFactory(Bundle bundle) {
        this.bundle = bundle;
    }

    @Override
    public String getDefaultValue(String key, Map<String, Object> args, Locale locale) {
        if (locale.equals(bundle.defaultLocale)) {
            return key;
        }

        return bundle.format(bundle.defaultLocale, key, args);
    }
}
