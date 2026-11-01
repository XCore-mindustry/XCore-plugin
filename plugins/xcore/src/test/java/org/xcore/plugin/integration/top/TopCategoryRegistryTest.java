package org.xcore.plugin.integration.top;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.localization.Localization;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopCategoryRegistryTest {

    private TopCategoryRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TopCategoryRegistry();
    }

    private TopCategoryProvider provider(String id, int priority) {
        return new TopCategoryProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String displayName(Localization local) {
                return "Title:" + id;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public LeaderboardPage loadPage(LeaderboardPageRequest request) {
                return LeaderboardPage.empty(request.page());
            }
        };
    }

    @Test
    @DisplayName("register validates non-null and non-blank id")
    void register_validatesId() {
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> registry.register(provider("", 10)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> registry.register(provider("   ", 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("register rejects duplicate id case-insensitively")
    void register_rejectsDuplicates() {
        registry.register(provider("custom-top", 10));

        assertThatThrownBy(() -> registry.register(provider("custom-top", 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        assertThatThrownBy(() -> registry.register(provider("CUSTOM-TOP", 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("registerIfAbsent is idempotent")
    void registerIfAbsent_isIdempotent() {
        var r1 = registry.registerIfAbsent(provider("test", 10));
        assertThat(r1).isPresent();

        var r2 = registry.registerIfAbsent(provider("TEST", 20));
        assertThat(r2).isEmpty();

        assertThat(registry.all()).hasSize(1);
    }

    @Test
    @DisplayName("resolve finds provider case-insensitively")
    void resolve_caseInsensitive() {
        var p = provider("hexed-elo", 10);
        registry.register(p);

        assertThat(registry.resolve("hexed-elo")).contains(p);
        assertThat(registry.resolve("HEXED-ELO")).contains(p);
        assertThat(registry.resolve("Hexed-Elo")).contains(p);
        assertThat(registry.resolve("unknown")).isEmpty();
        assertThat(registry.resolve(null)).isEmpty();
    }

    @Test
    @DisplayName("all sorts providers by priority descending then registration order")
    void all_sortsByPriorityDescending() {
        var low = provider("low", 5);
        var high = provider("high", 50);
        var mid1 = provider("mid1", 20);
        var mid2 = provider("mid2", 20);

        registry.register(low);
        registry.register(high);
        registry.register(mid1);
        registry.register(mid2);

        List<TopCategoryProvider> all = registry.all();
        assertThat(all).containsExactly(high, mid1, mid2, low);
    }

    @Test
    @DisplayName("unregister removes provider and is idempotent")
    void unregister_removesProvider() {
        var p1 = provider("p1", 10);
        var p2 = provider("p2", 20);

        var reg1 = registry.register(p1);
        registry.register(p2);

        assertThat(registry.all()).containsExactly(p2, p1);
        assertThat(reg1.isRegistered()).isTrue();

        reg1.unregister();
        assertThat(reg1.isRegistered()).isFalse();
        assertThat(registry.all()).containsExactly(p2);
        assertThat(registry.resolve("p1")).isEmpty();

        // Second unregister call does nothing
        reg1.unregister();
        assertThat(registry.all()).containsExactly(p2);
    }

    @Test
    @DisplayName("resolveDefault prioritizes configured default over fallback and first provider")
    void resolveDefault_priority() {
        var p1 = provider("p1", 10);
        var p2 = provider("p2", 20);
        registry.register(p1);
        registry.register(p2);

        // No default configured, no fallback: resolves first provider by priority (p2)
        assertThat(registry.resolveDefault(null)).contains(p2);

        // Fallback provided: resolves fallback
        assertThat(registry.resolveDefault("p1")).contains(p1);

        // Configured default overrides fallback
        registry.setDefaultCategory("p1");
        assertThat(registry.resolveDefault("p2")).contains(p1);

        registry.clearDefaultCategory();
        assertThat(registry.resolveDefault("p2")).contains(p2);
    }
}
