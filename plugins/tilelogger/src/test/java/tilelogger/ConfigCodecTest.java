package tilelogger;

import arc.math.geom.Point2;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.UnitTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xcore.testkit.fixtures.junit.HeadlessWorldExtension;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(HeadlessWorldExtension.class)
@WithHeadlessWorld(width = 16, height = 16)
class ConfigCodecTest {
    private Object roundtrip(Object value) {
        var encoded = ConfigCodec.encode(value);
        return ConfigCodec.decode(encoded.type(), encoded.type() >= 5 ? encoded.bytes() : encoded.scalar());
    }
    @Test
    void roundtripsAllSupportedKinds() {
        assertThat(roundtrip(null)).isNull();
        for (Object value : new Object[] {-42, true, false, "привет 🧱", Items.copper, Liquids.water,
                     UnitTypes.dagger, Blocks.router, new Point2(-5, 7)})
            assertThat(roundtrip(value)).isEqualTo(value);
        assertThat((byte[]) roundtrip(new byte[] {0, 1, -1})).containsExactly(0, 1, -1);
        assertThat((Point2[]) roundtrip(new Point2[] {new Point2(1, 2), new Point2(-3, 4)}))
                .containsExactly(new Point2(1, 2), new Point2(-3, 4));
        assertThat((Point2[]) roundtrip(new Point2[0])).isEmpty();
    }
    @Test
    void rejectsMalformedPayloadsAndReportsUnsupportedSeparately() {
        assertThatThrownBy(() -> ConfigCodec.decode(7, new byte[3]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfigCodec.decode(3, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfigCodec.decode(2, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> ConfigCodec.decode(8, "item:missing".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfigCodec.decode(6, new byte[] {(byte) 0xc3}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ConfigCodec.encode(new Object()).supported()).isFalse();
        assertThat(ConfigCodec.encode(null).supported()).isTrue();
        assertThat(TileState.formatDuration(360001)).isEqualTo("100:00:01");
    }
}
