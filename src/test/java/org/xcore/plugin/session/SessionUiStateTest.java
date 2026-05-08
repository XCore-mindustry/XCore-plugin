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
        session.setDraft("draft");
        session.sortStatus.put("key", StatusEnum.Active);

        session.clearUiState();

        assertThat(session.hasHistory()).isTrue();
        assertThat(session.hasDraft(String.class)).isTrue();
        assertThat(session.sortStatus).containsKey("key");
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("uuid-1", true);
        return new Session(
                new GlobalConfig(),
                mock(Bundle.class),
                mock(MenuService.class),
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }
}
