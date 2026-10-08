package org.xcore.plugin.localization;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.MissingKeyPolicy;
import com.ospx.flubundle.mindustry.Messenger;
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
        bundle.setMissingKeyPolicy(MissingKeyPolicy.logOnce());
        return bundle;
    }

    /** Delivers messages in each player's selected language; shared with dependent plugins. */
    @Bean
    public Messenger messenger(Bundle bundle) {
        return Messenger.of(bundle);
    }
}
