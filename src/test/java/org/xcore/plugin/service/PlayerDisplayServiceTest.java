package org.xcore.plugin.service;

import arc.graphics.Color;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.model.PlayerData;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerDisplayServiceTest {

    @Test
    @DisplayName("buildDisplayName uses nickname when no badges are active")
    void nicknameOnly() {
        var service = new PlayerDisplayService(config("mini-pvp"));
        var data = basePlayer();

        assertThat(service.buildDisplayName(data, null)).isEqualTo("PlayerOne");
    }

    @Test
    @DisplayName("buildDisplayName prefers custom nickname")
    void customNicknamePreferred() {
        var service = new PlayerDisplayService(config("mini-pvp"));
        var data = basePlayer();
        data.customNickname = "[green]Custom[]";

        assertThat(service.buildDisplayName(data, null)).isEqualTo("[green]Custom[]");
    }

    @Test
    @DisplayName("buildDisplayName includes selected manual badge when unlocked")
    void selectedBadgeIncluded() {
        var service = new PlayerDisplayService(config("mini-pvp"));
        var data = basePlayer();
        data.unlockedBadges.add("developer");
        data.activeBadge = "developer";

        assertThat(service.buildDisplayName(data, null)).isEqualTo("[#86dca2]<[white]" + Iconc.wrench + "[]>[] PlayerOne");
    }

    @Test
    @DisplayName("buildDisplayName uses player color for selected badge symbol when mode is player-color")
    void selectedBadgeUsesPlayerColorWhenConfigured() {
        var service = new PlayerDisplayService(config("mini-pvp"));
        var data = basePlayer();
        data.unlockedBadges.add("developer");
        data.activeBadge = "developer";
        data.badgeSymbolColorMode = "player-color";

        Player player = Mockito.mock(Player.class);
        player.color = new Color();
        player.color.set(Color.valueOf("ff8844"));

        assertThat(service.buildDisplayName(data, player)).isEqualTo("[#86dca2]<[white][#ff8844]" + Iconc.wrench + "[]>[] PlayerOne");
    }

    @Test
    @DisplayName("buildDisplayName falls back to default badge color when player color is unavailable")
    void selectedBadgeFallsBackWhenPlayerColorUnavailable() {
        var service = new PlayerDisplayService(config("mini-pvp"));
        var data = basePlayer();
        data.unlockedBadges.add("developer");
        data.activeBadge = "developer";
        data.badgeSymbolColorMode = "player-color";

        assertThat(service.buildDisplayName(data, null)).isEqualTo("[#86dca2]<[white]" + Iconc.wrench + "[]>[] PlayerOne");
    }

    @Test
    @DisplayName("buildDisplayName ignores selected badge when it is not unlocked")
    void lockedBadgeIgnored() {
        var service = new PlayerDisplayService(config("mini-pvp"));
        var data = basePlayer();
        data.activeBadge = "developer";

        assertThat(service.buildDisplayName(data, null)).isEqualTo("PlayerOne");
    }

    @Test
    @DisplayName("buildDisplayName includes automatic admin badge")
    void adminBadgeIncluded() {
        var service = new PlayerDisplayService(config("mini-pvp"));
        var data = basePlayer();
        data.admin = true;

        assertThat(service.buildDisplayName(data, null)).isEqualTo("[scarlet]<" + Iconc.hammer + ">[] PlayerOne");
    }

    @Test
    @DisplayName("buildDisplayName hides admin badge when runtime player admin is false")
    void runtimeAdminStateWinsForOnlinePlayer() {
        var service = new PlayerDisplayService(config("mini-pvp"));
        var data = basePlayer();
        data.admin = true;

        Player player = Mockito.mock(Player.class);
        player.admin = false;

        assertThat(service.buildDisplayName(data, player)).isEqualTo("PlayerOne");
    }

    @Test
    @DisplayName("chat badge prefix excludes hexed rank and base nickname")
    void chatBadgePrefixOnlyUsesBadges() {
        var service = new PlayerDisplayService(config("mini-hexed"));
        var data = basePlayer();
        data.admin = true;
        data.unlockedBadges.add("translator");
        data.activeBadge = "translator";
        data.hexedRank(HexedRanks.HexedRank.regular);

        assertThat(service.buildChatBadgePrefix(data, null))
                .isEqualTo("[scarlet]<" + Iconc.hammer + ">[] [#79d7ff]<[white]" + Iconc.bookOpen + "[]>[]");
    }

    @Test
    @DisplayName("chat badge prefix uses player color for selected badge symbol when mode is player-color")
    void chatBadgePrefixUsesPlayerColorWhenConfigured() {
        var service = new PlayerDisplayService(config("mini-pvp"));
        var data = basePlayer();
        data.unlockedBadges.add("translator");
        data.activeBadge = "translator";
        data.badgeSymbolColorMode = "player-color";

        Player player = Mockito.mock(Player.class);
        player.color = new Color();
        player.color.set(Color.valueOf("44ccff"));

        assertThat(service.buildChatBadgePrefix(data, player))
                .isEqualTo("[#79d7ff]<[white][#44ccff]" + Iconc.bookOpen + "[]>[]");
    }

    @Test
    @DisplayName("buildDisplayName includes hexed rank on mini-hexed server")
    void hexedRankIncluded() {
        var service = new PlayerDisplayService(config("mini-hexed"));
        var data = basePlayer();
        data.hexedRank(HexedRanks.HexedRank.regular);

        assertThat(service.buildDisplayName(data, null)).isEqualTo("[cyan]<[accent]\uF7E7[cyan]>[] PlayerOne");
    }

    @Test
    @DisplayName("buildDisplayName composes admin badge manual badge and hexed rank together")
    void fullComposition() {
        var service = new PlayerDisplayService(config("mini-hexed"));
        var data = basePlayer();
        data.admin = true;
        data.unlockedBadges.add("developer");
        data.activeBadge = "developer";
        data.hexedRank(HexedRanks.HexedRank.regular);
        data.customNickname = "[gold]Hero[]";

        assertThat(service.buildDisplayName(data, null))
                .isEqualTo("[scarlet]<" + Iconc.hammer + ">[] [#86dca2]<[white]" + Iconc.wrench + "[]>[] [cyan]<[accent]\uF7E7[cyan]>[] [gold]Hero[]");
    }

    @Test
    @DisplayName("resolveSelectedBadgeTag rejects non selectable system badge")
    void systemBadgeCannotBeSelectedManually() {
        var service = new PlayerDisplayService(config("mini-pvp"));
        var data = basePlayer();
        data.unlockedBadges.add("admin");
        data.activeBadge = "admin";

        assertThat(service.resolveSelectedBadgeTag(data)).isEmpty();
    }

    private static PlayerData basePlayer() {
        var data = new PlayerData("uuid-test", true);
        data.nickname = "PlayerOne";
        data.unlockedBadges = new HashSet<>();
        data.activeBadge = "";
        return data;
    }

    private static Config config(String server) {
        var config = new Config();
        config.server = server;
        return config;
    }
}
