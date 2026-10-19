package org.xcore.plugin.ui.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.VNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MenuScreenToUiAdapterTest {

    @Test
    @DisplayName("a screen is drawn with its title, its text and a button for every action")
    void window_showsTheScreen() {
        MenuScreen screen = MenuScreen.normal("Server Menu", "Welcome to XCore Server!", List.of(
                List.of(MenuButton.of("Play", "action.play"), MenuButton.of("Settings", "action.settings")),
                List.of(MenuButton.of("Exit", "action.exit"))
        ));

        VNode window = MenuScreenToUiAdapter.window(screen, Screen.NARROW);

        assertThat(LayoutAssert.texts(window))
                .containsExactly("[accent]Server Menu[]", "Welcome to XCore Server!", "Play", "Settings", "Exit");
        // A button answers with its place among the buttons of the screen.
        assertThat(LayoutAssert.actions(window)).containsExactly("0", "1", "2");
    }

    @Test
    @DisplayName("a screen with no title and no text is its buttons alone")
    void window_withoutTitleAndText() {
        MenuScreen screen = MenuScreen.normal("", "  ", List.of(
                List.of(MenuButton.of("Option A", "opt.a"), MenuButton.of("Option B", "opt.b"))
        ));

        VNode window = MenuScreenToUiAdapter.window(screen, Screen.SMALL);

        assertThat(LayoutAssert.texts(window)).containsExactly("Option A", "Option B");
        assertThat(LayoutAssert.actions(window)).containsExactly("0", "1");
    }

    @Test
    @DisplayName("the dialog closes itself, so a screen's own button for it is left out and the numbers stay")
    void window_leavesOutTheCloseButton() {
        MenuScreen screen = MenuScreen.normal("Title", "Text", List.of(
                List.of(MenuButton.of("First", "first")),
                List.of(MenuButton.of("Close", "close"), MenuButton.of("Second", "second")),
                List.of(MenuButton.of("Back", "back"), MenuButton.of("Close", "close"))
        ));

        VNode window = MenuScreenToUiAdapter.window(screen, Screen.NARROW);

        assertThat(LayoutAssert.allText(window)).doesNotContain("Close");
        assertThat(LayoutAssert.actions(window)).containsExactly("0", "2", "3");
        // The way back is a button under the part that scrolls, with the arrow the other menus have.
        assertThat(LayoutAssert.texts(window).getLast()).endsWith(" Back");
    }

    @Test
    @DisplayName("both layouts travel in one packet, each under the condition of its screens")
    void toVNode_laysOutForBothClasses() {
        MenuScreen screen = MenuScreen.normal("Title", "Text", List.of(List.of(MenuButton.of("OK", "ok"))));

        VNode root = MenuScreenToUiAdapter.toVNode(screen);

        assertThat(LayoutAssert.actions(root)).containsExactly("0", "0");
        assertThat(LayoutAssert.dsl(root)).contains("width >= 490").contains("width < 490");
        LayoutAssert.assertFitsPacket(root, "a small screen");
    }

    @Test
    @DisplayName("long texts and crowded rows still fit the screen they are laid out for")
    void window_isLaidOutForEveryScreen() {
        List<MenuButton> crowded = new ArrayList<>();
        for (String text : List.of("-1 день", "+1 день", "-1 час", "+1 час", "+15 минут")) {
            crowded.add(MenuButton.of(text, text));
        }
        MenuScreen screen = MenuScreen.followUp(
                "[orange]XCore — Очень длинное название меню, которое не помещается в одну строку",
                "Первая строка текста.\n\nВторая строка, намного длиннее первой, чтобы её пришлось переносить"
                        + " на телефоне несколько раз подряд.\n[accent]Третья[]",
                List.of(
                        crowded,
                        List.of(MenuButton.of("Кнопка с очень длинным текстом, который не помещается в одну строку", "long")),
                        List.of(MenuButton.of("Две строки\n[gray]с подписью[]", "two"), MenuButton.of("Коротко", "short")),
                        List.of(MenuButton.of("Назад", "back"), MenuButton.of("Закрыть", "close"))
                ));

        for (Screen layout : List.of(Screen.NARROW, Screen.SMALL)) {
            VNode window = MenuScreenToUiAdapter.window(screen, layout);
            LayoutAssert.assertLaidOut(window, layout);
            assertThat(LayoutAssert.actions(window)).containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8");
        }
    }
}
