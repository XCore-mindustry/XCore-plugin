package org.xcore.testkit.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DeterministicQueueTest {
    @Test
    void taskPostedDuringTurnWaitsForNextTurn() {
        var queue = new DeterministicQueue();
        var delivered = new ArrayList<String>();
        queue.post(() -> {
            delivered.add("A");
            queue.post(() -> delivered.add("B"));
        });

        queue.runTurn();
        assertEquals(List.of("A"), delivered);
        queue.runTurn();
        assertEquals(List.of("A", "B"), delivered);
        assertFalse(queue.runNext());
    }

    @Test
    void enqueueIsDeferredAndDeliveryIsFifo() {
        var queue = new DeterministicQueue();
        var delivered = new ArrayList<String>();
        queue.post(() -> delivered.add("first"));
        queue.post(() -> delivered.add("second"));

        assertEquals(List.of(), delivered);
        assertTrue(queue.runNext());
        assertEquals(List.of("first"), delivered);
        assertTrue(queue.runNext());
        assertEquals(List.of("first", "second"), delivered);
        assertFalse(queue.runNext());
    }

    @Test
    void runUntilIdleRunsChainedTurnsAndCountsThem() {
        var queue = new DeterministicQueue();
        var delivered = new ArrayList<String>();
        java.util.concurrent.Executor executor = queue;
        executor.execute(() -> {
            delivered.add("A");
            queue.post(() -> delivered.add("B"));
        });

        assertEquals(1, queue.size());
        assertEquals(2, queue.runUntilIdle());
        assertEquals(List.of("A", "B"), delivered);
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.runUntilIdle());
    }

    @Test
    void runUntilIdleFailsOnTaskThatRepostsItself() {
        var queue = new DeterministicQueue();
        Runnable[] forever = new Runnable[1];
        forever[0] = () -> queue.post(forever[0]);
        queue.post(forever[0]);

        var error = assertThrows(IllegalStateException.class, () -> queue.runUntilIdle(5));
        assertTrue(error.getMessage().contains("after 5 turns"), error.getMessage());
    }
}
