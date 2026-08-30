package org.xcore.plugin.integration.playerstorage;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.not;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Indexes.ascending;
import static com.mongodb.client.model.Indexes.descending;

@Singleton
public class PluginPlayerStoreFactory {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
    private final MongoDatabase database;
    private final TomlSecretsConfig config;
    private final ConcurrentMap<String, PlayerStoreSchema> schemas = new ConcurrentHashMap<>();

    public PluginPlayerStoreFactory(MongoDatabase database, TomlSecretsConfig config) {
        this.database = database;
        this.config = config;
    }

    public PluginPlayerStore create(String pluginId, PlayerStoreSchema schema) {
        if (pluginId == null || !ID.matcher(pluginId).matches()) {
            throw new IllegalArgumentException("Plugin ID must match [a-z0-9][a-z0-9_-]{0,31}");
        }
        Objects.requireNonNull(schema, "schema");
        validateSchema(schema);

        var old = schemas.putIfAbsent(pluginId, schema);
        if (old != null && (!old.fields().equals(schema.fields()) || old.version() != schema.version())) {
            throw new IllegalArgumentException("Store already bound with incompatible schema");
        }

        MongoCollection<Document> collection = database.getCollection(collectionName(pluginId), Document.class);
        // Read-only stores must not perform index-management writes during creation.
        if (!config.database.readOnly) {
            collection.createIndex(ascending("player_uuid"), new com.mongodb.client.model.IndexOptions().unique(true));
            for (var e : schema.fields().entrySet()) {
                if (e.getValue().indexed()) {
                    collection.createIndex(descending("data." + e.getKey()));
                }
            }
        }
        return new MongoPlayerStore(collection, schema, config);
    }

    public static String collectionName(String pluginId) {
        return "xcore_plugin_" + pluginId + "_players";
    }

