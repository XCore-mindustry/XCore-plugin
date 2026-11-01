package org.xcore.testkit.ui;

import mindustry.gen.MenuBuilderCallPacket;
import mindustry.ui.builder.UiBuilder;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MenuFixturesTest {

    @Test
    void writesThePacketAndItsReadableTree(@TempDir Path dir) throws Exception {
        MenuBuilderCallPacket packet = MenuFixtures.packet(UiBuilder.table().add(UiBuilder.label("Hello")));
        packet.token = 42;
        packet.title = "Menu";

        Path file = MenuFixtures.write(dir, "hello-en", packet);

        MenuBuilderCallPacket back = MenuFixtures.decode(Files.readAllBytes(file));
        assertEquals(42, back.token);
        assertEquals("Menu", back.title);
        assertTrue(back.fillScreen);
        assertTrue(back.hidePrevious);
        assertEquals(UiDslWriter.write(packet.builder), Files.readString(dir.resolve("hello-en.msui")));
    }

    @Test
    void refusesAMenuTooLargeForAPacket(@TempDir Path dir) {
        MenuBuilderCallPacket packet = MenuFixtures.packet(
                UiBuilder.table().add(UiBuilder.label("W".repeat(MenuFixtures.PACKET_LIMIT))));

        assertThrows(IllegalStateException.class, () -> MenuFixtures.write(dir, "large", packet));
        assertFalse(Files.exists(dir.resolve("large.packet")));
    }

    @Test
    void refusesANameThatIsNotAFileStem(@TempDir Path dir) {
        MenuBuilderCallPacket packet = MenuFixtures.packet(UiBuilder.table());

        assertThrows(IllegalArgumentException.class, () -> MenuFixtures.write(dir, "../escape", packet));
    }
}
