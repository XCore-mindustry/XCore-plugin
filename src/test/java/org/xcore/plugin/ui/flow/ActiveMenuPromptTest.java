package org.xcore.plugin.ui.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveMenuPromptTest {

    @Test
    @DisplayName("submit invokes submit callback")
    void submit_invokesSubmitCallback() {
        AtomicReference<String> received = new AtomicReference<>();
        ActiveMenuPrompt prompt = ActiveMenuPrompt.create(1L, 42, received::set, () -> {});

        prompt.submit("hello");

        assertThat(received.get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("cancel invokes cancel callback")
    void cancel_invokesCancelCallback() {
        AtomicBoolean ran = new AtomicBoolean(false);
        ActiveMenuPrompt prompt = ActiveMenuPrompt.create(1L, 42, s -> {}, () -> ran.set(true));

        prompt.cancel();

        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("submit tolerates null callback")
    void submit_toleratesNullCallback() {
        ActiveMenuPrompt prompt = ActiveMenuPrompt.create(1L, 42, null, null);

        prompt.submit("hello"); // should not throw
    }

    @Test
    @DisplayName("cancel tolerates null callback")
    void cancel_toleratesNullCallback() {
        ActiveMenuPrompt prompt = ActiveMenuPrompt.create(1L, 42, null, null);

        prompt.cancel(); // should not throw
    }
}
