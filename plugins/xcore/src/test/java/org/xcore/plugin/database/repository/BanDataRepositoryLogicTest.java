package org.xcore.plugin.database.repository;

import com.mongodb.MongoClientSettings;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class BanDataRepositoryLogicTest {

    @Test
    @DisplayName("identifierFilter matches uuid or ip when both are present")
    void identifierFilterMatchesUuidOrIpWhenBothPresent() throws Exception {
        var filter = identifierFilter("uuid-1", "1.2.3.4");
        var json = toJson(filter);

        assertThat(json).contains("\"$or\"");
        assertThat(json).contains("\"uuid\": \"uuid-1\"");
        assertThat(json).contains("\"ip\": \"1.2.3.4\"");
    }

    @Test
    @DisplayName("identifierFilter matches uuid only when ip is missing")
    void identifierFilterMatchesUuidOnlyWhenIpMissing() throws Exception {
        var filter = identifierFilter("uuid-1", null);

        assertThat(toJson(filter)).isEqualTo("{\"uuid\": \"uuid-1\"}");
    }

    @Test
    @DisplayName("identifierFilter matches ip only when uuid is missing")
    void identifierFilterMatchesIpOnlyWhenUuidMissing() throws Exception {
        var filter = identifierFilter(null, "1.2.3.4");

        assertThat(toJson(filter)).isEqualTo("{\"ip\": \"1.2.3.4\"}");
    }

    @Test
    @DisplayName("identifierFilter returns null when both identifiers are missing")
    void identifierFilterReturnsNullWhenBothMissing() throws Exception {
        assertThat(identifierFilter(null, null)).isNull();
    }

    private static Object identifierFilter(String uuid, String ip) throws Exception {
        Method method = BanDataRepository.class.getDeclaredMethod("identifierFilter", String.class, String.class);
        method.setAccessible(true);
        return method.invoke(null, uuid, ip);
    }

    private static String toJson(Object filter) {
        return ((org.bson.conversions.Bson) filter)
                .toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry())
                .toJson();
    }
}
