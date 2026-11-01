package org.xcore.plugin.startup;

import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PluginScopesTest {
    @Test
    void closesChildrenInReverseOrderAndOnlyOnce() {
        var scopes = new PluginScopes();
        var first = mock(BeanScope.class);
        var second = mock(BeanScope.class);
        var registration = scopes.register(first);
        scopes.register(second);

        scopes.shutdown();
        scopes.shutdown();
        registration.close();

        var order = inOrder(second, first);
        order.verify(second).close();
        order.verify(first).close();
        verifyNoMoreInteractions(first, second);
    }

    @Test
    void explicitUnloadUnregistersTheScope() {
        var scopes = new PluginScopes();
        var child = mock(BeanScope.class);
        scopes.register(child).close();

        scopes.shutdown();

        verify(child).close();
        verifyNoMoreInteractions(child);
    }

    @Test
    void failureClosingOneChildDoesNotSkipOtherChildren() {
        var scopes = new PluginScopes();
        var first = mock(BeanScope.class);
        var second = mock(BeanScope.class);
        scopes.register(first);
        scopes.register(second);
        doThrow(new IllegalStateException("close failed")).when(second).close();

        scopes.shutdown();

        verify(first).close();
    }

    @Test
    void lateRegistrationClosesTheRejectedScope() {
        var scopes = new PluginScopes();
        scopes.shutdown();
        var child = mock(BeanScope.class);

        assertThatThrownBy(() -> scopes.register(child)).isInstanceOf(IllegalStateException.class);

        verify(child).close();
    }
}
