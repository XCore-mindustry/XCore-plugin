package org.xcore.plugin.localization;

import arc.files.Fi;
import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.MenuService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalizationUserLanguageTest {

    private Bundle bundle;

    @BeforeEach
    void setUp() {
        bundle = new Bundle();
        bundle.addSource(new Fi("src/main/resources/bundles"));
        bundle.addLocaleAlias("uk", "uk_UA");
    }

    @Test
    @DisplayName("session localization prefers saved player language over client locale")
    void sessionLocalizationPrefersSavedLanguage() {
        Session session = session("en", "ru");

        assertThat(session.locale().format("close")).isEqualTo("[scarlet]Закрыть");
        assertThat(session.locale().localizer().locale()).isEqualTo(java.util.Locale.of("ru"));
    }

    @Test
    @DisplayName("session localization falls back to client locale when language is auto")
    void sessionLocalizationUsesClientLocaleWhenAuto() {
        Session session = session("uk", "auto");

        assertThat(session.locale().format("close")).isEqualTo("[scarlet]Закрити");
        assertThat(session.locale().localizer().locale()).isEqualTo(java.util.Locale.of("uk", "UA"));
    }

    @Test
    @DisplayName("setLocale persists normalized player language for subsequent formatting")
    void setLocalePersistsNormalizedLanguage() {
        Session session = session("en", "auto");

        session.locale().setLocale("ru");

        assertThat(session.data.language).isEqualTo("ru");
        assertThat(session.locale().format("close")).isEqualTo("[scarlet]Закрыть");
    }

    private Session session(String clientLocale, String savedLanguage) {
        Player player = Player.create();
        player.locale = clientLocale;

        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        when(repository.updateLanguage("uuid-1", "ru")).thenReturn(true);
        when(repository.updateLanguage("uuid-1", "auto")).thenReturn(true);
        when(repository.updateLanguage("uuid-1", savedLanguage)).thenReturn(true);

        PlayerData data = PlayerData.builder()
                .uuid("uuid-1")
                .language(savedLanguage)
                .build();

        return new Session(
                new TomlSecretsConfig(),
                bundle,
                mock(MenuService.class),
                repository,
                player,
                data
        );
    }
}
