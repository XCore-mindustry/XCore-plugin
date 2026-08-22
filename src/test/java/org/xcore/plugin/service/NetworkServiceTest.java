package org.xcore.plugin.service;

import arc.func.Cons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkServiceTest {

    @Test
    @DisplayName("reloadBackend reconnects and replays registered hooks")
    void reloadBackend_reconnectsAndReplaysRegisteredHooks() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        Runnable hook = mock(Runnable.class);
        NetworkService service = new NetworkService(backend);
        service.registerReconnectHook(hook);

        boolean reloaded = service.reloadBackend();

        assertThat(reloaded).isTrue();
        var inOrder = inOrder(backend, hook);
        inOrder.verify(backend).disconnect();
        inOrder.verify(backend).connect();
        inOrder.verify(hook).run();
        verify(hook).run();
    }

    @Test
    @DisplayName("reloadBackend disconnects and reports failure when reconnect fails")
    void reloadBackend_disconnectsAndReportsFailureWhenReconnectFails() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        Runnable hook = mock(Runnable.class);
        NetworkService service = new NetworkService(backend);
        service.registerReconnectHook(hook);
        doThrow(new IllegalStateException("boom")).when(backend).connect();

        boolean reloaded = service.reloadBackend();

        assertThat(reloaded).isFalse();
        verify(backend, times(2)).disconnect();
        verify(backend).connect();
        verify(hook, never()).run();
    }

    @Test
    @DisplayName("reloadBackend disconnects after reconnect hook failure")
    void reloadBackend_disconnectsAfterReconnectHookFailure() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        NetworkService service = new NetworkService(backend);
        service.registerReconnectHook(() -> {
            throw new IllegalStateException("hook failed");
        });

        boolean reloaded = service.reloadBackend();

        assertThat(reloaded).isFalse();
        verify(backend, times(2)).disconnect();
        verify(backend).connect();
    }

    @Test
    @DisplayName("subscribe returns a no-op subscription when backend is unavailable")
    void subscribe_returnsNoopWhenBackendUnavailable() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.ensureConnected()).thenReturn(false);
        NetworkService service = new NetworkService(backend);

        RedisNetworkBackend.Subscription<String> subscription = service.subscribe(String.class, s -> {});

        assertThat(subscription.unsubscribe()).isFalse();
        verify(backend, never()).subscribe(any(), any());
    }

    @Test
    @DisplayName("request returns a no-op subscription when backend is unavailable")
    void request_returnsNoopWhenBackendUnavailable() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.ensureConnected()).thenReturn(false);
        NetworkService service = new NetworkService(backend);

        RedisNetworkBackend.RequestSubscription<String> subscription =
                service.request(new Object(), r -> {}, () -> {});

        subscription.cancel();
        verify(backend, never()).request(any(), any(), any());
    }

    @Test
    @DisplayName("subscribe and request delegate to the backend when it is available")
    void subscribeAndRequest_delegateWhenBackendAvailable() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.ensureConnected()).thenReturn(true);
        NetworkService service = new NetworkService(backend);
        Cons<String> listener = s -> {};
        RedisNetworkBackend.Subscription<String> delegate =
                new RedisNetworkBackend.Subscription<>() {
                    @Override
                    public void call(String object) {}

                    @Override
                    public boolean unsubscribe() {
                        return true;
                    }
                };
        when(backend.subscribe(String.class, listener)).thenReturn(delegate);
        RedisNetworkBackend.RequestSubscription<String> requestDelegate =
                new RedisNetworkBackend.RequestSubscription<>() {
                    @Override
                    public void cancel() {}
                };
        doReturn(requestDelegate).when(backend).request(any(), any(), any());

        assertThat(service.subscribe(String.class, listener)).isSameAs(delegate);
        assertThat(service.request(new Object(), r -> {}, () -> {})).isSameAs(requestDelegate);
    }
}
