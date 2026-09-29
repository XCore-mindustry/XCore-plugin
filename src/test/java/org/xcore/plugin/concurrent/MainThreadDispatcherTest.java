package org.xcore.plugin.concurrent;

import arc.Application;
import arc.Core;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Arc's task queue runs posted work without a try/catch, so an exception escaping a
 * marshalled task propagates out of the main loop and takes the headless server with it.
 * The dispatcher is the only chokepoint every one of those tasks passes through, which is
 * why the guard lives there rather than at each call site.
 */
class MainThreadDispatcherTest {

    @Test
    @DisplayName("an exception from a marshalled task does not escape the dispatcher")
    void taskException_isContained() {
        // Boot Arc so the task takes the post() path rather than the inline test path.
        Application previous = Core.app;
        Core.app = application();

        try {
            Runnable explodes = () -> {
                throw new IllegalStateException("boom from a redis listener");
            };

            assertThatCode(() -> MainThreadDispatcher.mindustry().execute(explodes))
                    .as("the caller must not see the task's failure")
                    .doesNotThrowAnyException();
        } finally {
            Core.app = previous;
        }
    }

    @Test
    @DisplayName("an Error from a marshalled task is contained too, not just an Exception")
    void taskError_isContained() {
        Application previous = Core.app;
        Core.app = application();

        try {
            assertThatCode(() -> MainThreadDispatcher.mindustry().execute(() -> {
                throw new NoClassDefFoundError("boom");
            })).doesNotThrowAnyException();
        } finally {
            Core.app = previous;
        }
    }

    @Test
    @DisplayName("a null task is rejected before it reaches the queue")
    void nullTask_isRejected() {
        assertThatThrownBy(() -> MainThreadDispatcher.mindustry().execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("with no Arc booted the task runs inline, which is what unit tests rely on")
    void withoutArc_runsInline() {
        Application previous = Core.app;
        Core.app = null;

        try {
            boolean[] ran = {false};
            MainThreadDispatcher.mindustry().execute(() -> ran[0] = true);

            assertThat(ran[0]).isTrue();
        } finally {
            Core.app = previous;
        }
    }

    /**
     * Arc's Application is an interface, so a mock is the only cheap stand-in. Running the
     * posted task inline is what the real task queue does for our purposes: the assertion
     * is about whether the exception escapes, not about queue ordering.
     */
    private static Application application() {
        Application application = mock(Application.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(application).post(any(Runnable.class));
        return application;
    }
}
