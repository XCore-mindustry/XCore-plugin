package org.xcore.plugin.integration;

import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.model.PlayerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class PlayerDisplayRegistry {
    private final CopyOnWriteArrayList<RegisteredProvider> providers = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private long nextOrder;

    public Registration register(PlayerDisplayProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = Objects.requireNonNull(provider.id(), "provider.id()");
        if (id.isBlank()) {
            throw new IllegalArgumentException("provider.id() must not be blank");
        }

        RegisteredProvider registered;
        synchronized (lock) {
            for (RegisteredProvider existing : providers) {
                if (existing.id.equals(id)) {
                    throw new IllegalArgumentException("A display provider is already registered for id: " + id);
                }
            }
            registered = new RegisteredProvider(provider, id, provider.priority(), nextOrder++);
            providers.add(registered);
            providers.sort(Comparator.comparingInt(RegisteredProvider::priority).reversed()
                    .thenComparingLong(RegisteredProvider::order));
        }

        return new ProviderRegistration(registered);
    }

    public List<String> resolve(PlayerData data, Player player) {
        List<String> tags = new ArrayList<>();
        for (RegisteredProvider registered : providers) {
            try {
                String tag = registered.provider.resolve(data, player);
                if (tag != null && !tag.isBlank()) {
                    tags.add(tag);
                }
            } catch (Exception ignored) {
                // An external provider must not prevent the player's name from rendering.
            }
        }
        return List.copyOf(tags);
    }

    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private final class ProviderRegistration implements Registration {
        private final RegisteredProvider registered;
        private boolean closed;

        private ProviderRegistration(RegisteredProvider registered) {
            this.registered = registered;
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (closed) return;
                closed = true;
                providers.remove(registered);
            }
        }
    }

    private record RegisteredProvider(PlayerDisplayProvider provider, String id, int priority, long order) {
    }
}
