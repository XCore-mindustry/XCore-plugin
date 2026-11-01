package org.xcore.plugin.integration.top;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class TopCategoryRegistry {

    private final CopyOnWriteArrayList<RegisteredProvider> providers = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private long nextOrder;
    private volatile String defaultCategoryId;

    public Registration register(TopCategoryProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = Objects.requireNonNull(provider.id(), "provider.id()");
        if (id.isBlank()) {
            throw new IllegalArgumentException("provider.id() must not be blank");
        }

        RegisteredProvider registered;
        synchronized (lock) {
            for (RegisteredProvider existing : providers) {
                if (existing.id().equalsIgnoreCase(id)) {
                    throw new IllegalArgumentException("A top category provider is already registered for id: " + id);
                }
            }
            registered = new RegisteredProvider(provider, id, provider.priority(), nextOrder++);
            providers.add(registered);
            providers.sort(Comparator.comparingInt(RegisteredProvider::priority).reversed()
                    .thenComparingLong(RegisteredProvider::order));
        }

        return new ProviderRegistration(registered);
    }

    public Optional<Registration> registerIfAbsent(TopCategoryProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = Objects.requireNonNull(provider.id(), "provider.id()");
        if (id.isBlank()) {
            throw new IllegalArgumentException("provider.id() must not be blank");
        }

        synchronized (lock) {
            for (RegisteredProvider existing : providers) {
                if (existing.id().equalsIgnoreCase(id)) {
                    return Optional.empty();
                }
            }
            return Optional.of(register(provider));
        }
    }

    public Optional<TopCategoryProvider> resolve(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        for (RegisteredProvider registered : providers) {
            if (registered.id().equalsIgnoreCase(id)) {
                return Optional.of(registered.provider());
            }
        }
        return Optional.empty();
    }

    public List<TopCategoryProvider> all() {
        List<TopCategoryProvider> list = new ArrayList<>(providers.size());
        for (RegisteredProvider registered : providers) {
            list.add(registered.provider());
        }
        return List.copyOf(list);
    }

    public void setDefaultCategory(String id) {
        this.defaultCategoryId = (id == null || id.isBlank()) ? null : id.trim();
    }

    public void clearDefaultCategory() {
        this.defaultCategoryId = null;
    }

    public Optional<String> defaultCategoryId() {
        return Optional.ofNullable(defaultCategoryId);
    }

    public Optional<TopCategoryProvider> resolveDefault(String fallbackId) {
        String configuredDefault = defaultCategoryId;
        if (configuredDefault != null) {
            Optional<TopCategoryProvider> provider = resolve(configuredDefault);
            if (provider.isPresent()) {
                return provider;
            }
        }
        if (fallbackId != null && !fallbackId.isBlank()) {
            Optional<TopCategoryProvider> provider = resolve(fallbackId);
            if (provider.isPresent()) {
                return provider;
            }
        }
        if (!providers.isEmpty()) {
            return Optional.of(providers.get(0).provider());
        }
        return Optional.empty();
    }

    public interface Registration extends AutoCloseable {
        void unregister();
        boolean isRegistered();

        @Override
        default void close() {
            unregister();
        }
    }

    private final class ProviderRegistration implements Registration {
        private final RegisteredProvider registered;
        private boolean closed;

        private ProviderRegistration(RegisteredProvider registered) {
            this.registered = registered;
        }

        @Override
        public void unregister() {
            synchronized (lock) {
                if (closed) return;
                closed = true;
                providers.remove(registered);
            }
        }

        @Override
        public boolean isRegistered() {
            synchronized (lock) {
                return !closed;
            }
        }
    }

    private record RegisteredProvider(TopCategoryProvider provider, String id, int priority, long order) {
    }
}
