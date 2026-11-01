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

    @Test
    @DisplayName("streams a texture again after a world load or a reconnect, as the client dropped it")
    void streamsAgainAfterTheClientDroppedTextures() {
        java.util.List<String> sent = new java.util.ArrayList<>();
        XcoreImageService service = new XcoreImageService((connection, region, bytes) -> sent.add(connection));
        Player player = Player.create();
        player.con = null;
        byte[] png = {9, 8, 7};

        service.ensureDelivered(player, png);
        service.ensureDelivered(player, png);
        service.onWorldLoad();
        service.ensureDelivered(player, png);
        service.reset(player);
        service.ensureDelivered(player, png);

        assertThat(sent).hasSize(3);
    }

    @Test
    @DisplayName("forgets an unregistered texture")
    void forgetsUnregisteredTexture() {
        XcoreImageService service = new XcoreImageService((connection, region, bytes) -> {
        });
        String region = service.register(new byte[]{1, 1});

        service.unregister(region);

        assertThat(service.registry().bytes(region)).isNull();
    }
}
