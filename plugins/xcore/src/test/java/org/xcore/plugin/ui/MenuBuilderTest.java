package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuBuilderTest {

    private MenuService menuService;
    private Session session;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Provider<SessionService> sessionServiceProvider = mock(Provider.class);
        menuService = new MenuService(sessionServiceProvider, mock(MindustryMenuGateway.class));
        session = session();
    }

    @Test
    @DisplayName("addButtonText keeps raw button text")
    void addButtonText_keepsRawButtonText() {
        MenuBuilder builder = new MenuBuilder(menuService, session);

        builder.start()
                .addButtonText("[green]Save", () -> {})
                .end();

        assertThat(builder.rows).containsExactly(
                java.util.List.of("[green]Save")
        );
    }

    @Test
    @DisplayName("addButtonKey localizes button key once")
    void addButtonKey_localizesButtonKeyOnce() {
        MenuBuilder builder = new MenuBuilder(menuService, session);

        builder.start()
                .addButtonKey("save", () -> {})
                .end();

        assertThat(builder.rows).containsExactly(
                java.util.List.of("localized:save")
        );
    }

    @Test
    @DisplayName("addRowText keeps raw row text")
    void addRowText_keepsRawRowText() {
        MenuBuilder builder = new MenuBuilder(menuService, session);

        builder.addRowText("[red]Cancel", () -> {});

        assertThat(builder.rows).containsExactly(
                java.util.List.of("[red]Cancel")
        );
    }

    @Test
    @DisplayName("addRowKey localizes row key once")
    void addRowKey_localizesRowKeyOnce() {
        MenuBuilder builder = new MenuBuilder(menuService, session);

        builder.addRowKey("cancel", () -> {});

        assertThat(builder.rows).containsExactly(
                java.util.List.of("localized:cancel")
        );
    }

    @Test
    @DisplayName("addNavigationRow shows back when route history exists")
    void addNavigationRow_showsBackWhenRouteHistoryExists() {
        MenuBuilder builder = new MenuBuilder(menuService, session);
        session.pushRouteHistory(org.xcore.plugin.ui.route.MenuRoute.of("route.one"));

        builder.addNavigationRow();

        assertThat(builder.rows).containsExactly(
                java.util.List.of("localized:back", "localized:close")
        );
    }

    @Test
    @DisplayName("addNavigationRow shows back when legacy history exists")
    void addNavigationRow_showsBackWhenLegacyHistoryExists() {
        MenuBuilder builder = new MenuBuilder(menuService, session);
        session.pushHistory(() -> {});

        builder.addNavigationRow();

        assertThat(builder.rows).containsExactly(
                java.util.List.of("localized:back", "localized:close")
        );
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("uuid-1", true);
        Session session = new Session(
                new TomlSecretsConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> "localized:" + invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> "localized:" + invocation.getArgument(0));
        when(localization.format(anyString())).thenAnswer(invocation -> "localized:" + invocation.getArgument(0));
        when(localization.format(anyString(), anyMap())).thenAnswer(invocation -> "localized:" + invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;
        return session;
    }
}
