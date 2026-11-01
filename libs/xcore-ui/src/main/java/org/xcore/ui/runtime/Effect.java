package org.xcore.ui.runtime;

/**
 * Work a reducer asks for after its model is in place, such as a load whose result comes back as
 * an event. Returned with {@link UpdateResult#effect}; the session runs it once the update has been
 * applied and sent, so the effect never sees the model it replaced.
 *
 * <pre>{@code
 * case Refresh() -> UpdateResult.rerender(model.loading())
 *         .effect("load", (Dispatcher<TopEvent> d) -> async.supply(this::fetch)
 *                 .thenMain((data, error) -> d.dispatch(new Loaded(data))));
 * }</pre>
 *
 * @param <E> the event type of the controller
 */
@FunctionalInterface
public interface Effect<E> {

    void run(Dispatcher<E> dispatcher);
}
