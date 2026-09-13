package org.xcore.plugin.ui;

import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XcoreImageServiceTest {

    @Test
    @DisplayName("registers PNG bytes with content-addressed SHA-256 hash")
    void registersPngBytes() {
        XcoreImageService service = new XcoreImageService();

        byte[] png1 = new byte[]{1, 2, 3, 4};
        byte[] png2 = new byte[]{1, 2, 3, 4};
        byte[] png3 = new byte[]{5, 6, 7, 8};

        String name1 = service.register(png1);
        String name2 = service.register(png2);
        String name3 = service.register(png3);

        assertThat(name1).isEqualTo(name2);
        assertThat(name1).startsWith("net-xcore_");
        assertThat(name1).isNotEqualTo(name3);
    }

    @Test
    @DisplayName("ensureDelivered safely tolerates null player or null connection")
    void ensureDeliveredToleratesNull() {
        XcoreImageService service = new XcoreImageService();

        assertThat(service.ensureDelivered(null, new byte[]{1})).isNull();

        Player player = Player.create();
        player.con = null;

        String name = service.ensureDelivered(player, new byte[]{1, 2, 3});
        assertThat(name).startsWith("net-xcore_");
    }
}
