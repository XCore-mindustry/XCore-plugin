package org.xcore.plugin.localization;

import java.util.List;

public record TranslationProviderPipeline(List<TranslationProvider> providers) {

    public TranslationProviderPipeline {
        providers = List.copyOf(providers);
    }
}
