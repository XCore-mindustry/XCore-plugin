package org.xcore.plugin.integration.profile;

import arc.util.Log;
import jakarta.inject.Singleton;
import org.xcore.plugin.model.PlayerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** The profile sections registered by this server's modes. */
@Singleton
public class ProfileSectionRegistry {

    private final CopyOnWriteArrayList<RegisteredProvider> providers = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private long nextOrder;

    public Registration register(ProfileSectionProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = Objects.requireNonNull(provider.id(), "provider.id()");
        if (id.isBlank()) {
            throw new IllegalArgumentException("provider.id() must not be blank");
        }

        RegisteredProvider registered;
        synchronized (lock) {
            for (RegisteredProvider existing : providers) {
                if (existing.id().equals(id)) {
                    throw new IllegalArgumentException("A profile section is already registered for id: " + id);
                }
            }
            registered = new RegisteredProvider(provider, id, provider.priority(), nextOrder++);
            providers.add(registered);
            providers.sort(Comparator.comparingInt(RegisteredProvider::priority).reversed()
                    .thenComparingLong(RegisteredProvider::order));
        }
        return new ProviderRegistration(registered);
    }

    /**
     * Loads every section that has something to show about {@code target}, in display
     * order. Blocking. A section that fails is left out and logged.
     */
    public List<ProfileSectionView> load(PlayerData target) {
        List<ProfileSectionView> views = new ArrayList<>();
        if (target == null) {
            return views;
        }
        for (RegisteredProvider registered : providers) {
            try {
                registered.provider().load(target).ifPresent(views::add);
            } catch (Exception e) {
                // One mode's section must not take the whole profile down.
                Log.warn("Profile section @ failed to load for @: @", registered.id(), target.uuid, e.getMessage());
            }
        }
        return List.copyOf(views);
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

    private record RegisteredProvider(ProfileSectionProvider provider, String id, int priority, long order) {
    }
}
