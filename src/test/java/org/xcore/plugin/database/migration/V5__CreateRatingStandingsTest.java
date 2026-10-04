package org.xcore.plugin.database.migration;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class V5__CreateRatingStandingsTest {

    @Test
    @DisplayName("Metadata identifies migration as version 5")
    void metadataMatchesVersionFive() {
        V5__CreateRatingStandings migration = new V5__CreateRatingStandings();
        assertThat(migration.getVersion()).isEqualTo(5);
        assertThat(migration.getDescription()).contains("rating_standings");
    }

    @Test
    @DisplayName("up copies both ratings into rating_standings without touching the sources")
    @SuppressWarnings("unchecked")
    void upMergesBothSourcesIntoStandings() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> standings = mock(MongoCollection.class);
        MongoCollection<Document> players = mock(MongoCollection.class);
        MongoCollection<Document> hexed = mock(MongoCollection.class);
        AggregateIterable<Document> playersResult = mock(AggregateIterable.class);
        AggregateIterable<Document> hexedResult = mock(AggregateIterable.class);
        when(database.getCollection("rating_standings")).thenReturn(standings);
        when(database.getCollection("players")).thenReturn(players);
        when(database.getCollection("xcore_plugin_hexedcore_rating_players")).thenReturn(hexed);
        when(players.aggregate(anyList())).thenReturn(playersResult);
        when(hexed.aggregate(anyList())).thenReturn(hexedResult);

        new V5__CreateRatingStandings().up(database);

        ArgumentCaptor<Document> index = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<IndexOptions> indexOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(standings).createIndex(index.capture(), indexOptions.capture());
        assertThat(index.getValue().keySet()).containsExactly("ladder", "season", "player_uuid");
        assertThat(indexOptions.getValue().isUnique()).isTrue();

        List<Document> miniPvp = pipeline(players);
        assertThat(miniPvp.getFirst().get("$match", Document.class)).containsKeys("pvp_matches", "uuid");
        assertThat(projection(miniPvp).get("ladder", Document.class).getString("$literal")).isEqualTo("minipvp");
        assertThat(projection(miniPvp).getString("player_uuid")).isEqualTo("$uuid");
        assertMergesIntoStandings(miniPvp);
        verify(playersResult).toCollection();

        List<Document> hexedCore = pipeline(hexed);
        assertThat(projection(hexedCore).get("ladder", Document.class).getString("$literal")).isEqualTo("hexed");
        assertThat(projection(hexedCore).get("stats", Document.class)).containsKeys("top3", "disconnects");
        assertMergesIntoStandings(hexedCore);
        verify(hexedResult).toCollection();

        verify(players, never()).updateMany(any(Document.class), anyList());
        verifyNoMoreInteractions(players, hexed);
    }

    @SuppressWarnings("unchecked")
    private static List<Document> pipeline(MongoCollection<Document> collection) {
        ArgumentCaptor<List<Document>> pipeline = ArgumentCaptor.forClass(List.class);
        verify(collection).aggregate(pipeline.capture());
        return pipeline.getValue();
    }

    private static Document projection(List<Document> pipeline) {
        return pipeline.get(1).get("$project", Document.class);
    }

    private static void assertMergesIntoStandings(List<Document> pipeline) {
        Document merge = pipeline.getLast().get("$merge", Document.class);
        assertThat(merge.getString("into")).isEqualTo("rating_standings");
        assertThat(merge.getList("on", String.class)).containsExactly("ladder", "season", "player_uuid");
        // Re-running the migration must never overwrite a standing the ladder already owns.
        assertThat(merge.getString("whenMatched")).isEqualTo("keepExisting");
    }
}
