package org.xcore.plugin.service;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapIdentityAuditServiceTest {

    @Test
    @DisplayName("audit reports legacy collision groups and affected player votes")
    void auditReportsLegacyCollisionGroupsAndAffectedVotes() {
        MapDataRepository mapRepository = mock(MapDataRepository.class);
        PlayerDataRepository playerRepository = mock(PlayerDataRepository.class);

        MapData first = map("Arena", "arena-v1.msav", "Author One", "pvp", 3, 1, 2);
        MapData second = map("Arena", "arena-v2.msav", "Author Two", "pvp", 4, 2, 1);
        MapData unrelated = map("Other", "other.msav", "Mapper", "pvp", 1, 0, 0);

        PlayerData player = new PlayerData("player-1", true);
        player.pid = 12;
        player.nickname = "Extrayzi";
        player.mapVotes = Map.of(
                first.id.toHexString(), true,
                second.id.toHexString(), false,
                unrelated.id.toHexString(), true
        );

        when(mapRepository.findAll()).thenReturn(List.of(first, second, unrelated));
        when(playerRepository.findAllWithMapVotes()).thenReturn(List.of(player));

        MapIdentityAuditService service = new MapIdentityAuditService(mapRepository, playerRepository);
        var report = service.audit();

        assertThat(report.mapsScanned()).isEqualTo(3);
        assertThat(report.playersScanned()).isEqualTo(1);
        assertThat(report.hasConflicts()).isTrue();
        assertThat(report.conflictGroups()).hasSize(1);
        assertThat(report.conflictingMapCount()).isEqualTo(2);
        assertThat(report.affectedPlayerCount()).isEqualTo(1);
        assertThat(report.affectedVoteReferenceCount()).isEqualTo(2);

        var group = report.conflictGroups().getFirst();
        assertThat(group.legacyKey()).isEqualTo("Arena|pvp");
        assertThat(group.maps()).hasSize(2);
        assertThat(group.affectedPlayers()).containsExactly("player-1");
        assertThat(group.affectedVoteReferences()).isEqualTo(2);
        assertThat(group.affectedVotes()).hasSize(1);
        assertThat(group.affectedVotes().getFirst().conflictingMapIds())
                .containsExactlyInAnyOrder(first.id.toHexString(), second.id.toHexString());
    }

    @Test
    @DisplayName("audit ignores groups that only share legacy key without differing identity metadata")
    void auditIgnoresNonConflictingGroups() {
        MapDataRepository mapRepository = mock(MapDataRepository.class);
        PlayerDataRepository playerRepository = mock(PlayerDataRepository.class);

        MapData first = map("Arena", "arena.msav", "Author", "pvp", 0, 0, 0);
        MapData second = map("Arena", "arena.msav", "Author", "pvp", 0, 0, 0);

        when(mapRepository.findAll()).thenReturn(List.of(first, second));
        when(playerRepository.findAllWithMapVotes()).thenReturn(List.of());

        MapIdentityAuditService service = new MapIdentityAuditService(mapRepository, playerRepository);
        var report = service.audit();

        assertThat(report.hasConflicts()).isFalse();
        assertThat(report.conflictGroups()).isEmpty();
        assertThat(report.affectedPlayerCount()).isZero();
        assertThat(report.affectedVoteReferenceCount()).isZero();
    }

    private static MapData map(String name,
                               String fileName,
                               String author,
                               String mode,
                               int like,
                               int dislike,
                               int reputation) {
        MapData map = new MapData(name, fileName, author, mode);
        map.id = new ObjectId();
        map.like = like;
        map.dislike = dislike;
        map.reputation = reputation;
        return map;
    }
}
