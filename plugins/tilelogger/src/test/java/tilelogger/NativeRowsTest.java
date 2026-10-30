package tilelogger;

import org.junit.jupiter.api.Test;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

class NativeRowsTest {
    private byte[] row(int type, int flags, int originalBytes, byte[] config) {
        byte[] actor = "игрок🧱\0".getBytes(StandardCharsets.UTF_8);
        var out = ByteBuffer.allocate(128 + actor.length + config.length).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(1).putShort((short) 65535).putShort((short) 65534)
                .putShort((short) 9).putShort((short) 2048);
        out.put((byte) 3).put((byte) type).put((byte) ActionKind.CONFIGURE.id).put((byte) flags);
        out.putLong(70000).putLong(4097).putLong(21).putLong(4096);
        out.putInt(Integer.MIN_VALUE).putInt(originalBytes);
        out.putInt(actor.length).put(actor).putInt(config.length).put(config);
        return Arrays.copyOf(out.array(), out.position());
    }
    private TileState decode(byte[] bytes) {
        return NativeStore.decode(MemorySegment.ofArray(bytes))[0];
    }
    @Test
    void copiesWideFieldsUnicodeAndScalarBitsIntoJavaValues() {
        byte[] bytes = row(1, 7, 0, new byte[0]);
        var state = decode(bytes);
        Arrays.fill(bytes, (byte) 0);
        assertThat(state.x).isEqualTo(65535);
        assertThat(state.y).isEqualTo(65534);
        assertThat(state.team).isEqualTo(9);
        assertThat(state.block).isEqualTo(2048);
        assertThat(state.time).isEqualTo(70000);
        assertThat(state.sequence).isEqualTo(4097);
        assertThat(state.planId).isEqualTo(21);
        assertThat(state.expectedSequence).isEqualTo(4096);
        assertThat(state.uuid).isEqualTo("игрок🧱\0");
        assertThat(state.getConfig()).isEqualTo(Integer.MIN_VALUE);
        assertThat(state.valid && state.destroy && state.legacy).isTrue();
    }
    @Test
    void distinguishesFullConfigurationsFromDisplayPreviews() {
        var full = decode(row(5, 1, 3, new byte[] {1, 2, 3}));
        assertThat((byte[]) full.getConfig()).containsExactly(1, 2, 3);
        var preview = decode(row(5, 9, 100, new byte[] {1, 2, 3}));
        assertThat(preview.configTruncated).isTrue();
        assertThat(preview.configBytes).isEqualTo(100);
        assertThatThrownBy(preview::getConfig).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> decode(row(5, 1, 100, new byte[] {1})))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> decode(row(1, 1, 1, new byte[] {1})))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test
    void rejectsMalformedNativeBuffersBeforeAllocationOrOutOfBoundsReads() {
        byte[] valid = row(1, 1, 0, new byte[0]);
        byte[] negativeLength = valid.clone();
        ByteBuffer.wrap(negativeLength).order(ByteOrder.LITTLE_ENDIAN).putInt(56, -1);
        byte[] oversizedCount = new byte[4];
        ByteBuffer.wrap(oversizedCount).order(ByteOrder.LITTLE_ENDIAN).putInt(4098);
        byte[] invalidAction = valid.clone();
        invalidAction[14] = 99;
        for (byte[] invalid : new byte[][] {new byte[0], new byte[3], oversizedCount, negativeLength,
                Arrays.copyOf(valid, valid.length - 1), Arrays.copyOf(valid, valid.length + 1),
                invalidAction, row(1, 16, 0, new byte[0]), row(10, 1, 0, new byte[0])})
            assertThatThrownBy(() -> NativeStore.decode(MemorySegment.ofArray(invalid)))
                    .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void copyingConfigurationToForeignMemoryDoesNotExposeItsBackingArray() {
        var config = ConfigCodec.encode(new byte[] {1, 2, 3});
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            var bytes = arena.allocate(config.byteSize());
            config.copyBytesTo(bytes);
            assertThat(bytes.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE)).containsExactly(1, 2, 3);
            bytes.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 0, (byte) 99);
            assertThat(config.bytes()).containsExactly(1, 2, 3);
        }
    }

    @Test
    void foreignAllocationFailureRemainsRecoverableByWorldMutationHandlers() {
        var arena = org.mockito.Mockito.mock(java.lang.foreign.Arena.class);
        org.mockito.Mockito.when(arena.allocate(1024, 1)).thenThrow(new OutOfMemoryError("native heap"));
        assertThatThrownBy(() -> NativeStore.allocateNative(arena, 1024, 1))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(OutOfMemoryError.class);
    }
}
