package org.xcore.plugin.service;

import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.session.Session;

import java.util.ArrayList;
import java.util.List;

import static mindustry.Vars.netServer;

@Singleton
public class PlayerDisplayService {

    private final Config config;

    @Inject
    public PlayerDisplayService(Config config) {
        this.config = config;
    }

    public String buildDisplayName(PlayerData data, Player player) {
        List<String> parts = new ArrayList<>();

        String systemBadge = resolveSystemBadgeTag(data, player);
        if (!systemBadge.isEmpty()) {
            parts.add(systemBadge);
        }

        String selectedBadge = resolveSelectedBadgeTag(data);
        if (!selectedBadge.isEmpty()) {
            parts.add(selectedBadge);
        }

        String hexedTag = resolveHexedTag(data);
        if (!hexedTag.isEmpty()) {
            parts.add(hexedTag);
        }

        String baseName = resolveBaseName(data, player);
        if (!baseName.isEmpty()) {
            parts.add(baseName);
        }

        return String.join(" ", parts).trim();
    }

    public String resolveBaseName(PlayerData data) {
        return resolveBaseName(data, null);
    }

    public String resolveBaseName(PlayerData data, Player player) {
        if (data == null) return "Player";

        if (data.customNickname != null && !data.customNickname.isBlank()) {
            return data.customNickname;
        }

        if (data.nickname != null && !data.nickname.isBlank()) {
            return data.nickname;
        }

        if (player != null && player.getInfo() != null && player.getInfo().lastName != null && !player.getInfo().lastName.isBlank()) {
            return player.getInfo().lastName;
        }

        return "Player";
    }

    public String resolveSystemBadgeTag(PlayerData data) {
        return resolveSystemBadgeTag(data, null);
    }

    public String resolveSystemBadgeTag(PlayerData data, Player player) {
        if (data == null) return "";

        if (player != null) {
            return player.admin ? Badge.ADMIN.tag() : "";
        }

        return data.admin ? Badge.ADMIN.tag() : "";
    }

    public String resolveSelectedBadgeTag(PlayerData data) {
        if (data == null || data.activeBadge == null || data.activeBadge.isBlank()) return "";

        Badge badge = Badge.byId(data.activeBadge);
        if (badge == null || !badge.selectable() || badge.system()) return "";
        if (data.unlockedBadges == null || !data.unlockedBadges.contains(badge.id())) return "";
        return badge.tag();
    }

    public String resolveHexedTag(PlayerData data) {
        if (data == null || !config.isMiniHexed()) return "";
        return data.hexedRank().tag == null ? "" : data.hexedRank().tag;
    }

    public String buildChatBadgePrefix(PlayerData data, Player player) {
        List<String> parts = new ArrayList<>();

        String systemBadge = resolveSystemBadgeTag(data, player);
        if (!systemBadge.isEmpty()) {
            parts.add(systemBadge);
        }

        String selectedBadge = resolveSelectedBadgeTag(data);
        if (!selectedBadge.isEmpty()) {
            parts.add(selectedBadge);
        }

        return String.join(" ", parts).trim();
    }

    public String resolveChatBaseName(PlayerData data, Player player) {
        return resolveBaseName(data, player);
    }

    public void refresh(Session session) {
        if (session == null) return;
        refresh(session.player, session.data);
    }

    public void refresh(Player player, PlayerData data) {
        if (player == null || data == null) return;

        String displayName = buildDisplayName(data, player);
        if (displayName.isBlank()) {
            displayName = "Player";
        }

        player.name = displayName;

        var info = netServer.admins.getInfo(player.uuid());
        if (info != null) {
            info.lastName = player.name;
        }
    }

    public String plainDisplayName(PlayerData data, Player player) {
        return Strings.stripColors(buildDisplayName(data, player));
    }
}
