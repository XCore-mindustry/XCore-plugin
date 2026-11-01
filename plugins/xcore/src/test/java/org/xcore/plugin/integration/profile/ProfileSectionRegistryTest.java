package org.xcore.plugin.integration.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.PlayerData;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileSectionRegistryTest {
    private ProfileSectionRegistry registry;
    private final PlayerData player = new PlayerData("u1", true);

    @BeforeEach
    void setUp() {
        registry = new ProfileSectionRegistry();
    }

    private static ProfileSectionProvider provider(String id, int priority, String headline) {
        return new ProfileSectionProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public Optional<ProfileSectionView> load(PlayerData target) {
                return Optional.of(local -> new ProfileSection(headline, List.of(), List.of()));
            }
        };
    }

    private List<String> headlines() {
        return registry.load(player).stream().map(view -> view.render(null).headline()).toList();
    }

    @Test
    @DisplayName("sections come back by priority, ties in registration order")
    void load_ordersByPriority() {
        registry.register(provider("a", 10, "A"));
        registry.register(provider("b", 20, "B"));
        registry.register(provider("c", 10, "C"));

        assertThat(headlines()).containsExactly("B", "A", "C");
    }

    @Test
    @DisplayName("a provider with nothing to show leaves no section")
    void load_skipsEmpty() {
        registry.register(new ProfileSectionProvider() {
            @Override
            public String id() {
                return "quiet";
            }

            @Override
            public Optional<ProfileSectionView> load(PlayerData target) {
                return Optional.empty();
            }
        });
        registry.register(provider("loud", 0, "L"));

        assertThat(headlines()).containsExactly("L");
    }

    @Test
    @DisplayName("a provider that throws is skipped, the others still load")
    void load_survivesFailure() {
        registry.register(new ProfileSectionProvider() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public int priority() {
                return 100;
            }

            @Override
            public Optional<ProfileSectionView> load(PlayerData target) {
                throw new IllegalStateException("database is down");
            }
        });
        registry.register(provider("fine", 0, "F"));

        assertThat(headlines()).containsExactly("F");
    }

    @Test
    @DisplayName("an id can be registered once; closing the registration frees it")
    void register_rejectsDuplicatesUntilClosed() {
        ProfileSectionRegistry.Registration first = registry.register(provider("dup", 0, "1"));

        assertThatThrownBy(() -> registry.register(provider("dup", 0, "2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup");
        assertThatThrownBy(() -> registry.register(provider(" ", 0, "x")))
                .isInstanceOf(IllegalArgumentException.class);

        first.close();
        first.close();
        registry.register(provider("dup", 0, "2"));

        assertThat(headlines()).containsExactly("2");
    }

    @Test
    @DisplayName("nobody to load for means no sections")
    void load_nullTarget() {
        registry.register(provider("a", 0, "A"));

        assertThat(registry.load(null)).isEmpty();
    }
}
