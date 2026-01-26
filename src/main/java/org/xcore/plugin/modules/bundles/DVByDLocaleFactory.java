package org.xcore.plugin.modules.bundles;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.DefaultValueFactory;

import java.util.Locale;
import java.util.Map;

public class DVByDLocaleFactory implements DefaultValueFactory {

    private final Bundle bundle;

    public DVByDLocaleFactory(Bundle bundle) {
        this.bundle = bundle;
    }

    @Override
    public String getDefaultValue(String key, Map<String, Object> args, Locale locale) {
        return locale.equals(bundle.defaultLocale) ? key : bundle.format(bundle.defaultLocale, key, args);
    }
}
