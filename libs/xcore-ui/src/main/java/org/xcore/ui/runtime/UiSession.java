package org.xcore.ui.runtime;

import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.VSlot;
import org.xcore.ui.VNodes;
import mindustry.ui.builder.UiBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player dialog session driving one {@link UiController}.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #open()} renders the initial model and transmits the full dialog;</li>
 *   <li>{@link #handle(MenuResult)} decodes the event, runs the reducer and
 *       delivers either slot patches ({@code menuBuilderUpdate}) or a full
 *       re-render, honoring {@link SlotPruner};</li>
 *   <li>{@link #close()} hides the dialog and drops session state.</li>
 * </ol>
 *
 * <p>Transmission is delegated to a {@link DeliveryGateway} so the runtime stays
 * testable without a live server.
 */
public final class UiSession<Model, Event> {

    private static final java.util.concurrent.atomic.AtomicLong TOKEN_GENERATOR =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() << 16);

    private final UiController<Model, Event> controller;
    private final ControllerContext ctx;
    private final DeliveryGateway gateway;
    private volatile VNodeCompiler compiler;
    private volatile long token;

    private volatile Model model;

    private UiSession(UiController<Model, Event> controller, ControllerContext ctx,
                      DeliveryGateway gateway, VNodeCompiler compiler, long token, Model model) {
        this.controller = controller;
        this.ctx = ctx;
        this.gateway = gateway;
        this.compiler = compiler;
        this.token = token;
        this.model = model;
    }

    /**
     * Starts a new UI session using {@link LocalizerResolver#getDefault()}.
     */
    public static <Model, Event> UiSession<Model, Event> start(
            UiController<Model, Event> controller,
            Model initialModel,
            ControllerContext ctx,
            DeliveryGateway gateway) {
        return start(controller, initialModel, ctx, gateway, LocalizerResolver.getDefault());
    }

    public static <Model, Event> UiSession<Model, Event> start(
            UiController<Model, Event> controller,
            Model initialModel,
            ControllerContext ctx,
            DeliveryGateway gateway,
            LocalizerResolver resolver) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(gateway, "gateway");
        VNodeCompiler compiler = new VNodeCompiler(resolver != null ? resolver : LocalizerResolver.getDefault());
        long token = TOKEN_GENERATOR.incrementAndGet();
        return new UiSession<>(controller, ctx, gateway, compiler, token, initialModel);
    }

    /**
     * Updates the localization resolver for this session and fully re-renders the dialog.
     * Useful when a player changes their language while a dialog is active.
     */
    public void updateResolver(LocalizerResolver resolver) {
        this.compiler = new VNodeCompiler(resolver != null ? resolver : LocalizerResolver.getDefault());
        open();
    }

    public long token() {
        return token;
    }

    public Model model() {
        return model;
    }

    /** Renders the current model and shows the dialog with a fresh window token. */
    public void open() {
        this.token = TOKEN_GENERATOR.incrementAndGet();
        VNode tree = controller.render(model);
        UiBuilder.NodeBuilder<?> compiled = compiler.compile(tree);
        gateway.show(ctx.playerId(), token, compiled);
    }

    /** Dispatches a domain event directly into the reducer (e.g. from async or push notifications). */
    public void dispatch(Event event) {
        if (event == null) {
            return;
        }

        UpdateResult<Model> update = controller.update(model, event, ctx);
        model = update.model();

        if (update.close()) {
            gateway.hide(ctx.playerId());
            return;
        }

        if (update.isNoop()) {
            return;
        }

        if (update.fullRerender()) {
            open();
            return;
        }

        patchSlots(update.dirtySlots());
    }

    /** Feeds a client result into the reducer and delivers render directives. */
    public void handle(mindustry.ui.builder.MenuResult result) {
        Objects.requireNonNull(result, "result");
        if (result.token != 0 && result.token != this.token) {
            // Drop stale results from an older window generation (replacement cancels, late clicks)
            return;
        }
        Event event = controller.parseEvent(result);
        if (event != null) {
            dispatch(event);
        }
    }

    private void patchSlots(List<SlotKey<?>> dirty) {
        List<SlotKey<Object>> minimal = SlotPruner.prune(dirty.stream()
                .map(s -> new SlotKey<Object>(s.path()))
                .toList());
        if (minimal.isEmpty()) {
            return;
        }
        VNode tree = controller.render(model);
        for (SlotKey<?> key : minimal) {
            VSlot slot = VNodes.findSlot(tree, key.path());
            if (slot == null) {
                continue; // slot not in current view; nothing to patch
            }
            UiBuilder.NodeBuilder<?> body = compiler.compileSlotBody(slot);
            gateway.update(ctx.playerId(), token, key.path(), body);
        }
    }

    /** Closes the dialog. */
    public void close() {
        gateway.hide(ctx.playerId());
    }

    /** Transport abstraction; production binds to {@code Call.menuBuilder*}. */
    public interface DeliveryGateway {
        void show(String playerId, long token, UiBuilder.NodeBuilder<?> ui);

        void update(String playerId, long token, String elementId, UiBuilder.NodeBuilder<?> ui);

        void hide(String playerId);
    }

    /** Session registry mapping player -> active session, for host dispatch. */
    public static final class Registry {
        private final Map<String, UiSession<?, ?>> sessions = new ConcurrentHashMap<>();

        public void register(String playerId, UiSession<?, ?> session) {
            sessions.put(playerId, session);
        }

        @SuppressWarnings("unchecked")
        public <Model, Event> UiSession<Model, Event> get(String playerId) {
            return (UiSession<Model, Event>) sessions.get(playerId);
        }

        public void remove(String playerId) {
            sessions.remove(playerId);
        }
    }
}
