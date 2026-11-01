package org.xcore.testkit.ui;

import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.MenuBuilderCallPacket;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import mindustry.ui.builder.UiDslWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Menus written to disk exactly as the server sends them, for the real-client renderer in
 * {@code render/} of this repository.
 *
 * <p>A fixture is a {@code <name>.packet} holding the whole {@link MenuBuilderCallPacket} (the
 * tree and its envelope: title, token, dialog flags) and a readable {@code <name>.msui} beside it.
 * Capture the packet where the plugin hands it to the network, so the renderer sees what a
 * player's client would.
 */
public final class MenuFixtures {

    /** What a client accepts as a packet: it reads one into a 32 KB buffer, less a margin. */
    public static final int PACKET_LIMIT = 24_000;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private MenuFixtures() {
    }

    /** A packet that opens {@code builder} as a full-screen dialog in place of any other. */
    public static MenuBuilderCallPacket packet(NodeBuilder<?> builder) {
        MenuBuilderCallPacket packet = new MenuBuilderCallPacket();
        packet.id = 1;
        packet.hidePrevious = true;
        packet.fillScreen = true;
        packet.builder = Objects.requireNonNull(builder, "builder");
        return packet;
    }

    /** {@code packet} on the wire. */
    public static byte[] encode(MenuBuilderCallPacket packet) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        packet.write(new Writes(new DataOutputStream(bytes)));
        return bytes.toByteArray();
    }

    /** A packet read back from the wire, as the client reads it. */
    public static MenuBuilderCallPacket decode(byte[] bytes) {
        MenuBuilderCallPacket packet = new MenuBuilderCallPacket();
        packet.read(new Reads(new DataInputStream(new ByteArrayInputStream(bytes))), bytes.length);
        packet.handled();
        return packet;
    }

    /**
     * Writes {@code packet} as the fixture {@code name} in {@code dir}, once it has been shown to
     * fit a packet and to come back from the wire unchanged.
     *
     * @return the {@code .packet} file
     * @throws IllegalStateException when the packet is too large or does not survive the wire
     */
    public static Path write(Path dir, String name, MenuBuilderCallPacket packet) throws IOException {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("A fixture name is letters, digits, '-' and '_': " + name);
        }
        byte[] bytes = encode(packet);
        if (bytes.length >= PACKET_LIMIT) {
            throw new IllegalStateException(name + " takes " + bytes.length + " bytes, over the limit of " + PACKET_LIMIT);
        }
        MenuBuilderCallPacket back = decode(bytes);
        String dsl = UiDslWriter.write(packet.builder);
        if (back.id != packet.id || back.token != packet.token || !Objects.equals(back.title, packet.title)
                || back.hideOnClick != packet.hideOnClick || back.hidePrevious != packet.hidePrevious
                || back.fillScreen != packet.fillScreen || !UiDslWriter.write(back.builder).equals(dsl)) {
            throw new IllegalStateException(name + " does not come back from the wire as it was written");
        }
        Files.createDirectories(dir);
        Path file = dir.resolve(name + ".packet");
        Files.write(file, bytes);
        Files.writeString(dir.resolve(name + ".msui"), dsl);
        return file;
    }
}
