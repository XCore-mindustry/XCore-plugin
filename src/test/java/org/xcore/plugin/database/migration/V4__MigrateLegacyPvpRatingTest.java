package org.xcore.plugin.database.migration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class V4__MigrateLegacyPvpRatingTest {

    @Test
    @DisplayName("Metadata identifies migration as version 4 with accurate description")
    void metadataMatchesVersionFour() {
        V4__MigrateLegacyPvpRating migration = new V4__MigrateLegacyPvpRating();
        assertThat(migration.getVersion()).isEqualTo(4);
        assertThat(migration.getDescription()).containsIgnoringCase("legacy");
    }

    @Test
    @DisplayName("up migrates pvp_rating to legacy_pvp_rating and resets new rating to 1000")
    @SuppressWarnings("unchecked")
    void upExecutesCorrectUpdatePipeline() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);

        when(database.getCollection(eq("players"))).thenReturn(collection);

        V4__MigrateLegacyPvpRating migration = new V4__MigrateLegacyPvpRating();
        migration.up(database);

        ArgumentCaptor<Document> filterCaptor = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<List<Document>> updateCaptor = ArgumentCaptor.forClass(List.class);

        verify(collection).updateMany(filterCaptor.capture(), updateCaptor.capture());

        assertThat(filterCaptor.getValue()).containsKey("legacy_pvp_rating");
        assertThat(updateCaptor.getValue()).isNotEmpty();
    }
}
