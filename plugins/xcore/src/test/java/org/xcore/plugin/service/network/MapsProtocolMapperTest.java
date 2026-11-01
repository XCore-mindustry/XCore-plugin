package org.xcore.plugin.service.network;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapsProtocolMapperTest {
    @Test
    void capKeepsShortTextAndNeverSplitsSurrogatePairs() {
        assertThat(MapsProtocolMapper.cap(null, 3)).isNull();
        assertThat(MapsProtocolMapper.cap("abc", 3)).isEqualTo("abc");
        assertThat(MapsProtocolMapper.cap("abcd", 3)).isEqualTo("abc");
        assertThat(MapsProtocolMapper.cap("ab😀", 3)).isEqualTo("ab");
    }
}
