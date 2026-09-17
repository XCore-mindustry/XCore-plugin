package org.xcore.plugin.map.domain;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.MapData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapEntityConsolidationTest {

    @Test
    @DisplayName("Consolidates multi-gamemode legacy records into a single MapEntity")
    void consolidatesMultiGamemodeRecordsIntoSingleMapEntity() {
        long now = System.currentTimeMillis();
        MapData survivalRecord = MapData.builder()
                .id(new ObjectId())
                .name("[accent]Desert [white]Crossing")
                .author("[#ffd37f]Anuke")
                .fileName("desert_crossing.msav")
                .contentHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .gameMode("survival")
                .like(10)
                .dislike(2)
                .reputation(25)
                .popularity(50.0)
                .interest(12.5)
                .playedTimes(30)
                .playedTimesYear(10)
                .lastPlayedTime(now - 100_000L)
                .minimumGameTime(60_000L)
                .averageGameTime(300_000L)
                .maximumGameTime(600_000L)
                .build();

        MapData pvpRecord = MapData.builder()
                .id(new ObjectId())
                .name("Desert Crossing")
                .author("Anuke")
                .fileName("desert_crossing.msav")
                .contentHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .gameMode("pvp")
                .like(5)
                .dislike(1)
                .reputation(15)
                .popularity(30.0)
                .interest(8.0)
                .playedTimes(20)
                .playedTimesYear(5)
                .lastPlayedTime(now - 50_000L) // later than survival
                .minimumGameTime(120_000L)
                .averageGameTime(240_000L)
                .maximumGameTime(480_000L)
                .build();

        MapData attackRecord = MapData.builder()
                .id(new ObjectId())
                .name("Desert Crossing")
                .author("Anuke")
                .fileName("desert_crossing.msav")
                .contentHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .gameMode("attack")
                .like(2)
                .dislike(0)
                .reputation(5)
                .popularity(10.0)
                .interest(3.0)
                .playedTimes(10)
                .playedTimesYear(2)
                .lastPlayedTime(now - 200_000L)
                .minimumGameTime(90_000L)
                .averageGameTime(180_000L)
                .maximumGameTime(360_000L)
                .build();

        MapDataConsolidator consolidator = new MapDataConsolidator();
        MapEntity entity = consolidator.consolidate(List.of(survivalRecord, pvpRecord, attackRecord));

        assertThat(entity).isNotNull();
        // Canonical metadata
        assertThat(entity.name).isEqualTo("Desert Crossing");
        assertThat(entity.author).isEqualTo("Anuke");
        assertThat(entity.fileName).isEqualTo("desert_crossing.msav");
        assertThat(entity.contentHash).isEqualTo("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertThat(entity.slug).isEqualTo("anuke/desert-crossing");

        // Aggregated global ratings
        assertThat(entity.likes).isEqualTo(17);
        assertThat(entity.dislikes).isEqualTo(3);
        assertThat(entity.reputation).isEqualTo(45);
        assertThat(entity.popularity).isEqualTo(90.0);
        assertThat(entity.interest).isEqualTo(23.5);
        assertThat(entity.totalPlayCount).isEqualTo(60);
        assertThat(entity.lastPlayedAt).isEqualTo(now - 50_000L);

        // Per-gamemode telemetry stats
        assertThat(entity.gamemodeStats).containsOnlyKeys("survival", "pvp", "attack");
        MapEntity.GamemodeMetrics survivalMetrics = entity.gamemodeStats.get("survival");
        assertThat(survivalMetrics.plays).isEqualTo(30);
        assertThat(survivalMetrics.minimumDurationMs).isEqualTo(60_000L);
        assertThat(survivalMetrics.averageDurationMs).isEqualTo(300_000L);
        assertThat(survivalMetrics.maximumDurationMs).isEqualTo(600_000L);

        MapEntity.GamemodeMetrics pvpMetrics = entity.gamemodeStats.get("pvp");
        assertThat(pvpMetrics.plays).isEqualTo(20);
        assertThat(pvpMetrics.minimumDurationMs).isEqualTo(120_000L);
        assertThat(pvpMetrics.averageDurationMs).isEqualTo(240_000L);
        assertThat(pvpMetrics.maximumDurationMs).isEqualTo(480_000L);
    }
}
