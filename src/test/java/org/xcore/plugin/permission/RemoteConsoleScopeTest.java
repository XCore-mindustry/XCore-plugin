package org.xcore.plugin.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteConsoleScopeTest {

    @Test
    @DisplayName("The console is remote only while a relayed command runs")
    void scope() {
        RemoteConsoleScope scope = new RemoteConsoleScope();
        List<Actor> seen = new ArrayList<>();

        seen.add(scope.consoleActor());
        scope.run("hub", () -> seen.add(scope.consoleActor()));
        seen.add(scope.consoleActor());

        assertThat(seen).containsExactly(Actor.LOCAL_CONSOLE, new Actor.RemoteConsole("hub"), Actor.LOCAL_CONSOLE);
    }

    @Test
    @DisplayName("A failing command does not leave the console marked as remote")
    void failure() {
        RemoteConsoleScope scope = new RemoteConsoleScope();

        assertThatThrownBy(() -> scope.run("hub", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(scope.consoleActor()).isEqualTo(Actor.LOCAL_CONSOLE);
    }

    @Test
    @DisplayName("Audit names tell the consoles apart and survive an unknown origin")
    void auditNames() {
        assertThat(Actor.LOCAL_CONSOLE.auditName()).isEqualTo("console");
        assertThat(new Actor.RemoteConsole("hub").auditName()).isEqualTo("remote-console@hub");
        assertThat(new Actor.RemoteConsole(" ").auditName()).isEqualTo("remote-console@unknown");
    }
}
