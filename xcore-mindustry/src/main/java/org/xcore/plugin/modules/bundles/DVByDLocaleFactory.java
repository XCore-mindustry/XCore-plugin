package org.xcore.plugin.modules.bundles;

import com.ospx.flubundle.DefaultValueFactory;

import java.util.Locale;
import java.util.Map;

import static org.xcore.plugin.PluginVars.bundle;

/**
 * Default Value By Default Locale Factory
 */
public class DVByDLocaleFactory implements DefaultValueFactory {
    @Override
    public String getDefaultValue(String key, Map<String, Object> args, Locale locale) {
        return locale.equals(bundle.defaultLocale) ? key : bundle.format(bundle.defaultLocale, key, args);
    }
}
