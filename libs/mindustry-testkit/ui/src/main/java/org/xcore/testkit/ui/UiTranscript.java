package org.xcore.testkit.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Chronological log of messages exchanged across the UI transport boundary.
 */
public final class UiTranscript {
    private final List<UiWireMessage> entries = new ArrayList<>();

    public void record(UiWireMessage message) {
        entries.add(message);
    }

    public int size() {
        return entries.size();
    }

    public UiWireMessage get(int index) {
        return entries.get(index);
    }

    public List<UiWireMessage> all() {
        return Collections.unmodifiableList(entries);
    }

    /** Messages of one kind, in order. */
    public <T extends UiWireMessage> List<T> all(Class<T> type) {
        return entries.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** The latest message of one kind. */
    public <T extends UiWireMessage> Optional<T> last(Class<T> type) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (type.isInstance(entries.get(i))) return Optional.of(type.cast(entries.get(i)));
        }
        return Optional.empty();
    }

    public List<UiWireMessage.Show> shows() {
        return all(UiWireMessage.Show.class);
    }

    public List<UiWireMessage.Update> updates() {
        return all(UiWireMessage.Update.class);
    }

    public List<UiWireMessage.Choose> chooses() {
        return all(UiWireMessage.Choose.class);
    }

    /**
     * The latest {@link UiWireMessage.Show}.
     *
     * @throws IllegalStateException when no menu was shown
     */
    public UiWireMessage.Show lastShow() {
        return last(UiWireMessage.Show.class)
                .orElseThrow(() -> new IllegalStateException("No Show in a transcript of " + entries.size() + " messages"));
    }

    /**
     * The latest {@link UiWireMessage.Update}.
     *
     * @throws IllegalStateException when no slot was patched
     */
    public UiWireMessage.Update lastUpdate() {
        return last(UiWireMessage.Update.class)
                .orElseThrow(() -> new IllegalStateException("No Update in a transcript of " + entries.size() + " messages"));
    }
}
