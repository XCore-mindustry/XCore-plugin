package org.xcore.plugin.database.repository;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import org.bson.*;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.MapDiscordUpload;
import org.xcore.plugin.model.MapUploadRecord;
import org.xcore.plugin.map.domain.MapContentHash;
import org.xcore.plugin.map.domain.MapSlug;
import org.xcore.plugin.service.map.MapIdentityCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class MapDiscordUploadPersistenceTest {
    private final MongoDatabase database = mock(MongoDatabase.class);
    private final MongoCollection<MapUploadRecord> current = mock(MongoCollection.class);
    private final MongoCollection<MapUploadRecord> history = mock(MongoCollection.class);
    private final MongoCollection<MapData> statistics = mock(MongoCollection.class);
    private final FindIterable<MapUploadRecord> query = mock(FindIterable.class);
    private final FindIterable<MapData> oldQuery = mock(FindIterable.class);
    private final TomlXcoreConfig config = new TomlXcoreConfig();
    private final TomlSecretsConfig secrets = new TomlSecretsConfig();
    private MapUploadRepository repository;
    private final MapDiscordUpload upload = new MapDiscordUpload("123456789012345678", "Reviewer", 1791806400000L,
            "234567890123456789", "Creator", "reviewer", "creator");
    private final String hash = "a".repeat(64);

    @BeforeEach
    void setup() {
        config.server.name = "pvp";
        when(database.getCollection("map_uploads", MapUploadRecord.class)).thenReturn(current);
        when(database.getCollection("map_upload_history", MapUploadRecord.class)).thenReturn(history);
        when(database.getCollection("maps", MapData.class)).thenReturn(statistics);
        when(current.find(any(Bson.class))).thenReturn(query);
        when(statistics.find(any(Bson.class))).thenReturn(oldQuery);
        when(oldQuery.sort(any(Bson.class))).thenReturn(oldQuery);
        when(current.replaceOne(any(Bson.class), any(MapUploadRecord.class), any())).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        repository = new MapUploadRepository(database, InlineStorageExecutor.create(), config, secrets);
        clearInvocations(current, history, statistics);
    }

    private static MapIdentityCatalog.Entry entry(String fileName, String hash) {
        return new MapIdentityCatalog.Entry(fileName, "Arena", "Author", 1, 1,
                MapSlug.of("Author", "Arena"), MapContentHash.fromHex(hash));
    }

    @Test
    void attributionRepositoryDoesNotModifyStatisticsAndKeepsAuditHistory() {
        assertThat(repository.save("arena_v2.msav", hash, upload)).isTrue();
        verify(current).replaceOne(any(), any(MapUploadRecord.class), any());
        var audit = ArgumentCaptor.forClass(MapUploadRecord.class);
        verify(history).insertOne(audit.capture());
        assertThat(audit.getValue().upload).isEqualTo(upload);
        assertThat(audit.getValue().server).isEqualTo("pvp");
        verifyNoInteractions(statistics);
    }

    @Test
    void scopeIsExactAndDifferentServersCannotOverwriteEachOthersUpload() {
        var keys = ArgumentCaptor.forClass(Bson.class);
        repository.save("Arena.msav", hash, upload);
        config.server.name = "survival";
        repository.save("Arena.msav", hash, null);
        repository.save("arena.msav", hash, upload);
        verify(current, times(3)).replaceOne(keys.capture(), any(MapUploadRecord.class), any());
        var ids = keys.getAllValues().stream().map(b -> b.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).getString("_id").getValue()).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void onlyMatchingInstalledBytesAreAttributedAndRemovalBlocksLegacyFallback() {
        var record = new MapUploadRecord();
        record.contentHash = hash;
        record.upload = upload;
        record.fileName = "arena.msav";
        doAnswer(call -> { ((java.util.List<MapUploadRecord>) call.getArgument(0)).add(record); return call.getArgument(0); })
                .when(query).into(any(java.util.List.class));
        assertThat(repository.snapshotAsync(List.of(entry("arena.msav", hash))).toCompletableFuture().join()).containsEntry("arena.msav", upload);
        assertThat(repository.snapshotAsync(List.of(entry("arena.msav", "b".repeat(64)))).toCompletableFuture().join()).isEmpty();
        record.contentHash = null;
        record.upload = null;
        assertThat(repository.snapshotAsync(List.of(entry("arena.msav", hash))).toCompletableFuture().join()).isEmpty();
        verifyNoInteractions(statistics);
        assertThat(repository.remove("arena.msav")).isTrue();
    }

    @Test
    void legacyTransitionRequiresExactFilenameAndHashRegardlessOfStatisticsMode() {
        var old = new MapData("Arena", "arena.msav", "File author", "pvp");
        old.discordUpload = upload;
        old.contentHash = hash;
        doAnswer(call -> { ((java.util.List<MapData>) call.getArgument(0)).add(old); return call.getArgument(0); })
                .when(oldQuery).into(any(java.util.List.class));
        assertThat(repository.snapshotAsync(List.of(entry("arena.msav", hash))).toCompletableFuture().join()).containsEntry("arena.msav", upload);
        var filter = ArgumentCaptor.forClass(Bson.class);
        verify(statistics).find(filter.capture());
        var json = filter.getValue().toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson();
        assertThat(json).contains("arena.msav", hash).doesNotContain("regularExpression", "game_mode");
    }

    @Test
    void badDocumentDoesNotRemoveOtherMapsFromSnapshot() throws Exception {
        var corrupt = new MapUploadRecord();
        corrupt.contentHash = hash;
        corrupt.upload = new MapDiscordUpload("", "Broken", -1, null, null);
        var valid = new MapUploadRecord();
        valid.contentHash = hash;
        valid.upload = upload;
        corrupt.fileName = "bad.msav";
        valid.fileName = "good.msav";
        doAnswer(call -> {
            ((java.util.List<MapUploadRecord>) call.getArgument(0)).addAll(List.of(corrupt, valid));
            return call.getArgument(0);
        }).when(query).into(any(java.util.List.class));
        var digest = MapContentHash.fromHex(hash);
        var entries = List.of(
                new MapIdentityCatalog.Entry("bad.msav", "Bad", "Author", 1, 1, MapSlug.of("Author", "Bad"), digest),
                new MapIdentityCatalog.Entry("good.msav", "Good", "Author", 1, 1, MapSlug.of("Author", "Good"), digest));
        var result = repository.snapshotAsync(entries).toCompletableFuture().join();
        assertThat(result).containsOnlyKeys("good.msav");
    }

    @Test
    void pojoCodecRoundTripsNestedAttributionHandlesAndOptionalCreator() {
        var codecs = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        var codec = codecs.get(MapUploadRecord.class);
        var record = new MapUploadRecord();
        record.id = "pvp:arena";
        record.server = "pvp";
        record.fileName = "arena.msav";
        record.contentHash = hash;
        record.upload = upload;
        var document = new BsonDocument();
        codec.encode(new BsonDocumentWriter(document), record, EncoderContext.builder().build());
        assertThat(codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build())).isEqualTo(record);
        record.upload.creatorDiscordId = null;
        record.upload.creatorName = null;
        record.upload.creatorHandle = null;
        document = new BsonDocument();
        codec.encode(new BsonDocumentWriter(document), record, EncoderContext.builder().build());
        assertThat(codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build()).upload.toProtocol().creator()).isNull();
    }

    @Test
    void readOnlyNeverMutatesCurrentOrHistoryAndHistoryFailureDoesNotHideSuccessfulSave() {
        secrets.database.readOnly = true;
        assertThat(repository.save("arena.msav", hash, upload)).isFalse();
        verifyNoInteractions(current, history, statistics);
        secrets.database.readOnly = false;
        doThrow(new IllegalStateException("audit offline")).when(history).insertOne(any(MapUploadRecord.class));
        assertThat(repository.save("arena.msav", hash, upload)).isTrue();
    }

    @Test
    void existingStatisticsDoNotTriggerAttributionLookupOnGameThread() {
        var data = new MapData("Arena", "arena.msav", "Author", "pvp");
        data.like = 42;
        when(oldQuery.first()).thenReturn(data);
        var statsRepository = new MapDataRepository(database, secrets);
        clearInvocations(statistics);
        assertThat(statsRepository.findOrCreate("Arena", "arena.msav", "Author", "pvp").like).isEqualTo(42);
        verify(statistics, times(1)).find(any(Bson.class));
    }
}
