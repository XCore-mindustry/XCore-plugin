package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
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
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.MindustryMenuGateway;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuFlow;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DateTimePickerFlowTest {

    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private Session session;

    @BeforeEach
    void setUp() {
        gateway = mock(MindustryMenuGateway.class);
        menuService = new MenuService(null, gateway);
        menuService.registerRoute(new OriginFlow());
        menuService.registerRoute(new DateTimePickerFlows.PickerFlow());
        session = session();
    }

    @Test
    @DisplayName("manual input updates picker state and re-renders the picker route")
    void manualInput_updatesPickerStateAndRendersRoute() {
        DateTimePickerFlows.PickerState state = DateTimePickerFlows.state("event-menu-edit-planned-start", 0L, value -> {
        });
        session.setDraft(DateTimePickerFlows.PickerState.class, state);

        menuService.renderRoute(session, MenuRoute.of(DateTimePickerFlows.ROUTE_PICKER));

        dispatchMenuOption(17);

        verify(gateway).textInput(eq(session.player), eq(0), eq("date-time-picker-manual-title"), eq("date-time-picker-manual-message"), eq(64), eq(""), eq(false));
        assertThat(session.activePrompt()).isNotNull();

        long before = System.currentTimeMillis();
        dispatchPromptSubmit("+2h");
        long after = System.currentTimeMillis();

        assertThat(state.selectedTime).isBetween(before + 7_200_000L, after + 7_200_000L);
        assertThat(session.activePrompt()).isNull();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of(DateTimePickerFlows.ROUTE_PICKER));
    }

    @Test
    @DisplayName("apply invokes callback, clears picker draft, and returns to previous route")
    void apply_invokesCallbackAndReturnsToPreviousRoute() {
        menuService.renderRoute(session, MenuRoute.of("origin"));

        AtomicLong appliedValue = new AtomicLong(-1L);
        DateTimePickerFlows.PickerState state = DateTimePickerFlows.state("event-menu-edit-planned-end", 12345L, appliedValue::set);
        session.setDraft(DateTimePickerFlows.PickerState.class, state);

        menuService.openRoute(session, MenuRoute.of(DateTimePickerFlows.ROUTE_PICKER));
        dispatchMenuOption(15);

        assertThat(appliedValue.get()).isEqualTo(12345L);
        assertThat(session.hasDraft(DateTimePickerFlows.PickerState.class)).isFalse();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("origin"));
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("test-uuid", true);
        data.uuid = "test-uuid";
        Session session = new Session(
                new TomlSecretsConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;
        return session;
    }

    @SuppressWarnings("unchecked")
    private void dispatchMenuOption(int option) {
        var screen = session.activeScreen();
        assertThat(screen).isNotNull();
        assertThat(screen.hasFlow()).isTrue();

        MenuFlow<Object> flow = (MenuFlow<Object>) screen.flow();
        MenuRenderContext<Object> context = new MenuRenderContext<>(menuService, session, flow, screen.state(), screen.route());
        flow.onAction(context, screen.actionIdAt(option));
    }

    @SuppressWarnings("unchecked")
    private void dispatchPromptSubmit(String text) {
        var prompt = session.activePrompt();
        assertThat(prompt).isNotNull();
        assertThat(prompt.hasFlow()).isTrue();

        session.clearActivePrompt();

        MenuFlow<Object> flow = (MenuFlow<Object>) prompt.flow();
        MenuRenderContext<Object> context = new MenuRenderContext<>(menuService, session, flow, prompt.state(), prompt.route());
        flow.onPromptSubmit(context, prompt.promptIdString(), text);
    }

    private static final class OriginFlow extends BaseMenuFlow<OriginState> {

        private OriginFlow() {
            super("origin", OriginState.class);
        }

        @Override
        public OriginState createState(Session session, MenuRoute route, OriginState currentState) {
            return currentState == null ? new OriginState() : currentState;
        }

        @Override
        public MenuScreen render(MenuRenderContext<OriginState> context) {
            return MenuScreen.normal("origin", "origin", List.of(List.of(MenuButton.of("Origin", "close"))));
        }
    }

    private static final class OriginState {
    }
}
