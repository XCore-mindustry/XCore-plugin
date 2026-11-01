package org.xcore.plugin.event;

import arc.func.Cons;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.RedisNetworkBackend.Subscription;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransportSubscriptionsTest {
    @Test
    @SuppressWarnings("unchecked")
    void closingUnsubscribesAndDisablesAlreadyQueuedCallbacks() {
        var network = mock(NetworkService.class);
        Subscription<String> handle = mock(Subscription.class);
        when(network.subscribe(eq(String.class), any())).thenReturn(handle);
        var calls = new AtomicInteger();
        var subscriptions = new TransportSubscriptions(network);
        subscriptions.subscribe(String.class, event -> calls.incrementAndGet());
        ArgumentCaptor<Cons<String>> callback = ArgumentCaptor.forClass(Cons.class);
        verify(network).subscribe(eq(String.class), callback.capture());
        callback.getValue().get("before");

        subscriptions.close();
        subscriptions.close();
        callback.getValue().get("queued");

        assertThat(calls).hasValue(1);
        verify(handle).unsubscribe();
    }
}
