package org.xcore.plugin.integration;

import mindustry.gen.Player;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.PlayerData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerDisplayRegistryTest {

    @Test
    void resolvesProvidersByPriorityWithStableOrderForTies() {
        var registry = new PlayerDisplayRegistry();

        registry.register(provider("low", 1, "low"));
        registry.register(provider("tie-first", 5, "first"));
        registry.register(provider("high", 10, "high"));
        registry.register(provider("tie-second", 5, "second"));

        assertThat(registry.resolve(new PlayerData(), null))
                .containsExactly("high", "first", "second", "low");
    }

    @Test
    void rejectsDuplicateAndNullProviders() {
        var registry = new PlayerDisplayRegistry();
        registry.register(provider("duplicate", 0, "one"));

        assertThatThrownBy(() -> registry.register(provider("duplicate", 0, "two")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void ignoresNullAndBlankResults() {
        var registry = new PlayerDisplayRegistry();
        registry.register(provider("null", 3, null));
        registry.register(provider("blank", 2, "   "));
        registry.register(provider("value", 1, "tag"));

        assertThat(registry.resolve(new PlayerData(), null)).containsExactly("tag");
    }

    @Test
    void isolatesProviderExceptions() {
        var registry = new PlayerDisplayRegistry();
        registry.register(new PlayerDisplayProvider() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public String resolve(PlayerData data, Player player) {
                throw new IllegalStateException("provider failure");
            }
        });
        registry.register(provider("healthy", 0, "healthy"));

        assertThat(registry.resolve(new PlayerData(), null)).containsExactly("healthy");
    }

    @Test
    void closingRegistrationIsIdempotentAndOnlyUnregistersItsProvider() {
        var registry = new PlayerDisplayRegistry();
        var first = registry.register(provider("first", 0, "first"));
        registry.register(provider("second", 0, "second"));

        first.close();
        first.close();

        assertThat(registry.resolve(new PlayerData(), null)).containsExactly("second");
    }

    private static PlayerDisplayProvider provider(String id, int priority, String result) {
        return new PlayerDisplayProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public String resolve(PlayerData data, Player player) {
                return result;
            }
        };
    }
}
