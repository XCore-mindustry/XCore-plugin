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
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuMode;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuServiceTest {

    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private Session session;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        gateway = mock(MindustryMenuGateway.class);
        Provider<SessionService> sessionServiceProvider = mock(Provider.class);
        menuService = new MenuService(sessionServiceProvider, gateway);
        session = session();
    }

    @Test
    @DisplayName("show sets active screen mode NORMAL and calls gateway.menu")
    void show_setsActiveScreenModeNormalAndCallsGatewayMenu() {
        menuService.show(session, "Title", "Content", List.of(), List.of(), MenuMode.NORMAL);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        assertThat(session.activeScreen().version()).isEqualTo(1L);
        verify(gateway).menu(eq(session.player), eq(0), eq("Title"), eq("Content"), any());
    }

    @Test
    @DisplayName("show sets active screen mode FOLLOW_UP and calls gateway.followUpMenu")
    void show_setsActiveScreenModeFollowUpAndCallsGatewayFollowUpMenu() {
        menuService.show(session, "Title", "Content", List.of(), List.of(), MenuMode.FOLLOW_UP);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        assertThat(session.activeScreen().version()).isEqualTo(1L);
        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("Title"), eq("Content"), any());
    }

    @Test
    @DisplayName("hideFollowUp calls gateway.hideFollowUpMenu when active screen is FOLLOW_UP and clears active screen")
    void hideFollowUp_callsGatewayHideFollowUpWhenFollowUpActive() {
        menuService.show(session, "Title", "Content", List.of(), List.of(), MenuMode.FOLLOW_UP);
        assertThat(session.activeScreen()).isNotNull();

        menuService.hideFollowUp(session);

        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("hideFollowUp does not call gateway when active screen is NORMAL")
    void hideFollowUp_doesNotCallGatewayWhenNormalActive() {
        menuService.show(session, "Title", "Content", List.of(), List.of(), MenuMode.NORMAL);

        menuService.hideFollowUp(session);

        verify(gateway, never()).hideFollowUpMenu(any(), anyInt());
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("close hides follow-up when active and clears active screen and actions")
    void close_hidesFollowUpWhenActiveAndClearsScreenAndActions() {
        menuService.show(session, "Title", "Content", List.of(), List.of(), MenuMode.FOLLOW_UP);
        menuService.openTextPrompt(session, "Prompt", "Content", 100, "default", false, s -> {}, () -> {});
        session.actions.add(() -> {});
        session.textHandler = s -> {};
        assertThat(session.actions).isNotEmpty();

        menuService.close(session);

        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
        assertThat(session.activeScreen()).isNull();
        assertThat(session.activePrompt()).isNull();
        assertThat(session.actions).isEmpty();
        assertThat(session.textHandler).isNull();
    }

    @Test
    @DisplayName("close clears active screen and actions for NORMAL mode")
    void close_clearsScreenAndActionsForNormalMode() {
        menuService.show(session, "Title", "Content", List.of(), List.of(), MenuMode.NORMAL);
        menuService.openTextPrompt(session, "Prompt", "Content", 100, "default", false, s -> {}, () -> {});
        session.actions.add(() -> {});
        session.textHandler = s -> {};

        menuService.close(session);

        verify(gateway, never()).hideFollowUpMenu(any(), anyInt());
        assertThat(session.activeScreen()).isNull();
        assertThat(session.activePrompt()).isNull();
        assertThat(session.actions).isEmpty();
        assertThat(session.textHandler).isNull();
    }

    @Test
    @DisplayName("openTextPrompt sets active prompt and calls gateway.textInput")
    void openTextPrompt_setsActivePromptAndCallsGatewayTextInput() {
        Consumer<String> onSubmit = s -> {};
        Runnable onCancel = () -> {};

        menuService.openTextPrompt(session, "Title", "Content", 100, "default", false, onSubmit, onCancel);

        assertThat(session.activePrompt()).isNotNull();
        assertThat(session.activePrompt().promptId()).isEqualTo(0);
        assertThat(session.activePrompt().version()).isEqualTo(1L);
        verify(gateway).textInput(eq(session.player), eq(0), eq("Title"), eq("Content"), eq(100), eq("default"), eq(false));
    }

    @Test
    @DisplayName("builder show routes to service show with mode NORMAL")
    void builderShow_routesToServiceShowWithModeNormal() {
        MenuBuilder builder = new MenuBuilder(menuService, session);
        builder.title = "Builder Title";
        builder.content = "Builder Content";

        builder.show();

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        verify(gateway).menu(eq(session.player), eq(0), eq("Builder Title"), eq("Builder Content"), any());
    }

    @Test
    @DisplayName("builder showFollowUp routes to service show with mode FOLLOW_UP")
    void builderShowFollowUp_routesToServiceShowWithModeFollowUp() {
        MenuBuilder builder = new MenuBuilder(menuService, session);
        builder.title = "Builder Title";
        builder.content = "Builder Content";

        builder.showFollowUp();

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("Builder Title"), eq("Builder Content"), any());
    }

    @Test
    @DisplayName("builder followUp sets mode to FOLLOW_UP")
    void builderFollowUp_setsModeToFollowUp() {
        MenuBuilder builder = new MenuBuilder(menuService, session);

        assertThat(builder.mode).isEqualTo(MenuMode.NORMAL);
        builder.followUp();
        assertThat(builder.mode).isEqualTo(MenuMode.FOLLOW_UP);
    }

    @Test
    @DisplayName("builder show(int) routes to gateway.menu with custom menuId")
    void builderShowInt_routesToGatewayMenuWithCustomMenuId() {
        MenuBuilder builder = new MenuBuilder(menuService, session);
        builder.title = "Builder Title";
        builder.content = "Builder Content";

        builder.show(42);

        assertThat(session.activeScreen()).isNull();
        verify(gateway).menu(eq(session.player), eq(42), eq("Builder Title"), eq("Builder Content"), any());
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("uuid-1", true);
        return new Session(
                new TomlSecretsConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }
}
