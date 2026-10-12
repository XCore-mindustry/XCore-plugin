package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.MapDiscordUpload;
import org.xcore.plugin.model.MapUploadRecord;
import org.xcore.plugin.service.map.MapIdentityCatalog;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletionStage;

import static com.mongodb.client.model.Filters.*;

/** All Mongo access runs on storage workers. Server + filename identifies the installation; hash proves its bytes. */
@Singleton
public class MapUploadRepository {
    private final MongoCollection<MapUploadRecord> current;
    private final MongoCollection<MapUploadRecord> history;
    private final MongoCollection<MapData> legacy;
    private final StorageExecutor worker;
    private final TomlXcoreConfig config;
    private final TomlSecretsConfig secrets;

    @Inject
    public MapUploadRepository(MongoDatabase database, StorageExecutor worker, TomlXcoreConfig config, TomlSecretsConfig secrets) {
        this.current = database.getCollection("map_uploads", MapUploadRecord.class);
        this.history = database.getCollection("map_upload_history", MapUploadRecord.class);
        this.legacy = database.getCollection("maps", MapData.class);
        this.worker = worker;
        this.config = config;
        this.secrets = secrets;
        current.createIndex(new Document("server", 1).append("fileName", 1));
        history.createIndex(new Document("server", 1).append("fileName", 1).append("recordedAt", -1));
    }

    private String key(String fileName) {
        return config.server.name.length() + ":" + config.server.name + ":" + fileName;
    }

    public boolean save(String fileName, String hash, MapDiscordUpload upload) {
        if (secrets.database.readOnly) return false;
        var record = new MapUploadRecord();
        record.id = key(fileName);
        record.server = config.server.name;
        record.fileName = fileName;
        record.contentHash = hash;
        record.upload = upload;
        record.recordedAt = System.currentTimeMillis();
        var result = current.replaceOne(eq("_id", record.id), record, new ReplaceOptions().upsert(true));
        if (!result.wasAcknowledged()) return false;
        // Audit is advisory; a failed history insert must not claim the current metadata was lost.
        try {
            history.insertOne(new MapUploadRecord(new org.bson.types.ObjectId().toHexString(), record.server,
                    fileName, hash, upload, record.recordedAt));
        } catch (RuntimeException failure) {
            org.xcore.plugin.common.PLog.warn("Map upload history failed for @: @", fileName, failure.toString());
        }
        return true;
    }

    /** A tombstone suppresses legacy attribution even if the same bytes are installed manually later. */
    public boolean remove(String fileName) { return save(fileName, null, null); }

    public CompletionStage<Map<String, MapDiscordUpload>> snapshotAsync(List<MapIdentityCatalog.Entry> entries) {
        var captured = List.copyOf(entries);
        return worker.supply(() -> {
            if (captured.isEmpty()) return Map.of();
            var files = captured.stream().map(MapIdentityCatalog.Entry::fileName).toList();
            var rows = new java.util.ArrayList<MapUploadRecord>();
            current.find(and(eq("server", config.server.name), in("fileName", files))).into(rows);
            var installed = new LinkedHashMap<String, MapUploadRecord>();
            for (var row : rows) installed.put(row.fileName, row);
            var missing = files.stream().filter(file -> !installed.containsKey(file)).toList();
            var oldRows = new java.util.ArrayList<MapData>();
            if (!missing.isEmpty()) legacy.find(and(in("file_name", missing),
                    in("content_hash", captured.stream().map(entry -> entry.hash().asHex()).toList()),
                    exists("discord_upload", true))).into(oldRows);
            var result = new LinkedHashMap<String, MapDiscordUpload>();
            for (var entry : captured) {
                try {
                    String hash = entry.hash().asHex();
                    var row = installed.get(entry.fileName());
                    MapDiscordUpload upload;
                    if (row != null) upload = hash.equals(row.contentHash) ? row.upload : null;
                    else upload = oldRows.stream().filter(old -> entry.fileName().equals(old.fileName)
                                    && hash.equals(old.contentHash) && old.discordUpload != null)
                            .map(old -> old.discordUpload).max(java.util.Comparator.comparingLong(old -> old.uploadedAt)).orElse(null);
                    if (upload != null) {
                        upload.toProtocol();
                        result.put(entry.fileName(), upload);
                    }
                } catch (RuntimeException failure) {
                    org.xcore.plugin.common.PLog.warn("Map attribution lookup failed for @: @", entry.fileName(), failure.toString());
                }
            }
            return Map.copyOf(result);
        });
    }
}
