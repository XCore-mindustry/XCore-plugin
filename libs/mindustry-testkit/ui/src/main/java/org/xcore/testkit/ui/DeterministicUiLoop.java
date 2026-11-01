package org.xcore.testkit.ui;

import arc.Application;
import arc.Core;
import arc.mock.MockApplication;
import mindustry.gen.Player;
import mindustry.ui.Menus;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import org.xcore.testkit.core.DeterministicQueue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Deterministic test harness driving two FIFO transport queues (server-to-client,
 * client-to-server) and a snapshot-drain server-post queue.
 *
 * <p>The server side feeds it one of three ways: {@link #sendServerToClient} with a ready
 * message, {@link #sendShow}/{@link #sendUpdate}/{@link #sendHide} from a test gateway, or
 * {@link #sendPacket} with the {@code Call.menuBuilder*} packets the plugin really sends (for
 * instance from {@code MockNetConnection.onSend} in the {@code fixtures} module). Answers come
 * back through {@link #onChoose}, or straight into the plugin's registered menu listener with
 * {@link #dispatchChoicesTo}.
 */
public final class DeterministicUiLoop {
    /** Steps {@link #runUntilIdle()} allows before it decides the scenario never settles. */
    public static final int DEFAULT_MAX_STEPS = 1_000;

    private final HeadlessMenuClient client;
    private final DeterministicQueue serverPost = new DeterministicQueue();
    private final Queue<UiWireMessage> serverToClientQueue = new ArrayDeque<>();
    private final Queue<UiWireMessage> clientToServerQueue = new ArrayDeque<>();
    private final UiTranscript transcript = new UiTranscript();
    private final List<Consumer<UiWireMessage>> clientMessageListeners = new ArrayList<>();

    public DeterministicUiLoop() {
        this(new HeadlessMenuClient());
    }

    public DeterministicUiLoop(HeadlessMenuClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public HeadlessMenuClient client() {
        return client;
    }

    public DeterministicQueue serverPost() {
        return serverPost;
    }

    public UiTranscript transcript() {
        return transcript;
    }

    public void onClientMessage(Consumer<UiWireMessage> listener) {
        clientMessageListeners.add(Objects.requireNonNull(listener));
    }

    /** Like {@link #onClientMessage}, for the only client message there is: a menu answer. */
    public void onChoose(Consumer<UiWireMessage.Choose> listener) {
        Objects.requireNonNull(listener);
        onClientMessage(message -> {
            if (message instanceof UiWireMessage.Choose choose) listener.accept(choose);
        });
    }

    /**
     * Delivers every answer to the server as {@code player}'s, through
     * {@code Menus.menuBuilderChoose} — the call a {@code MenuBuilderChooseCallPacket} makes on a
     * live server — so it reaches the listener the plugin registered with
     * {@code Menus.registerMenuBuilder}.
     */
    public void dispatchChoicesTo(Player player) {
        Objects.requireNonNull(player, "player");
        onChoose(choose -> Menus.menuBuilderChoose(player, choose.menuId(), choose.toMenuResult()));
    }

    public void sendServerToClient(UiWireMessage message) {
        serverToClientQueue.add(Objects.requireNonNull(message));
    }

    /** Queues a menu as {@code Call.menuBuilder} sends it, capturing {@code ui} now. */
    public void sendShow(int menuId, long token, boolean hidePrevious, NodeBuilder<?> ui) {
        sendServerToClient(new UiWireMessage.Show(menuId, token, hidePrevious, UiSnapshot.capture(ui)));
    }

    /** Queues a slot patch as {@code Call.menuBuilderUpdate} sends it, capturing {@code ui} now. */
    public void sendUpdate(int menuId, String targetId, NodeBuilder<?> ui) {
        sendServerToClient(new UiWireMessage.Update(menuId, targetId, UiSnapshot.capture(ui)));
    }

    /** Queues {@code Call.hideMenuBuilder}. */
    public void sendHide(int menuId) {
        sendServerToClient(new UiWireMessage.Hide(menuId));
    }

    /**
     * Queues a menu packet ({@code MenuBuilderCallPacket}, {@code MenuBuilderUpdateCallPacket},
     * {@code HideMenuBuilderCallPacket}) and ignores any other.
     *
     * @return whether {@code packet} was a menu packet
     */
    public boolean sendPacket(Object packet) {
        return UiWireMessage.fromPacket(packet).map(message -> {
            sendServerToClient(message);
            return true;
        }).orElse(false);
    }

    public boolean stepServerToClient() {
        UiWireMessage msg = serverToClientQueue.poll();
        if (msg == null) return false;
        transcript.record(msg);
        switch (msg) {
            case UiWireMessage.Show s -> client.show(s.menuId(), s.token(), s.hidePrevious(), s.body());
            case UiWireMessage.Update u -> client.update(u.menuId(), u.targetId(), u.body());
            case UiWireMessage.Hide h -> client.hide(h.menuId());
            case UiWireMessage.Choose ignored -> {}
        }
        return true;
    }

    /** Delivers every queued server message to the client; nothing goes back. */
    public int deliverToClient() {
        int delivered = 0;
        while (stepServerToClient()) delivered++;
        return delivered;
    }

    public void flushClientOutbox() {
        while (!client.outbox().isEmpty()) {
            HeadlessMenuClient.MenuChoose choose = client.outbox().poll();
            clientToServerQueue.add(new UiWireMessage.Choose(choose.menuId(), choose.token(), choose.action(), choose.values()));
        }
    }

    public boolean stepClientToServer() {
        flushClientOutbox();
        UiWireMessage msg = clientToServerQueue.poll();
        if (msg == null) return false;
        transcript.record(msg);
        for (Consumer<UiWireMessage> l : clientMessageListeners) {
            l.accept(msg);
        }
        return true;
    }

    public void stepServerPost() {
        serverPost.runTurn();
    }

    public boolean stepAll() {
        boolean acted = false;
        if (stepServerToClient()) acted = true;
        if (stepClientToServer()) acted = true;
        stepServerPost();
        return acted;
    }

    /** Nothing in flight: both transport queues, the client outbox and the server-post queue are empty. */
    public boolean isIdle() {
        return serverToClientQueue.isEmpty() && clientToServerQueue.isEmpty()
                && client.outbox().isEmpty() && serverPost.isEmpty();
    }

    /**
     * Steps until {@link #isIdle()}: a server message to the client, an answer to the server, a
     * turn of server posts, and again.
     *
     * @return the number of steps that ran
     * @throws IllegalStateException when the loop is not idle after {@link #DEFAULT_MAX_STEPS} steps
     */
    public int runUntilIdle() {
        return runUntilIdle(DEFAULT_MAX_STEPS);
    }

    /**
     * Steps until {@link #isIdle()}.
     *
     * @return the number of steps that ran
     * @throws IllegalStateException when the loop is not idle after {@code maxSteps} steps
     */
    public int runUntilIdle(int maxSteps) {
        for (int step = 0; step < maxSteps; step++) {
            if (isIdle()) return step;
            stepServerToClient();
            stepClientToServer();
            stepServerPost();
        }
        if (isIdle()) return maxSteps;
        throw new IllegalStateException("UI loop is not idle after " + maxSteps + " steps");
    }

    /**
     * Makes {@code Core.app.post} queue on {@link #serverPost()} until the returned scope closes,
     * which puts back the {@code Core.app} that was there before.
     *
     * <p>Install it after creating a {@code HeadlessWorld} (which sets its own {@code Core.app}) and
     * close it before the world.
     */
    public CoreAppScope installAsCoreApp() {
        Application previous = Core.app;
        Core.app = new MockApplication() {
            @Override
            public void post(Runnable runnable) {
                serverPost.post(runnable);
            }
        };
        return () -> Core.app = previous;
    }

    /** A {@code Core.app} replacement that is undone on {@link #close()}. */
    @FunctionalInterface
    public interface CoreAppScope extends AutoCloseable {
        @Override
        void close();
    }
}
