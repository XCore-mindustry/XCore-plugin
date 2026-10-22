package org.xcore.ui;

import arc.util.io.Writes;
import mindustry.ui.builder.UiBuilder;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * Size of a compiled dialog on the wire.
 *
 * <p>A dialog travels as one packet, and the client reads a packet into a 32 KB buffer: a larger
 * one does not arrive as a dialog at all. {@link #LIMIT} leaves a margin under that buffer.
 */
public final class UiPackets {

    /** Bytes a compiled dialog or slot body may take; 32 KB on the client, less a margin. */
    public static final int LIMIT = 24_000;

    private UiPackets() {
    }

    /** Bytes {@code ui} takes on the wire. */
    public static int size(UiBuilder.NodeBuilder<?> ui) {
        var bytes = new ByteArrayOutputStream();
        try (var writes = new Writes(new DataOutputStream(bytes))) {
            ui.write(writes);
        }
        return bytes.size();
    }

    public static boolean fits(UiBuilder.NodeBuilder<?> ui) {
        return size(ui) < LIMIT;
    }

    /** Returns {@code ui} if it fits a packet; otherwise throws, before anything is sent. */
    public static <T extends UiBuilder.NodeBuilder<?>> T requireFits(T ui, String what) {
        int size = size(ui);
        if (size >= LIMIT) {
            throw new TooLargeException(what, size);
        }
        return ui;
    }

    /** A dialog or a slot body larger than {@link #LIMIT}. */
    public static final class TooLargeException extends IllegalStateException {
        private final int size;

        public TooLargeException(String what, int size) {
            super(what + " takes " + size + " bytes, over the packet limit of " + LIMIT);
            this.size = size;
        }

        public int size() {
            return size;
        }
    }
}
