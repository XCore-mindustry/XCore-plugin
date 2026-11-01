package org.xcore.plugin.integration.top;

import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.PlayerData;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardEntryTest {

    @Test
    void profileAttributesIgnoreLegacyAdminFlag() {
        var data = new PlayerData("target", true);
        data.admin = true;
        assertThat(LeaderboardEntry.profileAttributes(data)).doesNotContainKey("admin");
    }
}
