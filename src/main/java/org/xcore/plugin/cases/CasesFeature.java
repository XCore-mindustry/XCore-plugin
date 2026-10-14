package org.xcore.plugin.cases;

import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;

@Singleton
public class CasesFeature {
    private final TomlSecretsConfig secrets;
    private final TomlXcoreConfig config;

    public CasesFeature(TomlSecretsConfig secrets, TomlXcoreConfig config) {
        this.secrets = secrets;
        this.config = config;
    }

    public boolean enabled() {
        return secrets.cases.reports.enabled && !config.runtime.disabledFeatures.contains("reports");
    }
}
