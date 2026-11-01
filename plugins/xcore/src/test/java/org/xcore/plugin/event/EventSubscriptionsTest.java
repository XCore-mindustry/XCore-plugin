package org.xcore.plugin.event;

import arc.Core;
import arc.Events;
import arc.Application;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.testkit.core.DeterministicQueue;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventSubscriptionsTest {
    private record Probe() { }

    @Test
    void closingSubscriptionsRemovesOnlyTheirOwnListeners() {
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        try (var a = new EventSubscriptions(); var b = new EventSubscriptions()) {
            a.on(Probe.class, event -> first.incrementAndGet());
            b.on(Probe.class, event -> second.incrementAndGet());
            Events.fire(new Probe());
            a.close();
            Events.fire(new Probe());

            assertThat(first).hasValue(1);
            assertThat(second).hasValue(2);
        }
    }

    private enum Trigger { update }

    @Test
    void closedTriggerDoesNotCallItsCallback() {
        var calls = new AtomicInteger();
        var subscriptions = new EventSubscriptions();
        subscriptions.run(Trigger.update, calls::incrementAndGet);
        Events.fire(Trigger.update);
        subscriptions.close();
        Events.fire(Trigger.update);
        assertThat(calls).hasValue(1);
    }

    @Test
    void offThreadCloseDisablesCallbacksBeforeGameThreadRemovesThem() throws Exception {
        var previousApp = Core.app;
        var queue = new DeterministicQueue();
        var app = mock(Application.class);
        doAnswer(invocation -> {
            queue.post(invocation.getArgument(0));
            return null;
        }).when(app).post(any(Runnable.class));
        Core.app = app;
        GameThread.setGameThreadOverride(Thread.currentThread());
        var calls = new AtomicInteger();
        var subscriptions = new EventSubscriptions();
        try {
            subscriptions.on(Probe.class, event -> calls.incrementAndGet());
            var thread = Thread.ofPlatform().start(subscriptions::close);
            thread.join();
            Events.fire(new Probe());
            assertThat(calls).hasValue(0);
            verify(app).post(any(Runnable.class));
            queue.runTurn();
            Events.fire(new Probe());
            assertThat(calls).hasValue(0);
        } finally {
            queue.runTurn();
            subscriptions.close();
            GameThread.setGameThreadOverride(null);
            Core.app = previousApp;
        }
    }
}