    private static void validateSchema(PlayerStoreSchema s) {
        if (s.version() < 1 || s.fields().isEmpty()) {
            throw new IllegalArgumentException("Invalid schema");
        }
        for (var entry : s.fields().entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,31}")
                    || entry.getValue() == null || entry.getValue().type() == null) {
                throw new IllegalArgumentException("Invalid schema field");
            }
        }
    }

    static final class MongoPlayerStore implements PluginPlayerStore {
        private final MongoCollection<Document> c;
        private final PlayerStoreSchema schema;
        private final TomlSecretsConfig config;

        MongoPlayerStore(MongoCollection<Document> c, PlayerStoreSchema schema, TomlSecretsConfig config) {
            this.c = c;
            this.schema = schema;
            this.config = config;
        }

        private void uuid(String u) {
            if (u == null || u.isBlank()) {
                throw new IllegalArgumentException("UUID must not be blank");
            }
        }

        private PlayerStoreSchema.Field field(String n) {
            var f = schema.fields().get(n);
            if (f == null) {
                throw new IllegalArgumentException("Undeclared field: " + n);
            }
            return f;
        }

        private Object value(String n, Object v) {
            var f = field(n);
            if (v == null) {
                throw new IllegalArgumentException("Null values are not supported");
            }
            boolean ok = switch (f.type()) {
                case INT -> v instanceof Integer;
                case LONG -> v instanceof Long;
                case DOUBLE -> v instanceof Double || v instanceof Float;
                case BOOLEAN -> v instanceof Boolean;
                case STRING -> v instanceof String;
            };
            if (!ok) {
                throw new IllegalArgumentException("Invalid value for " + n);
            }
            return v instanceof Float ? ((Float) v).doubleValue() : v;
        }

        private void writable() {
            if (config.database.readOnly) {
                throw new IllegalStateException("Database is read-only");
            }
        }

        public Optional<PlayerRecord> find(String u) {
            uuid(u);
            return Optional.ofNullable(c.find(eq("player_uuid", u)).first()).map(this::record);
        }

        public boolean exists(String u) {
            uuid(u);
            return c.countDocuments(
                    eq("player_uuid", u),
                    new com.mongodb.client.model.CountOptions().limit(1)
            ) > 0;
        }

        public boolean set(String u, String n, Object v) {
            writable();
            uuid(u);
            v = value(n, v);
            var result = c.updateOne(
                    eq("player_uuid", u),
                    updatePlan(u, "data." + n, v, null),
                    new com.mongodb.client.model.UpdateOptions().upsert(true)
            );
            return changed(result);
        }

        public boolean remove(String u, String n) {
            writable();
            uuid(u);
            field(n);
            var result = c.updateOne(
                    eq("player_uuid", u),
                    updatePlan(u, "data." + n, null, Boolean.TRUE),
                    new com.mongodb.client.model.UpdateOptions()
            );
            return changed(result);
        }

        public boolean applyOnce(String operationId, String u, Map<String, Number> increments,
                                 Map<String, Object> values) {
            writable();
            if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("Operation ID must not be blank");
            uuid(u);
            if (increments == null || values == null) throw new IllegalArgumentException("Mutation maps must not be null");

            List<Document> set = new ArrayList<>();
            Document fields = new Document("player_uuid", literal(u))
                    .append("schema_version", new Document("$ifNull", List.of("$schema_version", schema.version())))
                    .append("revision", new Document("$add", List.of(new Document("$ifNull", List.of("$revision", 0)), 1)))
                    .append("updated_at", "$$NOW");
            for (var entry : increments.entrySet()) {
                var field = field(entry.getKey());
                Number delta = entry.getValue();
                if (delta == null || field.type() == FieldType.BOOLEAN || field.type() == FieldType.STRING
                        || (field.type() == FieldType.INT && !(delta instanceof Integer))
                        || (field.type() == FieldType.LONG && !(delta instanceof Long))
                        || (field.type() == FieldType.DOUBLE && !(delta instanceof Double || delta instanceof Float))) {
                    throw new IllegalArgumentException("Invalid increment");
                }
                fields.append("data." + entry.getKey(), new Document("$add",
                        List.of(new Document("$ifNull", List.of("$data." + entry.getKey(), 0)), valueNumber(field.type(), delta))));
            }
            for (var entry : values.entrySet()) {
                field(entry.getKey());
                Object value = value(entry.getKey(), entry.getValue());
                fields.append("data." + entry.getKey(), value instanceof String ? literal(value) : value);
            }
            fields.append("applied_operations", new Document("$concatArrays", List.of(
                    new Document("$ifNull", List.of("$applied_operations", List.of())), List.of(operationId))));
            var result = c.updateOne(and(eq("player_uuid", u), not(eq("applied_operations", operationId))),
                    List.of(new Document("$set", fields)));
            return changed(result);
        }

        public boolean increment(String u, String n, Number d) {
            writable();
            uuid(u);
            var f = field(n);
            if (d == null
                    || f.type() == FieldType.BOOLEAN
                    || f.type() == FieldType.STRING
                    || (f.type() == FieldType.INT && !(d instanceof Integer))
                    || (f.type() == FieldType.LONG && !(d instanceof Long))
                    || (f.type() == FieldType.DOUBLE && !(d instanceof Double || d instanceof Float))) {
                throw new IllegalArgumentException("Invalid increment");
            }
            var result = c.updateOne(
                    eq("player_uuid", u),
                    updatePlan(u, "data." + n, valueNumber(f.type(), d), Boolean.FALSE),
                    new com.mongodb.client.model.UpdateOptions().upsert(true)
            );
            return changed(result);
        }

        private boolean changed(com.mongodb.client.result.UpdateResult result) {
            return result.wasAcknowledged()
                    && (result.getMatchedCount() > 0 || result.getUpsertedId() != null);
        }

        private Number valueNumber(FieldType t, Number n) {
            return t == FieldType.DOUBLE ? n.doubleValue() : n;
        }

        private List<Bson> updatePlan(String u, String path, Object value, Boolean remove) {
            Document fields = new Document("player_uuid", new Document("$ifNull", List.of("$player_uuid", literal(u))))
                    .append("schema_version", new Document("$ifNull", List.of("$schema_version", schema.version())))
                    .append("revision", new Document("$add", List.of(new Document("$ifNull", List.of("$revision", 0)), 1)))
                    .append("updated_at", "$$NOW");
            if (Boolean.TRUE.equals(remove)) {
                return List.of(new Document("$set", fields), new Document("$unset", path));
            }
            if (Boolean.FALSE.equals(remove)) {
                fields.append(path, new Document("$add", List.of(new Document("$ifNull", List.of("$" + path, 0)), value)));
            } else {
                fields.append(path, value instanceof String ? literal(value) : value);
            }
            return List.of(new Document("$set", fields));
        }

        private Document literal(Object value) {
            return new Document("$literal", value);
        }

        public boolean delete(String u) {
            writable();
            uuid(u);
            return c.deleteOne(eq("player_uuid", u)).getDeletedCount() > 0;
        }

        public PlayerPage top(SortField sf, int limit, String cursor) {
            fieldSort(sf);
            if (limit < 1 || limit > 100) {
                throw new IllegalArgumentException("Limit must be 1..100");
            }
            Bson filter = cursor == null || cursor.isBlank()
                    ? com.mongodb.client.model.Filters.exists("data." + sf.field())
                    : after(sf, decode(cursor, sf));
            var docs = c.find(filter)
                    .sort(sf.direction() == SortDirection.ASC
                            ? ascending("data." + sf.field(), "player_uuid")
                            : descending("data." + sf.field(), "player_uuid"))
                    .limit(limit + 1);
            var list = new ArrayList<PlayerRecord>();
            for (var d : docs) {
                list.add(record(d));
            }
            var boundary = pageBoundary(list, limit);
            if (!boundary.hasNext()) {
                return new PlayerPage(boundary.records(), null, false);
            }
            var last = boundary.records().get(boundary.records().size() - 1);
            return new PlayerPage(boundary.records(), encode(
                    last.values().get(sf.field()),
                    last.playerUuid(),
                    schema.fields().get(sf.field()).type(),
                    sf.direction()), true);
        }

        static <T> PageBoundary<T> pageBoundary(List<T> records, int limit) {
            if (records.size() <= limit) {
                return new PageBoundary<>(List.copyOf(records), false);
            }
            return new PageBoundary<>(List.copyOf(records.subList(0, limit)), true);
        }

        record PageBoundary<T>(List<T> records, boolean hasNext) {
        }

        private void fieldSort(SortField s) {
            if (!field(s.field()).indexed()) {
                throw new IllegalArgumentException("Sort field is not indexed");
            }
        }

        public OptionalLong rankOf(String u, SortField sf) {
            fieldSort(sf);
            uuid(u);
            var d = c.find(eq("player_uuid", u)).first();
            if (d == null
                    || d.get("data", Document.class) == null
                    || !d.get("data", Document.class).containsKey(sf.field())) {
                return OptionalLong.empty();
            }
            var v = d.get("data", Document.class).get(sf.field());
            return OptionalLong.of(c.countDocuments(
                    and(com.mongodb.client.model.Filters.exists("data." + sf.field()), before(sf, new Cursor(v, u)))
            ) + 1);
        }

        private Bson after(SortField s, Cursor x) {
            Bson val = s.direction() == SortDirection.ASC
                    ? gt("data." + s.field(), x.value)
                    : lt("data." + s.field(), x.value);
            Bson tie = s.direction() == SortDirection.ASC
                    ? gt("player_uuid", x.uuid)
                    : lt("player_uuid", x.uuid);
            return or(val, and(eq("data." + s.field(), x.value), tie));
        }

        private Bson before(SortField s, Cursor x) {
            Bson val = s.direction() == SortDirection.ASC
                    ? lt("data." + s.field(), x.value)
                    : gt("data." + s.field(), x.value);
            Bson tie = s.direction() == SortDirection.ASC
                    ? lt("player_uuid", x.uuid)
                    : gt("player_uuid", x.uuid);
            return or(val, and(eq("data." + s.field(), x.value), tie));
        }

        private PlayerRecord record(Document d) {
            var data = d.get("data", Document.class);
            Map<String, Object> m = new HashMap<>();
            if (data != null) {
                for (String n : schema.fields().keySet()) {
                    if (data.containsKey(n)) {
                        m.put(n, data.get(n));
                    }
                }
            }
            return new PlayerRecord(
                    d.getString("player_uuid"),
                    m,
                    numberAsLong(d.get("revision"), 0),
                    numberAsInt(d.get("schema_version"), schema.version())
            );
        }

        static long numberAsLong(Object value, long fallback) {
            return value instanceof Number number ? number.longValue() : fallback;
        }

        static int numberAsInt(Object value, int fallback) {
            return value instanceof Number number ? number.intValue() : fallback;
        }

        private record Cursor(Object value, String uuid) {
        }

        private String encode(Object v, String u, FieldType t, SortDirection direction) {
            return CursorCodec.encode(t, v, u, direction);
        }

        private Cursor decode(String x, SortField s) {
            try {
                var decoded = CursorCodec.decode(x);
                if (!decoded.type().equals(schema.fields().get(s.field()).type())
                        || !decoded.direction().equals(s.direction())
                        || decoded.uuid().isBlank()) {
                    throw new IllegalArgumentException();
                }
                return new Cursor(decoded.value(), decoded.uuid());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid cursor", e);
            }
        }

    }

    static final class CursorCodec {
        private CursorCodec() {
        }

        static String encode(FieldType type, Object value, String uuid, SortDirection direction) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(direction, "direction");
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    type.name().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    uuid.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    + "." + direction.name();
        }

        static DecodedCursor decode(String encoded) {
            try {
                String[] parts = encoded.split("\\.", -1);
                if (parts.length != 4) {
                    throw new IllegalArgumentException();
                }
                FieldType type = FieldType.valueOf(text(parts[0]));
                String rawValue = text(parts[1]);
                String uuid = text(parts[2]);
                SortDirection direction = SortDirection.valueOf(parts[3]);
                Object value = switch (type) {
                    case INT -> Integer.valueOf(rawValue);
                    case LONG -> Long.valueOf(rawValue);
                    case DOUBLE -> Double.valueOf(rawValue);
                    case BOOLEAN -> {
                        if (!rawValue.equals("true") && !rawValue.equals("false")) {
                            throw new IllegalArgumentException();
                        }
                        yield Boolean.valueOf(rawValue);
                    }
                    case STRING -> rawValue;
                };
                return new DecodedCursor(type, value, uuid, direction);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid cursor", e);
            }
        }

        private static String text(String value) {
            return new String(java.util.Base64.getUrlDecoder().decode(value),
                    java.nio.charset.StandardCharsets.UTF_8);
        }

        record DecodedCursor(FieldType type, Object value, String uuid, SortDirection direction) {
        }
    }
}
