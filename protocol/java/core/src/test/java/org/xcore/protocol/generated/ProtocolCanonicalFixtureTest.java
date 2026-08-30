package org.xcore.protocol.generated;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xcore.protocol.generated.messages.chat.ChatMessages;
import org.xcore.protocol.generated.messages.discord.DiscordMessages;
import org.xcore.protocol.generated.messages.maps.MapsMessages;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages;
import org.xcore.protocol.generated.messages.sentinel.SentinelMessages;
import org.xcore.protocol.generated.messages.telemetry.TelemetryMessages;
import org.xcore.protocol.generated.messages.telemetry.TelemetryMessages.MetricsSnapshotV1;
import org.xcore.protocol.generated.routes.ProtocolRoutes;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class ProtocolCanonicalFixtureTest {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();
    private static final String VALID_FIXTURES_ROOT = "fixtures/valid";
    private static final Map<MessageKey, Class<? extends ProtocolPayload>> MESSAGE_PAYLOADS_BY_KEY = buildMessagePayloadIndex();

    @ParameterizedTest(name = "{0}")
    @MethodSource("allValidFixtures")
    void validFixtureParsesAndRoundTripsCanonically(Path fixturePath) throws IOException {
        var fixture = readFixture(fixturePath);
        assertNoLegacyKeys(fixture);
        var model = materializePayload(fixture);

        var canonicalPayload = model.toPayload();
        if (fixture.containsKey("schemaVersion")) {
            assertSchemaIdentity(canonicalPayload, MetricsSnapshotV1.SCHEMA_VERSION);
        } else {
            assertCanonicalIdentity(
                    canonicalPayload,
                    getString(fixture, "messageType"),
                    getInt(fixture, "messageVersion")
            );
        }
        assertEquals(normalizeMap(fixture), normalizeMap(canonicalPayload));
    }

    private static Stream<Path> allValidFixtures() throws IOException {
        var root = resolveFixturePath(VALID_FIXTURES_ROOT);
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted()
                .map(Path::normalize);
    }

    private static ProtocolPayload materializePayload(Map<String, Object> payload) {
        if (payload.containsKey("schemaVersion")) {
            assertSchemaIdentity(payload, MetricsSnapshotV1.SCHEMA_VERSION);
            return instantiateRecord(TelemetryMessages.MetricsSnapshotV1.class, payload);
        }

        var messageType = getString(payload, "messageType");
        var messageVersion = getInt(payload, "messageVersion");
        var route = ProtocolRoutes.routeFor(messageType, messageVersion);
        if (route != null) {
            return instantiateRecord(route.payloadType(), payload);
        }

        var payloadType = MESSAGE_PAYLOADS_BY_KEY.get(new MessageKey(messageType, messageVersion));
        assertNotNull(payloadType, () -> "No generated message payload registered for " + messageType + "@v" + messageVersion);
        return instantiateRecord(payloadType, payload);
    }

    private static Map<String, Object> readFixture(Path fixturePath) throws IOException {
        try (var reader = Files.newBufferedReader(fixturePath)) {
            return GSON.fromJson(reader, MAP_TYPE);
        }
    }

    private static Path resolveFixturePath(String relativePath) {
        var candidates = List.of(
            Path.of(relativePath),
            Path.of("..", "..", relativePath),
            Path.of("..", "..", "..", relativePath)
        );

        for (Path candidate : candidates) {
            var normalized = candidate.normalize();
            if (Files.exists(normalized)) {
                return normalized;
            }
        }

        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            var candidate = current.resolve(relativePath).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        fail("Unable to resolve fixture path: " + relativePath);
        throw new IllegalStateException("Unreachable");
    }

    private static void assertCanonicalIdentity(Map<String, Object> payload, String expectedType, int expectedVersion) {
        assertEquals(expectedType, getString(payload, "messageType"));
        assertEquals(expectedVersion, getInt(payload, "messageVersion"));
    }

    private static void assertSchemaIdentity(Map<String, Object> payload, String expectedSchemaVersion) {
        assertEquals(expectedSchemaVersion, getString(payload, "schemaVersion"));
        assertFalse(payload.containsKey("messageType"));
        assertFalse(payload.containsKey("messageVersion"));
    }

    private static void assertKeySet(Map<String, Object> payload, Set<String> expectedKeys) {
        assertEquals(expectedKeys, payload.keySet());
    }

    @SuppressWarnings("unchecked")
    private static void assertNoLegacyKeys(Map<String, Object> payload) {
        var legacyKeys = Set.of(
            "player_uuid",
            "player_name",
            "player_pid",
            "discord_id",
            "discord_username",
            "actor_name",
            "actor_discord_id",
            "actor_type",
            "server_name",
            "max_players",
            "requested_at",
            "occurred_at"
        );

        for (var entry : payload.entrySet()) {
            var key = entry.getKey();
            assertFalse(key.contains("_"), "fixture contains legacy snake_case key: " + key);
            assertFalse(legacyKeys.contains(key), "fixture contains legacy key: " + key);

            var value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                assertNoLegacyKeys((Map<String, Object>) nestedMap);
            } else if (value instanceof List<?> nestedList) {
                for (Object item : nestedList) {
                    if (item instanceof Map<?, ?> listMap) {
                        assertNoLegacyKeys((Map<String, Object>) listMap);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends ProtocolPayload> T instantiateRecord(Class<T> type, Map<String, Object> payload) {
        try {
            var components = type.getRecordComponents();
            var parameterTypes = new Class<?>[components.length];
            var arguments = new Object[components.length];
            for (int index = 0; index < components.length; index++) {
                RecordComponent component = components[index];
                parameterTypes[index] = component.getType();
                arguments[index] = convertValue(
                        payload.get(component.getName()),
                        component.getType(),
                        component.getGenericType(),
                        component.getName()
                );
            }
            Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(arguments);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException exception) {
            throw new AssertionError("Unable to instantiate generated payload: " + type.getName(), exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Generated payload constructor rejected canonical fixture: " + type.getName(), exception.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    private static Object convertValue(Object value, Class<?> rawType, Type genericType, String fieldName) {
        if (value == null) {
            if (rawType.isPrimitive()) {
                fail("Missing primitive value for field: " + fieldName);
            }
            return null;
        }

        if (rawType == String.class) {
            assertInstanceOf(String.class, value, () -> "Expected string value for field '" + fieldName + "'");
            return value;
        }
        if (rawType == int.class || rawType == Integer.class) {
            assertInstanceOf(Number.class, value, () -> "Expected numeric value for field '" + fieldName + "'");
            return ((Number) value).intValue();
        }
        if (rawType == long.class || rawType == Long.class) {
            assertInstanceOf(Number.class, value, () -> "Expected numeric value for field '" + fieldName + "'");
            return ((Number) value).longValue();
        }
        if (rawType == double.class || rawType == Double.class) {
            assertInstanceOf(Number.class, value, () -> "Expected numeric value for field '" + fieldName + "'");
            return ((Number) value).doubleValue();
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            assertInstanceOf(Boolean.class, value, () -> "Expected boolean value for field '" + fieldName + "'");
            return value;
        }
        if (Map.class.isAssignableFrom(rawType)) {
            assertInstanceOf(Map.class, value, () -> "Expected object value for field '" + fieldName + "'");
            return normalizeMap((Map<String, Object>) value);
        }
        if (List.class.isAssignableFrom(rawType)) {
            assertInstanceOf(List.class, value, () -> "Expected list value for field '" + fieldName + "'");
            Type itemType = Object.class;
            if (genericType instanceof ParameterizedType parameterizedType) {
                itemType = parameterizedType.getActualTypeArguments()[0];
            }
            Type resolvedItemType = itemType;
            Class<?> itemClass = resolvedItemType instanceof Class<?> candidate ? candidate : Object.class;
            return ((List<Object>) value).stream()
                    .map(item -> convertValue(item, itemClass, resolvedItemType, fieldName + "[]"))
                    .toList();
        }
        if (rawType.isEnum()) {
            assertInstanceOf(String.class, value, () -> "Expected enum string value for field '" + fieldName + "'");
            try {
                return rawType.getMethod("fromValue", String.class).invoke(null, value);
            } catch (NoSuchMethodException | IllegalAccessException exception) {
                throw new AssertionError("Enum does not expose fromValue(String): " + rawType.getName(), exception);
            } catch (InvocationTargetException exception) {
                throw new AssertionError("Enum rejected canonical value for field '" + fieldName + "'", exception.getCause());
            }
        }
        if (rawType.isRecord()) {
            assertInstanceOf(Map.class, value, () -> "Expected object value for field '" + fieldName + "'");
            return instantiateRecord((Class<? extends ProtocolPayload>) rawType, (Map<String, Object>) value);
        }

        throw new AssertionError("Unsupported generated field type for canonical fixture materialization: " + rawType.getName());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        assertNotNull(value, () -> "Expected map value for key '" + key + "'");
        assertInstanceOf(Map.class, value, () -> "Expected map value for key '" + key + "'");
        return (Map<String, Object>) value;
    }

    private static String getString(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        assertInstanceOf(String.class, value, () -> "Expected string value for key '" + key + "'");
        return (String) value;
    }

    private static String getOptionalString(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (value == null) {
            return null;
        }
        assertInstanceOf(String.class, value, () -> "Expected string value for key '" + key + "'");
        return (String) value;
    }

    private static boolean getBoolean(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        assertInstanceOf(Boolean.class, value, () -> "Expected boolean value for key '" + key + "'");
        return (Boolean) value;
    }

    private static int getInt(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        assertInstanceOf(Number.class, value, () -> "Expected numeric value for key '" + key + "'");
        return ((Number) value).intValue();
    }

    private static Integer getOptionalInt(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (value == null) {
            return null;
        }
        assertInstanceOf(Number.class, value, () -> "Expected numeric value for key '" + key + "'");
        return ((Number) value).intValue();
    }

    private static long getLong(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        assertInstanceOf(Number.class, value, () -> "Expected numeric value for key '" + key + "'");
        return ((Number) value).longValue();
    }

    private static Long getOptionalLong(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (value == null) {
            return null;
        }
        assertInstanceOf(Number.class, value, () -> "Expected numeric value for key '" + key + "'");
        return ((Number) value).longValue();
    }

    private static Double getOptionalDouble(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (value == null) {
            return null;
        }
        assertInstanceOf(Number.class, value, () -> "Expected numeric value for key '" + key + "'");
        return ((Number) value).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static List<Double> getOptionalDoubleList(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (value == null) {
            return null;
        }
        assertInstanceOf(List.class, value, () -> "Expected list value for key '" + key + "'");
        return ((List<Object>) value).stream()
                .map(item -> {
                    assertInstanceOf(Number.class, item, () -> "Expected numeric list item for key '" + key + "'");
                    return ((Number) item).doubleValue();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> getOptionalIntList(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (value == null) {
            return null;
        }
        assertInstanceOf(List.class, value, () -> "Expected list value for key '" + key + "'");
        return ((List<Object>) value).stream()
                .map(item -> {
                    assertInstanceOf(Number.class, item, () -> "Expected numeric list item for key '" + key + "'");
                    return ((Number) item).intValue();
                })
                .toList();
    }

    private static Map<String, Object> normalizeMap(Map<String, Object> payload) {
        return GSON.fromJson(GSON.toJson(payload), MAP_TYPE);
    }

    private static Map<MessageKey, Class<? extends ProtocolPayload>> buildMessagePayloadIndex() {
        Map<MessageKey, Class<? extends ProtocolPayload>> index = new LinkedHashMap<>();
        indexMessagePayloads(index, MapsMessages.class);
        indexMessagePayloads(index, ChatMessages.class);
        indexMessagePayloads(index, DiscordMessages.class);
        indexMessagePayloads(index, ModerationMessages.class);
        indexMessagePayloads(index, SentinelMessages.class);
        return Map.copyOf(index);
    }

    @SuppressWarnings("unchecked")
    private static void indexMessagePayloads(Map<MessageKey, Class<? extends ProtocolPayload>> index, Class<?> containerType) {
        for (Class<?> nestedType : containerType.getDeclaredClasses()) {
            if (!ProtocolPayload.class.isAssignableFrom(nestedType)) {
                continue;
            }
            try {
                var messageType = (String) nestedType.getField("MESSAGE_TYPE").get(null);
                var messageVersion = nestedType.getField("MESSAGE_VERSION").getInt(null);
                index.put(new MessageKey(messageType, messageVersion), (Class<? extends ProtocolPayload>) nestedType);
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                throw new AssertionError("Unable to inspect generated message payload: " + nestedType.getName(), exception);
            }
        }
    }

    private record MessageKey(String messageType, int messageVersion) {
    }
}
