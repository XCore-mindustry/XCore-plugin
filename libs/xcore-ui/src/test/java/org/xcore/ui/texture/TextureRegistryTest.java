package org.xcore.ui.texture;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextureRegistryTest {

    record DeliveryEvent(String connectionId, String regionName, int byteCount) {}

    @Test
    void contentAddressableSha256DeduplicatesIdenticalBytes() {
        List<DeliveryEvent> events = new ArrayList<>();
        TextureRegistry registry = new TextureRegistry((con, region, bytes) ->
                events.add(new DeliveryEvent(con, region, bytes.length)));

        byte[] pngA = new byte[]{1, 2, 3, 4, 5};
        byte[] pngB = new byte[]{1, 2, 3, 4, 5};
        byte[] pngC = new byte[]{9, 9, 9};

        String nameA = registry.register(pngA);
        String nameB = registry.register(pngB);
        String nameC = registry.register(pngC);

        assertThat(nameA).isEqualTo(nameB);
        assertThat(nameA).startsWith("net-xcore_");
        assertThat(nameA).isNotEqualTo(nameC);
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void ensureDeliveredSendsOnlyOncePerConnection() {
        List<DeliveryEvent> events = new ArrayList<>();
        TextureRegistry registry = new TextureRegistry((con, region, bytes) ->
                events.add(new DeliveryEvent(con, region, bytes.length)));

        byte[] png = new byte[]{0x7f, 'P', 'N', 'G'};

        // First delivery to conn1 -> streams
        String name = registry.ensureDelivered("conn1", png);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).connectionId()).isEqualTo("conn1");
        assertThat(events.get(0).regionName()).isEqualTo(name);

        // Second delivery of same PNG to conn1 -> NO-OP (cached delivery)
        registry.ensureDelivered("conn1", png);
        assertThat(events).hasSize(1);

        // Delivery of same PNG to conn2 -> streams to conn2
        registry.ensureDelivered("conn2", png);
        assertThat(events).hasSize(2);
        assertThat(events.get(1).connectionId()).isEqualTo("conn2");

        // After resetDelivery for conn1, re-streams
        registry.resetDelivery("conn1");
        registry.ensureDelivered("conn1", png);
        assertThat(events).hasSize(3);
    }
}
