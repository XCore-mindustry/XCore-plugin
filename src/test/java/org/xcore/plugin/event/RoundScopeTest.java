package org.xcore.plugin.event;

import arc.Events;
import arc.util.Timer;
import mindustry.game.EventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;

class RoundScopeTest {
    private record Probe() { }

    private RoundScope round;

    @BeforeEach
    void setUp() {
        round = new RoundScope();
        round.init();
    }

    @AfterEach
    void tearDown() {
        round.close();
    }

    @Test
    void listenerIsDroppedWhenTheWorldResets() {
        var calls = new AtomicInteger();
        round.on(Probe.class, event -> calls.incrementAndGet());
        Events.fire(new Probe());

        Events.fire(new EventType.ResetEvent());
        Events.fire(new Probe());

        assertThat(calls).hasValue(1);
    }

    @Test
    void nextRoundAcceptsNewListeners() {
        var calls = new AtomicInteger();
        Events.fire(new EventType.ResetEvent());
        round.on(Probe.class, event -> calls.incrementAndGet());

        Events.fire(new Probe());

        assertThat(calls).hasValue(1);
    }

    @Test
    void delayedTaskDoesNotFireIntoTheNextRound() {
        var task = mock(Timer.Task.class);
        var scheduled = new ArrayList<Runnable>();
        var calls = new AtomicInteger();
        try (var timer = mockStatic(Timer.class)) {
            timer.when(() -> Timer.schedule(any(Runnable.class), anyFloat())).thenAnswer(invocation -> {
                scheduled.add(invocation.getArgument(0));
                return task;
            });
            round.after(10f, calls::incrementAndGet);

            Events.fire(new EventType.ResetEvent());
            scheduled.getFirst().run();

            verify(task).cancel();
            assertThat(calls).hasValue(0);
        }
    }

    @Test
    void endActionRunsOnceForTheRoundItWasRegisteredIn() {
        var calls = new AtomicInteger();
        round.onEnd(calls::incrementAndGet);

        Events.fire(new EventType.ResetEvent());
        Events.fire(new EventType.ResetEvent());

        assertThat(calls).hasValue(1);
    }

    @Test
    void failingEndActionDoesNotStopTheOthers() {
        List<String> order = new ArrayList<>();
        round.onEnd(() -> {
            throw new IllegalStateException("boom");
        });
        round.onEnd(() -> order.add("second"));

        Events.fire(new EventType.ResetEvent());

        assertThat(order).containsExactly("second");
    }

    @Test
    void endActionMayRegisterWorkForTheNextRound() {
        var calls = new AtomicInteger();
        round.onEnd(() -> round.on(Probe.class, event -> calls.incrementAndGet()));

        Events.fire(new EventType.ResetEvent());
        Events.fire(new Probe());

        assertThat(calls).hasValue(1);
    }

    @Test
    void resetEventCannotBeSubscribedThroughTheRound() {
        assertThatThrownBy(() -> round.on(EventType.ResetEvent.class, event -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shutdownDoesNotRunEndActions() {
        var calls = new AtomicInteger();
        round.onEnd(calls::incrementAndGet);

        round.close();
        Events.fire(new EventType.ResetEvent());

        assertThat(calls).hasValue(0);
    }
}
