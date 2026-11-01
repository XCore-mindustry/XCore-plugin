package org.xcore.testkit.ui;

import mindustry.ui.builder.UiBuilder;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiSnapshotTest {
    @Test
    void sendingFreezesBuilderAndEachDeliveryGetsAnIndependentCopy() {
        var builder = UiBuilder.table().add(UiBuilder.label("initial"));
        var snapshot = UiSnapshot.capture(builder);
        builder.add(UiBuilder.label("after send"));

        var delivered = (UiBuilder.TableBuilder) snapshot.decode();
        delivered.add(UiBuilder.label("after delivery"));
        String received = UiDslWriter.write(snapshot.decode());
        assertTrue(received.contains("initial"));
        assertFalse(received.contains("after send"));
        assertFalse(received.contains("after delivery"));
    }
}
