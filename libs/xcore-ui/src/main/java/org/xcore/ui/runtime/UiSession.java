package org.xcore.ui.runtime;

import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.UiPackets;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.VSlot;
import org.xcore.ui.VNodes;
import mindustry.ui.builder.UiBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * The effects of the update that closes the dialog still run, for what follows the close such as
 * going back to the previous menu; what they dispatch is dropped all the same.
 *
 * <p>A patch reaches the client only where the client has the slot: it replaces the content of a
 * table it built under that id, and an id it does not know is ignored. So the session keeps the
 * slots of what it sent, and when a patch names a slot the new view has and the client has not, or
 * one the client has and the new view has dropped, it sends the whole dialog instead. A slot in
 * neither, such as one of a layout this view does not have, is passed over. A slot nested in
 * another slot of the same patch is not sent again: the outer one carries it.
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

    /**
     * The slots of what the client was sent, each with the keys of the slots nested in it at any
     * depth. Updated on the thread that runs the session, like the rest of it.
     */
    private final Map<String, Set<String>> shownSlots = new HashMap<>();

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
        shownSlots.clear();
        remember(tree);
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
        if (!active) {
            return;
        }
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
            gateway.closed(ctx.playerId());
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
        VNodeCompiler current = compiler;
        VNode tree = controller.render(model, RenderContext.of(current.resolver()));
        Map<String, VSlot> slots = VNodes.slotsByKey(tree);

        // A slot in neither the view nor what the client has belongs to another view, such as a
        // layout this one does not have. It goes before pruning, or it would take the slots under
        // its path with it.
        List<SlotKey<Object>> minimal = SlotPruner.prune(dirty.stream()
                .map(s -> new SlotKey<Object>(s.path()))
                .filter(key -> slots.containsKey(key.path()) || shownSlots.containsKey(key.path()))
                .toList());
        if (minimal.isEmpty()) {
            return;
        }

        // What a slot this patch can send carries with it: the slots of its new body, and those of
        // its old body that the view has dropped.
        Set<String> carried = new HashSet<>();
        for (SlotKey<?> key : minimal) {
            VSlot slot = slots.get(key.path());
            if (slot != null && shownSlots.containsKey(key.path())) {
                carried.addAll(nestedSlots(slot));
                for (String old : shownUnder(key.path())) {
                    if (!slots.containsKey(old)) {
                        carried.add(old);
                    }
                }
            }
        }

        Map<String, VSlot> targets = new LinkedHashMap<>();
        for (SlotKey<?> key : minimal) {
            if (carried.contains(key.path())) {
                continue;
            }
            VSlot slot = slots.get(key.path());
            if (slot == null || !shownSlots.containsKey(key.path())) {
                // The client lacks the slot, or the view no longer has it: a patch cannot say so.
                open();
                return;
            }
            targets.put(key.path(), slot);
        }

        // Compile every body before sending any, so a body over the packet limit sends none.
        Map<String, UiBuilder.NodeBuilder<?>> bodies = new LinkedHashMap<>();
        for (VSlot slot : targets.values()) {
            bodies.put(slot.slotKey(), UiPackets.requireFits(current.compileSlotBody(slot), "slot " + slot.slotKey()));
        }
        for (Map.Entry<String, UiBuilder.NodeBuilder<?>> body : bodies.entrySet()) {
            forget(body.getKey());
            remember(targets.get(body.getKey()));
            gateway.update(ctx.playerId(), token, body.getKey(), body.getValue());
        }
    }

    /** Records every slot of {@code root}, itself included when it is one. */
    private void remember(VNode root) {
        for (VNode node : VNodes.walk(root)) {
            if (node instanceof VSlot slot) {
                shownSlots.put(slot.slotKey(), nestedSlots(slot));
            }
        }
    }

    /**
     * Drops {@code key} and what the client has inside it, at any depth: a patch of it replaces
     * them. Each nested slot's own set is followed, so a slot an earlier patch put deeper down
     * goes too, though the outer slot's set predates it.
     */
    private void forget(String key) {
        Set<String> nested = shownSlots.remove(key);
        if (nested != null) {
            for (String inner : nested) {
                forget(inner);
            }
        }
    }

    /** The slots the client has inside {@code key}, at any depth. */
    private Set<String> shownUnder(String key) {
        Set<String> under = new LinkedHashSet<>();
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>(shownSlots.getOrDefault(key, Set.of()));
        while (!pending.isEmpty()) {
            String next = pending.poll();
            if (under.add(next)) {
                pending.addAll(shownSlots.getOrDefault(next, Set.of()));
            }
        }
        return under;
    }

    private static Set<String> nestedSlots(VSlot slot) {
        Set<String> keys = new LinkedHashSet<>();
        for (VNode child : slot.children()) {
            for (VNode node : VNodes.walk(child)) {
                if (node instanceof VSlot nested) {
                    keys.add(nested.slotKey());
                }
            }
        }
        return keys;
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

        /** The player closed the dialog on the client: nothing is to be sent, only bookkeeping done. */
        default void closed(String playerId) {
        }
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
