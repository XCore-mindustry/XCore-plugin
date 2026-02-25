package org.xcore.plugin.service;

import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.localization.TranslatorLanguagesProvider;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindServiceTest {

    private BeanScope scope;
    private FindService findService;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        scope = BeanScope.builder()
                .modules(new FindServiceModule())
                .forTesting()
                .mock(SessionService.class)
                .build();

        findService = scope.get(FindService.class);
        sessionService = scope.get(SessionService.class);
    }

    @AfterEach
    void tearDown() {
        scope.close();
    }

    @Test
    @DisplayName("findTranslatorLanguage returns matching prefix")
    void findTranslatorLanguage_returnsMatchingPrefix() {
        var result = findService.findTranslatorLanguage("en_US");

        assertThat(result).isEqualTo("en");
    }

    @Test
    @DisplayName("findTranslatorLanguage returns null when no match")
    void findTranslatorLanguage_returnsNull_whenNoMatch() {
        var result = findService.findTranslatorLanguage("zz_ZZ");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("playerData by pid prefix delegates to SessionService")
    void playerData_byPidPrefix_delegatesToSessionService() {
        var expected = PlayerData.builder().pid(12).uuid("uuid-12").build();
        when(sessionService.getOrLoadFromDb(12)).thenReturn(expected);

        var result = findService.playerData("#12");

        assertThat(result).isSameAs(expected);
        verify(sessionService).getOrLoadFromDb(12);
    }

    @Test
    @DisplayName("playerData by uuid delegates to SessionService")
    void playerData_byUuid_delegatesToSessionService() {
        var expected = PlayerData.builder().uuid("uuid-1").build();
        when(sessionService.getOrLoadFromDb("uuid-1")).thenReturn(expected);

        var result = findService.playerData("uuid-1");

        assertThat(result).isSameAs(expected);
        verify(sessionService).getOrLoadFromDb("uuid-1");
    }

    @Test
    @DisplayName("playerById returns null when input has no hash prefix")
    void playerById_returnsNull_whenNoHashPrefix() {
        var result = findService.playerById("12");

        assertThat(result).isNull();
    }

    private static final class FindServiceModule implements AvajeModule {
        @Override
        public Class<?>[] classes() {
            return new Class<?>[]{FindService.class, TranslatorLanguagesProvider.class};
        }

        @Override
        public void build(Builder builder) {
            if (builder.isBeanAbsent(TranslatorLanguagesProvider.class)) {
                builder.register(new TranslatorLanguagesProvider());
            }
            if (builder.isBeanAbsent(FindService.class)) {
                builder.register(new FindService(
                        builder.get(SessionService.class),
                        builder.get(TranslatorLanguagesProvider.class)
                ));
            }
        }
    }
}
