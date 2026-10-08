package org.xcore.plugin.localization;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.DefaultValueFactory;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.xcore.plugin.XcorePlugin;

@Factory
public class BundleFactory {

    @Bean
    public Bundle bundle() {
        Bundle bundle = Bundle.INSTANCE;
        bundle.addSource(XcorePlugin.class);
        bundle.addLocaleAlias("uk", "uk_UA");
        bundle.setDefaultValueFactory(DefaultValueFactory.logMissing(bundle.getDefaultValueFactory()));
        return bundle;
    }
}
