package org.xcore.testkit.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
}
