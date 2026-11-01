package org.xcore.plugin.localization;

import arc.files.Fi;
import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.Packets;
import org.xcore.plugin.concurrent.GameThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @Test
    @DisplayName("session locale resolver makes plain bundle calls honour the saved language")
    void sessionLocaleResolverAppliesToPlainBundleCalls() {
        Session russian = session("en", "ru");
        Session auto = session("uk", "auto");
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.get(russian.player)).thenReturn(russian);
        when(sessionService.get(auto.player)).thenReturn(auto);

        assertThat(bundle.locale(russian.player)).isEqualTo(java.util.Locale.ENGLISH);

        new SessionLocaleResolver(bundle, sessionService, null).install();

        assertThat(bundle.locale(russian.player)).isEqualTo(java.util.Locale.of("ru"));
        assertThat(bundle.localizer(russian.player).format("close")).isEqualTo("[scarlet]Закрыть");
        assertThat(bundle.locale(auto.player)).isEqualTo(java.util.Locale.of("uk", "UA"));

        Player withoutSession = Player.create();
        withoutSession.locale = "ru";
        assertThat(bundle.locale(withoutSession)).isEqualTo(java.util.Locale.of("ru"));
    }

    @AfterEach
    void clearGameThread() {
        GameThread.setGameThreadOverride(null);
    }

    private static Packets.ConnectPacket packet(String uuid, String locale) {
        Packets.ConnectPacket packet = new Packets.ConnectPacket();
        packet.uuid = uuid;
        packet.locale = locale;
        return packet;
    }

    @Test
    @DisplayName("before joining, a connection gets the stored language off the game thread")
    void connectPacketUsesStoredLanguage() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        when(repository.findLanguage("stored-ru")).thenReturn("ru");
        when(repository.findLanguage("stored-auto")).thenReturn("auto");
        long[] now = {0};
        new SessionLocaleResolver(bundle, sessionService, repository, () -> now[0]).install();
        GameThread.setGameThreadOverride(new Thread(() -> { }));

        assertThat(bundle.locale(packet("stored-ru", "en"))).isEqualTo(java.util.Locale.of("ru"));
        assertThat(bundle.format(bundle.locale(packet("stored-ru", "en")), "close")).isEqualTo("[scarlet]Закрыть");
        assertThat(bundle.locale(packet("stored-auto", "uk"))).isEqualTo(java.util.Locale.of("uk", "UA"));
        assertThat(bundle.locale(packet("unknown", "en"))).isEqualTo(java.util.Locale.ENGLISH);
        verify(repository, times(1)).findLanguage("stored-ru");

        now[0] += SessionLocaleResolver.FRESH_MILLIS;
        bundle.locale(packet("stored-ru", "en"));
        verify(repository, times(2)).findLanguage("stored-ru");
    }

    @Test
    @DisplayName("on the game thread the resolver uses only what it knows, never the database")
    void connectPacketOnGameThreadSkipsDatabase() {
        Session russian = session("en", "ru");
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.get(russian.player)).thenReturn(russian);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        new SessionLocaleResolver(bundle, sessionService, repository).install();
        GameThread.setGameThreadOverride(Thread.currentThread());

        assertThat(bundle.locale(packet("uuid-1", "en"))).isEqualTo(java.util.Locale.ENGLISH);

        // Any message to the player while online records the selected language.
        bundle.locale(russian.player);
        assertThat(bundle.locale(packet("uuid-1", "en"))).isEqualTo(java.util.Locale.of("ru"));
        verify(repository, never()).findLanguage("uuid-1");
    }

    @Test
    @DisplayName("before joining, a live session wins and a database failure falls back to the client locale")
    void connectPacketPrefersLiveSessionAndSurvivesDatabaseErrors() {
        Session russian = session("en", "ru");
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.get("uuid-1")).thenReturn(russian);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        when(repository.findLanguage("broken")).thenThrow(new IllegalStateException("db down"));
        new SessionLocaleResolver(bundle, sessionService, repository).install();
        GameThread.setGameThreadOverride(new Thread(() -> { }));

        assertThat(bundle.locale(packet("uuid-1", "en"))).isEqualTo(java.util.Locale.of("ru"));
        assertThat(bundle.locale(packet("broken", "uk"))).isEqualTo(java.util.Locale.of("uk", "UA"));
        assertThat(bundle.locale(packet(null, "uk"))).isEqualTo(java.util.Locale.of("uk", "UA"));
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
