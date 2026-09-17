package org.xcore.testkit.ui;

import mindustry.ui.builder.UiBuilder.NodeBuilder;
import arc.util.io.Reads;
import arc.util.io.Writes;
import java.io.*;
import java.util.Objects;

/** A wire payload captured at send time. */
public final class UiSnapshot {
    private final byte[] bytes;

    private UiSnapshot(byte[] bytes) { this.bytes = bytes; }

    public static UiSnapshot capture(NodeBuilder<?> builder) {
        Objects.requireNonNull(builder, "builder");
        var buffer = new ByteArrayOutputStream();
        builder.write(new Writes(new DataOutputStream(buffer)));
        return new UiSnapshot(buffer.toByteArray());
    }

    public NodeBuilder<?> decode() {
        return NodeBuilder.read(new Reads(new DataInputStream(new ByteArrayInputStream(bytes))));
    }
}
