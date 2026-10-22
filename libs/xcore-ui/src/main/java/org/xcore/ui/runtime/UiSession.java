package org.xcore.ui.runtime;

import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.UiPackets;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.VSlot;
import org.xcore.ui.VNodes;
import mindustry.ui.builder.UiBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player dialog session driving one {@link UiController}.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #open()} renders the initial model and transmits the full dialog;</li>
 *   <li>{@link #handle(MenuResult)} decodes the event, runs the reducer and
 *       delivers either slot patches ({@code menuBuilderUpdate}) or a full
 *       re-render, honoring {@link SlotPruner}, then runs the update's effects;</li>
 *   <li>{@link #close()} hides the dialog; the player closing it ends the session
 *       too, and the controller hears of it through {@link UiController#closed}.</li>
 * </ol>
 *
 * <p>A session that has ended, or has been {@link #detach() detached} because another dialog
 * took its place, sends nothing more: events, effects and renders addressed to it are dropped.
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
    private final EffectRunner<Event> effects = new EffectRunner<>(this::dispatch, this::isActive);
    private volatile VNodeCompiler compiler;
    private volatile long token;
    private volatile boolean active = true;
    private volatile boolean closeReported;

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

    /** Whether the session still drives a dialog: it has not ended nor been detached. */
    public boolean isActive() {
        return active;
    }

    /**
     * Ends the session without touching the client, for when another dialog has taken its place:
     * it sends nothing more, and its effects stop delivering.
     */
    public void detach() {
        active = false;
    }

    /**
     * Whether the client will report that the player closed the dialog. Mindustry stops reporting
     * it after the first press in a dialog, and a patch keeps that same dialog; only a dialog sent
     * anew ({@link #open()}) reports again.
     */
    public boolean willReportClose() {
        return active && closeReported;
    }

    /** Renders the current model and shows the dialog with a fresh window token. */
    public void open() {
        if (!active) {
            return;
        }
        VNodeCompiler current = compiler;
        VNode tree = controller.render(model, RenderContext.of(current.resolver()));
        UiBuilder.NodeBuilder<?> compiled = UiPackets.requireFits(current.compile(tree), "dialog");
        this.token = TOKEN_GENERATOR.incrementAndGet();
        this.closeReported = true;
        gateway.show(ctx.playerId(), token, compiled);
    }

    /** Dispatches a domain event directly into the reducer (e.g. from async or push notifications). */
    public void dispatch(Event event) {
        if (event == null || !active) {
            return;
        }

        UpdateResult<Model> update = controller.update(model, event, ctx);
        model = update.model();

        if (update.close()) {
            close();
        } else if (update.fullRerender()) {
            open();
        } else if (!update.isNoop()) {
            patchSlots(update.dirtySlots());
        }

        effects.run(update.effects());
    }

    /**
     * Runs {@code effect} against this session, as if an update had returned it; for work started
     * outside the reducer, such as the first load once the dialog is open.
     */
    public void run(String key, Effect<Event> effect) {
        effects.run(key, effect);
    }

    /** Feeds a client result into the reducer and delivers render directives. */
    public void handle(mindustry.ui.builder.MenuResult result) {
        Objects.requireNonNull(result, "result");
        if (result.token != 0 && result.token != this.token) {
            // Drop stale results from an older window generation (replacement cancels, late clicks)
            return;
        }
        if (!active) {
            return;
        }
        if (result.wasCancelled()) {
            // The player closed the dialog; there is nothing left on the client to hide.
            active = false;
            closeReported = false;
            controller.closed(model);
            return;
        }
        // The client stops reporting a close once something in the dialog was pressed.
        closeReported = false;
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
        VNodeCompiler current = compiler;
        VNode tree = controller.render(model, RenderContext.of(current.resolver()));
        for (SlotKey<?> key : minimal) {
            VSlot slot = VNodes.findSlot(tree, key.path());
            if (slot == null) {
                continue; // slot not in current view; nothing to patch
            }
            UiBuilder.NodeBuilder<?> body = UiPackets.requireFits(current.compileSlotBody(slot), "slot " + key.path());
            gateway.update(ctx.playerId(), token, key.path(), body);
        }
    }

    /** Closes the dialog. */
    public void close() {
        if (!active) {
            return;
        }
        active = false;
        gateway.hide(ctx.playerId());
    }

    /** Transport abstraction; production binds to {@code Call.menuBuilder*}. */
    public interface DeliveryGateway {
        void show(String playerId, long token, UiBuilder.NodeBuilder<?> ui);

        void update(String playerId, long token, String elementId, UiBuilder.NodeBuilder<?> ui);

        void hide(String playerId);
    }

    /**
     * Session registry mapping player -> active session, for host dispatch. A player has one
     * dialog at a time: registering a new session detaches the one it replaces.
     */
    public static final class Registry {
        private final Map<String, UiSession<?, ?>> sessions = new ConcurrentHashMap<>();

        public void register(String playerId, UiSession<?, ?> session) {
            UiSession<?, ?> previous = sessions.put(playerId, session);
            if (previous != null && previous != session) {
                previous.detach();
            }
        }

        @SuppressWarnings("unchecked")
        public <Model, Event> UiSession<Model, Event> get(String playerId) {
            return (UiSession<Model, Event>) sessions.get(playerId);
        }

        public void remove(String playerId) {
            UiSession<?, ?> previous = sessions.remove(playerId);
            if (previous != null) {
                previous.detach();
            }
        }
    }
}
