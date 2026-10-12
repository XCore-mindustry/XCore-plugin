package org.xcore.plugin.database.repository;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.MapDiscordUpload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MapDiscordUploadPersistenceTest {
    @Test
    @SuppressWarnings("unchecked")
    void submissionWritesUseExactNamesAndDoNotMergeCaseOnlyFiles() {
        var database = mock(MongoDatabase.class);
        MongoCollection<MapData> collection = mock(MongoCollection.class);
        when(database.getCollection("maps", MapData.class)).thenReturn(collection);
        FindIterable<MapData> exactQuery = mock(FindIterable.class);
        when(collection.find(any(Bson.class))).thenReturn(exactQuery);
        when(collection.updateMany(any(Bson.class), any(Bson.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MapDataRepository(database, new TomlSecretsConfig());
        var upload = new MapDiscordUpload("123", "Reviewer", 1791806400000L, null, null);
        assertThat(repository.recordDiscordUpload("Arena", "arena.msav", "Author", "pvp", "new hash", upload)).isTrue();
        var lookup = ArgumentCaptor.forClass(Bson.class);
        verify(collection).find(lookup.capture());
        String filter = lookup.getValue().toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson();
        assertThat(filter).contains("arena.msav").doesNotContain("regularExpression");
        var inserted = ArgumentCaptor.forClass(MapData.class);
        verify(collection).insertOne(inserted.capture());
        assertThat(inserted.getValue().fileName).isEqualTo("arena.msav");
        assertThat(inserted.getValue().discordUpload).isEqualTo(upload);
        var target = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateMany(target.capture(), any(Bson.class));
        assertThat(target.getValue().toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()))
                .isEqualTo(BsonDocument.parse("{file_name: 'arena.msav'}"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void attributionFromADifferentAliasIsDroppedWithoutChangingStatisticsOrStorage() {
        var database = mock(MongoDatabase.class);
        MongoCollection<MapData> collection = mock(MongoCollection.class);
        when(database.getCollection("maps", MapData.class)).thenReturn(collection);
        var repository = new MapDataRepository(database, new TomlSecretsConfig());
        clearInvocations(collection);
        var alias = new MapData("Arena", "other.msav", "Author", "pvp");
        alias.like = 42;
        alias.discordUpload = new MapDiscordUpload("123", "Other Reviewer", 1791806400000L, null, null);
        var result = repository.enrichDiscordUploadAsync(alias, "arena.msav").toCompletableFuture().join();
        assertThat(result.discordUpload).isNull();
        assertThat(result.like).isEqualTo(42);
        assertThat(result.fileName).isEqualTo("other.msav");
        verifyNoInteractions(collection);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anotherGameModeInheritsAttributionWithoutUsingThatModesStatistics() {
        var database = mock(MongoDatabase.class);
        MongoCollection<MapData> collection = mock(MongoCollection.class);
        when(database.getCollection("maps", MapData.class)).thenReturn(collection);
        FindIterable<MapData> modeQuery = mock(FindIterable.class);
        FindIterable<MapData> sourceQuery = mock(FindIterable.class);
        when(collection.find(any(Bson.class))).thenReturn(modeQuery, sourceQuery);
        when(sourceQuery.sort(any(Bson.class))).thenReturn(sourceQuery);
        var survival = new MapData("Arena", "arena.msav", "File author", "survival");
        survival.like = 2;
        var pvp = new MapData("Arena", "arena.msav", "File author", "pvp");
        pvp.like = 99;
        pvp.discordUpload = new MapDiscordUpload("123456789012345678", "Reviewer", 1791806400000L, null, null);
        when(modeQuery.first()).thenReturn(survival);
        when(sourceQuery.first()).thenReturn(pvp);
        var repository = new MapDataRepository(database, new TomlSecretsConfig());
        var data = repository.findOrCreate("Arena", "arena.msav", "File author", "survival");
        assertThat(data.discordUpload).isEqualTo(pvp.discordUpload);
        assertThat(data.like).isEqualTo(2);
        assertThat(data.gameMode).isEqualTo("survival");
        verify(collection, never()).insertOne(any(MapData.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reuploadUpdatesOnlyMetadataAndUnknownSourceClearsAttribution() {
        var database = mock(MongoDatabase.class);
        MongoCollection<MapData> collection = mock(MongoCollection.class);
        when(database.getCollection("maps", MapData.class)).thenReturn(collection);
        FindIterable<MapData> find = mock(FindIterable.class);
        when(collection.find(any(Bson.class))).thenReturn(find);
        var data = new MapData("Arena", "arena.msav", "File author", "pvp");
        data.id = new ObjectId();
        data.like = 42;
        when(find.first()).thenReturn(data);
        when(collection.updateMany(any(Bson.class), any(Bson.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MapDataRepository(database, new TomlSecretsConfig());
        var upload = new MapDiscordUpload("123456789012345678", "Reviewer", 1791806400000L, "234567890123456789", "Creator");
        assertThat(repository.recordDiscordUpload("Arena", "arena.msav", "File author", "pvp", "a".repeat(64), upload)).isTrue();
        var update = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateMany(any(Bson.class), update.capture());
        var codecs = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        BsonDocument bson = update.getValue().toBsonDocument(Document.class, codecs);
        assertThat(bson.getDocument("$set").getDocument("discord_upload").getString("creatorName").getValue()).isEqualTo("Creator");
        assertThat(bson.toJson()).doesNotContain("like", "reputation", "play_count");
        verify(collection, never()).replaceOne(any(Bson.class), any(MapData.class), any());
        clearInvocations(collection);
        assertThat(repository.recordDiscordUpload("Arena", "arena.msav", "File author", "pvp", "b".repeat(64), null)).isTrue();
        verify(collection).updateMany(any(Bson.class), update.capture());
        assertThat(update.getValue().toBsonDocument(Document.class, codecs).getDocument("$unset")).containsKey("discord_upload");
        assertThat(data.like).isEqualTo(42);
    }
}
