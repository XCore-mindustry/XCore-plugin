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
}
