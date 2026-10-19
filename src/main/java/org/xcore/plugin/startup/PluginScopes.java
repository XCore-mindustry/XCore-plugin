package org.xcore.plugin.startup;

import io.avaje.inject.BeanScope;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owns child scopes so plugin cleanup finishes before the root closes shared resources. */
@Singleton
public final class PluginScopes {
    private final List<Registration> children = new ArrayList<>();
    private boolean closed;

    public synchronized Registration register(BeanScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (closed) {
            scope.close();
            throw new IllegalStateException("XCore is shutting down; child scopes cannot be registered");
        }
        var registration = new Registration(scope);
        children.add(registration);
        return registration;
    }

    @PreDestroy(priority = ShutdownOrder.CHILD_SCOPES)
    public synchronized void shutdown() {
        if (closed) return;
        closed = true;
        // Later plugins can depend on earlier plugins as well as the root.
        for (int i = children.size() - 1; i >= 0; i--) {
            try {
                children.get(i).closeScope();
            } catch (Throwable error) {
                PLog.err("Error closing child plugin scope", error);
            }
        }
        children.clear();
    }

    public final class Registration implements AutoCloseable {
        private BeanScope scope;

        private Registration(BeanScope scope) {
            this.scope = scope;
        }

        @Override
        public void close() {
            synchronized (PluginScopes.this) {
                try {
                    closeScope();
                } finally {
                    children.remove(this);
                }
            }
        }

        private void closeScope() {
            var closing = scope;
            scope = null;
            if (closing != null) closing.close();
        }
    }
}
