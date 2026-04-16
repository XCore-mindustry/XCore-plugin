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

    @Test
    @DisplayName("same name with different file or author must remain distinct identities")
    void duplicateDisplayNamesShouldRemainDistinct() {
        var first = new MapIdentity("Arena", "arena-v1.msav", "Author One", "pvp");
        var second = new MapIdentity("Arena", "arena-v2.msav", "Author Two", "pvp");

        assertThat(first.matchesByFileName(second)).isFalse();
        assertThat(first.matchesByNameAuthorMode(second)).isFalse();
        assertThat(first.collidesByLegacyNameMode(second)).isTrue();
    }

    @Test
    @DisplayName("file name and mode provide stable identity when display metadata changes")
    void fileNameAndModeProvideStableIdentity() {
        var original = new MapIdentity("Arena", "arena.msav", "Author One", "pvp");
        var refreshedMetadata = new MapIdentity("Arena Reloaded", "arena.msav", "Author Two", "pvp");

        assertThat(original.matchesByFileName(refreshedMetadata)).isTrue();
        assertThat(original.matchesByNameAuthorMode(refreshedMetadata)).isFalse();
    }

    private record MapIdentity(String name, String fileName, String author, String mode) {

        boolean matchesByFileName(MapIdentity other) {
            return fileName.equals(other.fileName) && mode.equals(other.mode);
        }

        boolean matchesByNameAuthorMode(MapIdentity other) {
            return name.equals(other.name) && author.equals(other.author) && mode.equals(other.mode);
        }

        boolean collidesByLegacyNameMode(MapIdentity other) {
            return name.equals(other.name) && mode.equals(other.mode);
        }
    }
}
