package org.xcore.plugin.rating.season;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.PlayerPids;

import static org.assertj.core.api.Assertions.assertThat;

class MongoSeasonStorePodiumTest {

    private static SeasonPodiumEntry entry(int pid) {
        return new SeasonPodiumEntry(1, "uuid", pid, "Name", 1500, "SILICON", 12, 6, "", "");
    }

    @Test
    @DisplayName("negative, zero and missing pids survive a round trip")
    void podiumPidsRoundTrip() {
        for (int pid : new int[]{-1, -42, 0, 7, PlayerPids.NONE}) {
            assertThat(MongoSeasonStore.podiumEntry(MongoSeasonStore.document(entry(pid)))).isEqualTo(entry(pid));
        }
    }

    @Test
    @DisplayName("a missing pid is not stored")
    void missingPidIsNotStored() {
        Document document = MongoSeasonStore.document(entry(PlayerPids.NONE));

        assertThat(document.containsKey("pid")).isFalse();
        assertThat(document.getBoolean(MongoSeasonStore.SIGNED_PID)).isTrue();
    }

    @Test
    @DisplayName("archives written before signed pids read -1 as no pid and keep other pids")
    void legacyPlaceholderReadsAsMissing() {
        Document legacy = MongoSeasonStore.document(entry(-1));
        legacy.remove(MongoSeasonStore.SIGNED_PID);
        Document legacyReal = MongoSeasonStore.document(entry(7));
        legacyReal.remove(MongoSeasonStore.SIGNED_PID);

        assertThat(MongoSeasonStore.podiumEntry(legacy).pid()).isEqualTo(PlayerPids.NONE);
        assertThat(MongoSeasonStore.podiumEntry(legacyReal).pid()).isEqualTo(7);
    }
}
