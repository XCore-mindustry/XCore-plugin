package org.xcore.plugin.ui.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MenuScreenTest {

    @Test
    @DisplayName("normal factory creates screen with NORMAL mode")
    void normalFactory_createsNormalMode() {
        MenuScreen screen = MenuScreen.normal("Title", "Content", List.of());
        assertThat(screen.mode()).isEqualTo(MenuMode.NORMAL);
        assertThat(screen.title()).isEqualTo("Title");
        assertThat(screen.content()).isEqualTo("Content");
    }

    @Test
    @DisplayName("followUp factory creates screen with FOLLOW_UP mode")
    void followUpFactory_createsFollowUpMode() {
        MenuScreen screen = MenuScreen.followUp("Title", "Content", List.of());
        assertThat(screen.mode()).isEqualTo(MenuMode.FOLLOW_UP);
    }

    @Test
    @DisplayName("defensively copies rows deeply")
    void defensiveCopy_deeplyCopiesRows() {
        List<List<MenuButton>> rows = new ArrayList<>();
        List<MenuButton> row = new ArrayList<>();
        row.add(MenuButton.of("A", "a1"));
        rows.add(row);

        MenuScreen screen = MenuScreen.normal("T", "C", rows);
        row.clear();

        assertThat(screen.rows()).hasSize(1);
        assertThat(screen.rows().get(0)).hasSize(1);
        assertThat(screen.rows().get(0).get(0).text()).isEqualTo("A");
    }

    @Test
    @DisplayName("toTextRows maps button texts")
    void toTextRows_mapsButtonTexts() {
        MenuScreen screen = MenuScreen.normal("T", "C", List.of(
                List.of(MenuButton.of("A", "a1"), MenuButton.of("B", "b1")),
                List.of(MenuButton.of("C", "c1"))
        ));

        List<List<String>> textRows = screen.toTextRows();

        assertThat(textRows).containsExactly(
                List.of("A", "B"),
                List.of("C")
        );
    }

    @Test
    @DisplayName("toActions maps buttons to NamedAction with null callback")
    void toActions_mapsToNamedAction() {
        MenuScreen screen = MenuScreen.normal("T", "C", List.of(
                List.of(MenuButton.of("A", "a1"))
        ));

        List<MenuAction> actions = screen.toActions();

        assertThat(actions).hasSize(1);
        MenuAction action = actions.get(0);
        assertThat(action).isInstanceOf(MenuAction.NamedAction.class);
        assertThat(((MenuAction.NamedAction) action).id()).isEqualTo("a1");
        // should not NPE when run without callback
        action.run();
    }

    @Test
    @DisplayName("actionIdAt returns correct id for valid index")
    void actionIdAt_returnsCorrectId() {
        MenuScreen screen = MenuScreen.normal("T", "C", List.of(
                List.of(MenuButton.of("A", "a1"), MenuButton.of("B", "b1")),
                List.of(MenuButton.of("C", "c1"))
        ));

        assertThat(screen.actionIdAt(0)).isEqualTo("a1");
        assertThat(screen.actionIdAt(1)).isEqualTo("b1");
        assertThat(screen.actionIdAt(2)).isEqualTo("c1");
    }

    @Test
    @DisplayName("actionIdAt returns null for invalid index")
    void actionIdAt_returnsNullForInvalidIndex() {
        MenuScreen screen = MenuScreen.normal("T", "C", List.of());
        assertThat(screen.actionIdAt(0)).isNull();
        assertThat(screen.actionIdAt(-1)).isNull();
    }

    @Test
    @DisplayName("actionCount sums all buttons")
    void actionCount_sumsAllButtons() {
        MenuScreen screen = MenuScreen.normal("T", "C", List.of(
                List.of(MenuButton.of("A", "a1"), MenuButton.of("B", "b1")),
                List.of(MenuButton.of("C", "c1"))
        ));
        assertThat(screen.actionCount()).isEqualTo(3);
    }
}
