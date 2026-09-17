package org.xcore.testkit.ui;

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
 */
public final class DeterministicUiLoop {
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

    public void sendServerToClient(UiWireMessage message) {
        serverToClientQueue.add(Objects.requireNonNull(message));
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

    public void flushClientOutbox() {
        while (!client.outbox().isEmpty()) {
            HeadlessMenuClient.MenuChoose choose = client.outbox().poll();
            clientToServerQueue.add(new UiWireMessage.Choose(choose.menuId(), choose.token(), choose.action()));
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
}
