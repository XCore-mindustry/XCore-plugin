package org.xcore.plugin.event;

import arc.func.Cons;
import arc.util.Log;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import mindustry.game.EventType;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.startup.ShutdownOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * Listeners, timers and cleanups that belong to the round being played and are dropped
 * together when the world resets.
 *
 * <p>The boundary is {@link EventType.ResetEvent}: it fires on every map change, game over
 * reload, save load and {@code stop}, before the next world exists. Work registered here
 * therefore cannot leak into the next round - a delayed map switch does not fire against a
 * world that has already changed, and a vote does not outlive the map it was about.
 *
 * <p>There is deliberately no trigger variant: Arc cannot remove an enum trigger callback,
 * so a per-round trigger would leave one dead wrapper on the update loop for every round
 * played. Keep triggers in a bean-owned {@link EventSubscriptions}.
 */
@Singleton
public class RoundScope {
    private static final String OWNER = "RoundScope";

    private final EventSubscriptions lifecycle = new EventSubscriptions();
    private final List<Runnable> endActions = new ArrayList<>();
    private EventSubscriptions events = new EventSubscriptions();
    private ScheduledTasks tasks = new ScheduledTasks(OWNER);
    private boolean closed;

    @PostConstruct
    public void init() {
        lifecycle.on(EventType.ResetEvent.class, event -> endRound());
    }

    /** Listens for {@code type} until the current round ends. */
    public synchronized <T> void on(Class<T> type, Cons<T> listener) {
        GameThread.require("RoundScope.on");
        if (type == EventType.ResetEvent.class) {
            // The round's listeners are removed while Arc is still dispatching this event.
            throw new IllegalArgumentException("Use onEnd to react to the end of the round");
        }
        if (closed) return;
        events.on(type, listener);
    }

    /** Runs {@code action} once after {@code delaySeconds}, unless the round ends first. */
    public synchronized ScheduledTasks.Handle after(float delaySeconds, Runnable action) {
        GameThread.require("RoundScope.after");
        return tasks.after(delaySeconds, action);
    }

    /** Runs {@code action} every {@code intervalSeconds} until the round ends. */
    public synchronized ScheduledTasks.Handle every(float delaySeconds, float intervalSeconds, Runnable action) {
        GameThread.require("RoundScope.every");
        return tasks.every(delaySeconds, intervalSeconds, action);
    }

    /**
     * Runs {@code action} once when the current round ends, after its listeners and timers
     * are gone. Entities are already cleared at that point and the next world is not loaded.
     */
    public synchronized void onEnd(Runnable action) {
        GameThread.require("RoundScope.onEnd");
        if (closed) return;
        endActions.add(action);
    }

    /** Ends the current round and opens the next one. Called on every world reset. */
    public void endRound() {
        GameThread.require("RoundScope.endRound");
        List<Runnable> ending;
        synchronized (this) {
            if (closed) return;
            ending = List.copyOf(endActions);
            endActions.clear();
            events.close();
            tasks.close();
            events = new EventSubscriptions();
            tasks = new ScheduledTasks(OWNER);
        }
        // Outside the lock, and after the swap: an end action may register work for the next round.
        for (Runnable action : ending) {
            try {
                action.run();
            } catch (Throwable error) {
                Log.err("[" + OWNER + "] Round end action failed", error);
            }
        }
    }

    /**
     * End actions are not run here: shutdown is not the end of a round, and it can close
     * the scope off the game thread.
     */
    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public synchronized void close() {
        if (closed) return;
        closed = true;
        endActions.clear();
        lifecycle.close();
        events.close();
        tasks.close();
    }
}
