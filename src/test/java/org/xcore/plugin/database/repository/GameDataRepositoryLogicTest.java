package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.model.GameData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameDataRepositoryLogicTest {
    private MongoCollection<GameData> collection;
    private GameDataRepository repository;

    @BeforeEach
    void setUp() {
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(database.getCollection("games_v2", GameData.class)).thenReturn(collection);
        repository = new GameDataRepository(database, new TomlSecretsConfig());
    }

    @Test
    void saveOnceInsertsNewMatch() {
        var game = GameData.builder().matchId("match-1").build();

        assertThat(repository.saveOnce(game)).isTrue();
        verify(collection).insertOne(game);
    }

    @Test
    void saveOnceTreatsConcurrentDuplicateAsAlreadyStored() {
        var game = GameData.builder().matchId("match-1").build();
        var duplicate = mock(com.mongodb.MongoWriteException.class);
        var error = mock(com.mongodb.WriteError.class);
        when(error.getCode()).thenReturn(11000);
        when(duplicate.getError()).thenReturn(error);
        doThrow(duplicate).when(collection).insertOne(any(GameData.class));

        assertThat(repository.saveOnce(game)).isFalse();
    }

    @Test
    void saveOnceRejectsMissingMatchIdWithoutWriting() {
        var game = GameData.builder().matchId("").build();

        assertThat(repository.saveOnce(game)).isFalse();
        org.mockito.Mockito.verify(collection, org.mockito.Mockito.never()).insertOne(any(GameData.class));
    }
}
