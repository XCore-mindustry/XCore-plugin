package org.xcore.plugin.ui.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveMenuScreenTest {

    @Test
    @DisplayName("runAction executes callback at valid index")
    void runAction_executesCallbackAtValidIndex() {
        AtomicBoolean ran = new AtomicBoolean(false);
        List<MenuAction> actions = List.of(
                new MenuAction.CallbackAction(() -> ran.set(true))
        );
        ActiveMenuScreen screen = ActiveMenuScreen.create(1L, MenuMode.NORMAL, actions);

        screen.runAction(0);

        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("runAction ignores invalid negative index")
    void runAction_ignoresInvalidNegativeIndex() {
        AtomicBoolean ran = new AtomicBoolean(false);
        List<MenuAction> actions = List.of(
                new MenuAction.CallbackAction(() -> ran.set(true))
        );
        ActiveMenuScreen screen = ActiveMenuScreen.create(1L, MenuMode.NORMAL, actions);

        screen.runAction(-1);

        assertThat(ran).isFalse();
    }

    @Test
    @DisplayName("runAction ignores out-of-range index")
    void runAction_ignoresOutOfRangeIndex() {
        AtomicBoolean ran = new AtomicBoolean(false);
        List<MenuAction> actions = List.of(
                new MenuAction.CallbackAction(() -> ran.set(true))
        );
        ActiveMenuScreen screen = ActiveMenuScreen.create(1L, MenuMode.NORMAL, actions);

        screen.runAction(5);

        assertThat(ran).isFalse();
    }

    @Test
    @DisplayName("create defensively copies actions")
    void create_defensivelyCopiesActions() {
        List<MenuAction> original = new ArrayList<>();
        original.add(new MenuAction.CallbackAction(() -> {}));
        ActiveMenuScreen screen = ActiveMenuScreen.create(1L, MenuMode.NORMAL, original);

        assertThat(screen.actionCount()).isEqualTo(1);
        original.clear();
        assertThat(screen.actionCount()).isEqualTo(1);
    }
}
