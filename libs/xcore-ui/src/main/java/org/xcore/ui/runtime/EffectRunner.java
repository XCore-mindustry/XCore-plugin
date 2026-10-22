package org.xcore.ui.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Runs {@link Effect effects} and hands what they dispatch to a sink, the way {@link UiSession}
 * does: a keyed effect supersedes the earlier ones of its key, and nothing is delivered once the
 * owner is no longer live. Usable on its own to drive a controller's effects in a test.
 *
 * @param <E> the event type of the controller
 */
public final class EffectRunner<E> {

    private final Consumer<? super E> sink;
    private final BooleanSupplier live;
    private final Map<String, Long> generations = new ConcurrentHashMap<>();

    /**
     * @param sink where a delivered event goes, such as the session's reducer
     * @param live whether the owner still takes events
     */
    public EffectRunner(Consumer<? super E> sink, BooleanSupplier live) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.live = Objects.requireNonNull(live, "live");
    }

    /** Runs the effects of an update, in order. */
    @SuppressWarnings("unchecked")
    public void run(List<UpdateResult.Command> effects) {
        for (UpdateResult.Command command : effects) {
            run(command.key(), (Effect<E>) command.effect());
        }
    }

    /** Runs one effect; a {@code key} of {@code null} orders it against no other. */
    public void run(String key, Effect<E> effect) {
        effect.run(dispatcher(key));
    }

    private Dispatcher<E> dispatcher(String key) {
        Long generation = key == null ? null : generations.merge(key, 1L, Long::sum);
        return new Dispatcher<>() {
            @Override
            public boolean dispatch(E event) {
                if (!isCurrent()) {
                    return false;
                }
                sink.accept(event);
                return true;
            }

            @Override
            public boolean isCurrent() {
                return live.getAsBoolean() && (key == null || generation.equals(generations.get(key)));
            }
        };
    }
}
