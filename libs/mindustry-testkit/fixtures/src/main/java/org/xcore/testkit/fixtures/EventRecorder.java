package org.xcore.testkit.fixtures;

import arc.Events;
import arc.func.Cons;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Records every {@code Events.fire} of one event type until {@link #close()}, so a test can assert
 * what a plugin announced without registering and removing its own listener.
 *
 * <pre>{@code
 * try (var gameOvers = EventRecorder.record(EventType.GameOverEvent.class)) {
 *     service.finishRound();
 *     assertThat(gameOvers.count()).isEqualTo(1);
 *     assertThat(gameOvers.last().winner).isEqualTo(Team.sharded);
 * }
 * }</pre>
 *
 * <p>{@link HeadlessWorld#recordEvents} makes one that lives as long as the world.
 */
public final class EventRecorder<T> implements AutoCloseable {
    private final Class<T> type;
    private final List<T> events = new ArrayList<>();
    private final Cons<T> listener = this::add;

    private EventRecorder(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
        Events.on(type, listener);
    }

    /** Starts recording {@code type}. */
    public static <T> EventRecorder<T> record(Class<T> type) {
        return new EventRecorder<>(type);
    }

    public Class<T> type() {
        return type;
    }

    /** Recorded events, oldest first. */
    public synchronized List<T> events() {
        return List.copyOf(events);
    }

    public synchronized int count() {
        return events.size();
    }

    public synchronized boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * The latest recorded event.
     *
     * @throws IllegalStateException when none was fired
     */
    public synchronized T last() {
        if (events.isEmpty()) throw new IllegalStateException("No " + type.getSimpleName() + " was fired");
        return events.get(events.size() - 1);
    }

    /** Forgets what was recorded so far; recording goes on. */
    public synchronized void clear() {
        events.clear();
    }

    /** Stops recording. What was recorded stays readable. */
    @Override
    public void close() {
        Events.remove(type, listener);
    }

    private synchronized void add(T event) {
        events.add(event);
    }
}
