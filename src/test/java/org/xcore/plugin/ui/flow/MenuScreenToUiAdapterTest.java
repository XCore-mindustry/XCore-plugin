package org.xcore.plugin.ui.flow;

import mindustry.ui.builder.UiBuilder.NodeBuilder;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.ui.LocalizerResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MenuScreenToUiAdapterTest {

    @Test
    @DisplayName("MenuScreen converts into a VNode tree and compiles to NodeBuilder")
    void menuScreenConvertsToVNodeAndCompiles() {
        MenuScreen screen = MenuScreen.normal("Server Menu", "Welcome to XCore Server!", List.of(
                List.of(MenuButton.of("Play", "action.play"), MenuButton.of("Settings", "action.settings")),
                List.of(MenuButton.of("Exit", "action.exit"))
        ));

        NodeBuilder<?> compiled = MenuScreenToUiAdapter.compile(screen, LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiled);

        // Content text label
        assertThat(dsl).contains("Welcome to XCore Server!");

        // Buttons with sequential 0-indexed clicked results matching option indices
        assertThat(dsl).contains("button: Play");
        assertThat(dsl).contains("clicked: \"0\"");

        assertThat(dsl).contains("button: Settings");
        assertThat(dsl).contains("clicked: \"1\"");

        assertThat(dsl).contains("button: Exit");
        assertThat(dsl).contains("clicked: \"2\"");
    }

    @Test
    @DisplayName("MenuScreen without content converts button matrix cleanly")
    void menuScreenWithoutContent() {
        MenuScreen screen = MenuScreen.normal("Quick Select", "", List.of(
                List.of(MenuButton.of("Option A", "opt.a"), MenuButton.of("Option B", "opt.b"))
        ));

        NodeBuilder<?> compiled = MenuScreenToUiAdapter.compile(screen);
        String dsl = UiDslWriter.write(compiled);

        assertThat(dsl).contains("button: \"Option A\"");
        assertThat(dsl).contains("clicked: \"0\"");
        assertThat(dsl).contains("button: \"Option B\"");
        assertThat(dsl).contains("clicked: \"1\"");
    }
}
