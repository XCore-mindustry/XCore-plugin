package org.xcore.plugin.session;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.flow.ActiveMenuPrompt;
import org.xcore.plugin.ui.flow.ActiveMenuScreen;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SessionUiStateTest {

    @Test
    @DisplayName("nextUiVersion increments")
    void nextUiVersion_increments() {
        Session session = session();

        assertThat(session.nextUiVersion()).isEqualTo(1L);
        assertThat(session.nextUiVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("clearUiState clears active screen, active prompt, legacy actions, and textHandler")
    void clearUiState_clearsUiState() {
        Session session = session();
        session.setActiveScreen(ActiveMenuScreen.create(1L, MenuMode.NORMAL, List.of()));
        session.setActivePrompt(ActiveMenuPrompt.create(1L, 42, s -> {}, () -> {}));
        session.actions.add(() -> {});
        session.textHandler = s -> {};

        session.clearUiState();

        assertThat(session.activeScreen()).isNull();
        assertThat(session.activePrompt()).isNull();
        assertThat(session.actions).isEmpty();
        assertThat(session.textHandler).isNull();
    }

    @Test
    @DisplayName("clear delegates to clearUiState")
    void clear_delegatesToClearUiState() {
        Session session = session();
        session.setActiveScreen(ActiveMenuScreen.create(1L, MenuMode.NORMAL, List.of()));
        session.setActivePrompt(ActiveMenuPrompt.create(1L, 42, s -> {}, () -> {}));
        session.actions.add(() -> {});
        session.textHandler = s -> {};

        session.clear();

        assertThat(session.activeScreen()).isNull();
        assertThat(session.activePrompt()).isNull();
        assertThat(session.actions).isEmpty();
        assertThat(session.textHandler).isNull();
    }

    @Test
    @DisplayName("clearUiState does not clear history, drafts, or sortStatus")
    void clearUiState_preservesHistoryDraftsSortStatus() {
        Session session = session();
        session.pushHistory(() -> {});
        session.pushRouteHistory(MenuRoute.of("player.profile"));
        session.setDraft("draft");
        session.sortStatus.put("key", StatusEnum.Active);

        session.clearUiState();

        assertThat(session.hasHistory()).isTrue();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.hasDraft(String.class)).isTrue();
        assertThat(session.sortStatus).containsKey("key");
    }

    @Test
    @DisplayName("route history is LIFO")
    void routeHistory_isLifo() {
        Session session = session();
        session.pushRouteHistory(MenuRoute.of("route.one"));
        session.pushRouteHistory(MenuRoute.of("route.two"));

        assertThat(session.popRouteHistory()).isEqualTo(MenuRoute.of("route.two"));
        assertThat(session.popRouteHistory()).isEqualTo(MenuRoute.of("route.one"));
        assertThat(session.popRouteHistory()).isNull();
    }

    @Test
    @DisplayName("route history over max history drops oldest entry")
    void routeHistory_overMaxHistory_dropsOldestEntry() {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.maxHistory = 2;
        Session session = session(globalConfig);

        session.pushRouteHistory(MenuRoute.of("route.one"));
        session.pushRouteHistory(MenuRoute.of("route.two"));
        session.pushRouteHistory(MenuRoute.of("route.three"));

        assertThat(session.popRouteHistory()).isEqualTo(MenuRoute.of("route.three"));
        assertThat(session.popRouteHistory()).isEqualTo(MenuRoute.of("route.two"));
        assertThat(session.popRouteHistory()).isNull();
    }

    private Session session() {
        return session(new GlobalConfig());
    }

    private Session session(GlobalConfig globalConfig) {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("uuid-1", true);
        return new Session(
                globalConfig,
                mock(Bundle.class),
                mock(MenuService.class),
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }
}
