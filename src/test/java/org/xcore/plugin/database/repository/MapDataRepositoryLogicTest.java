package org.xcore.plugin.database.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapDataRepositoryLogicTest {

    @Test
    @DisplayName("genKey builds expected composite key")
    void genKey_buildsExpectedCompositeKey() {
        var key = MapDataRepository.genKey("Ancient Ruins", "mapper", "hexed");

        assertThat(key).isEqualTo("Ancient Ruins|mapper|hexed");
    }

    // TODO: add findOrCreate chain unit tests when repository internals are easier
    // to isolate without coupling to MongoCollection call chains.
}
