package org.xcore.plugin.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BiMapTest {

    @Test
    @DisplayName("put provides forward and reverse lookup")
    void putAndLookup() {
        var map = new BiMap<String, Integer>();
        map.put("alpha", 1);

        assertThat(map.get("alpha")).isEqualTo(1);
        assertThat(map.getByValue(1)).isEqualTo("alpha");
        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("put with existing value keeps bijection by removing old key")
    void replaceExistingValue() {
        var map = new BiMap<String, Integer>();
        map.put("alpha", 1);
        map.put("beta", 1);

        assertThat(map.get("alpha")).isNull();
        assertThat(map.get("beta")).isEqualTo(1);
        assertThat(map.getByValue(1)).isEqualTo("beta");
        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("put with existing key updates inverse map")
    void replaceExistingKey() {
        var map = new BiMap<String, Integer>();
        map.put("alpha", 1);
        map.put("alpha", 2);

        assertThat(map.get("alpha")).isEqualTo(2);
        assertThat(map.getByValue(1)).isNull();
        assertThat(map.getByValue(2)).isEqualTo("alpha");
    }

    @Test
    @DisplayName("keySet view is unmodifiable")
    void keySetIsUnmodifiable() {
        var map = new BiMap<String, Integer>();
        map.put("alpha", 1);

        assertThatThrownBy(() -> map.keySet().add("beta"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("entrySet reflects map entries")
    void entrySetContainsEntries() {
        var map = new BiMap<String, Integer>();
        map.put("alpha", 1);

        assertThat(map.entrySet()).contains(Map.entry("alpha", 1));
    }
}
