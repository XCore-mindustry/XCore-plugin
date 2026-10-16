package org.xcore.plugin.integration.blockhistory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockHistoryRegistryTest {
    private final BlockHistoryRegistry registry = new BlockHistoryRegistry();
    private final BlockHistorySummary summary = new BlockHistorySummary(600, 2, 1, 0, false, List.of(), null, 5);

    @Test
    void absentProviderIsSafeAndLastRegistrationWins() {
        assertThat(registry.summarize("player", 600)).isEmpty();
        var first = registry.register((uuid, window) -> Optional.empty());
        var second = registry.register((uuid, window) -> Optional.of(summary));
        first.close();
        assertThat(registry.summarize("player", 600)).contains(summary);
        second.close();
        second.close();
        assertThat(registry.summarize("player", 600)).isEmpty();
    }

    @Test
    void repeatedInstanceRegistrationsStillHaveDistinctOwnership() {
        BlockHistoryProvider provider = (uuid, window) -> Optional.of(summary);
        var first = registry.register(provider);
        var second = registry.register(provider);
        first.close();
        assertThat(registry.summarize("player", 600)).contains(summary);
        second.close();
        assertThat(registry.summarize("player", 600)).isEmpty();
    }

    @Test
    void nativeFailureIsSoftAndShutdownClearsAndClosesRegistry() {
        registry.register((uuid, window) -> { throw new UnsatisfiedLinkError("missing"); });
        assertThat(registry.summarize("player", 600)).isEmpty();
        registry.stop();
        assertThat(registry.summarize("player", 600)).isEmpty();
        assertThatThrownBy(() -> registry.register((uuid, window) -> Optional.empty()))
                .isInstanceOf(IllegalStateException.class);
    }
}
