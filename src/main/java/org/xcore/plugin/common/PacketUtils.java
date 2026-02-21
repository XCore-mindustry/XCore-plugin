package org.xcore.plugin.common;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static mindustry.Vars.charset;

public final class PacketUtils {

    private static final int DEFAULT_MAX_LENGTH = 32;

    private PacketUtils() {
    }

    public static void writeString(ByteBuffer buffer, String string, int maxLength) {
        byte[] bytes = string.getBytes(charset);
        if (bytes.length > maxLength) {
            bytes = Arrays.copyOfRange(bytes, 0, maxLength);
        }
        buffer.put((byte) bytes.length);
        buffer.put(bytes);
    }

    public static void writeString(ByteBuffer buffer, String string) {
        writeString(buffer, string, DEFAULT_MAX_LENGTH);
    }
}
