package org.xcore.plugin.model;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import mindustry.gen.Player;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.mindrot.jbcrypt.BCrypt;
import org.xcore.plugin.gamemode.hexed.HexedRanks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PlayerData extends ModelData {

    @Builder.Default
    public int pid = -1;
    @Builder.Default public String uuid = "";

    @BsonProperty("last_ip")
    @Builder.Default public String ip = "";
    @Builder.Default public String nickname = "Unknown";

    @BsonProperty("custom_nickname")
    @Builder.Default public String customNickname = "";
    @Builder.Default public String description = "";

    @BsonProperty("password_hash")
    @Builder.Default public String password = "";

    @BsonProperty("local_language")
    @Builder.Default public String language = "auto";
    @BsonProperty("translator_language")
    @Builder.Default public String translatorLanguage = "off";

    @BsonProperty("pvp_rating")
    @Builder.Default public int pvpRating = 0;
    @BsonProperty("hexed_rank")
    @Builder.Default public int hexedRank = 0;
    @BsonProperty("hexed_points")
    @Builder.Default public int hexedPoints = 0;
    @BsonProperty("total_play_time")
    @Builder.Default public int totalPlayTime = 0;

    @BsonProperty("event_votes")
    @Builder.Default public Map<String, Boolean> eventVotes = new HashMap<>();
    @BsonProperty("map_votes")
    @Builder.Default public Map<String, Boolean> mapVotes = new HashMap<>();
    @BsonProperty("blocked_private_uuids")
    @Builder.Default public Set<String> blockedPrivateUuids = new HashSet<>();
    @BsonProperty("unlocked_badges")
    @Builder.Default public Set<String> unlockedBadges = new HashSet<>();
    @BsonProperty("active_badge")
    @Builder.Default public String activeBadge = "";

    @BsonProperty("is_admin")
    @Builder.Default public boolean admin = false;
    @BsonProperty("admin_source")
    @Builder.Default public String adminSource = "NONE";
    @Builder.Default public boolean leaderboard = true;

    @BsonProperty("discord_id")
    @Builder.Default public String discordId = "";
    @BsonProperty("discord_username")
    @Builder.Default public String discordUsername = "";
    @BsonProperty("discord_linked_at")
    @Builder.Default public long discordLinkedAt = 0L;

    @Builder.Default @BsonIgnore public transient Player player = null;
    @Builder.Default @BsonIgnore public String adminModVersion = null;
    @Builder.Default @BsonIgnore public long historySize = 0L;

    @Builder.Default @BsonIgnore public transient boolean exists = true;

    @Builder.Default @BsonIgnore public long lastUnload = 0;

    public PlayerData(String uuid, Boolean exists) {
        super();
        this.uuid = uuid;
        this.exists = exists;
        this.pid = -1;
    }

    public HexedRanks.HexedRank hexedRank() {
        return HexedRanks.HexedRank.values()[hexedRank];
    }

    public void hexedRank(HexedRanks.HexedRank rank) {
        this.hexedRank = rank.ordinal();
    }

    public void hashPassword(String password) {
        this.password = BCrypt.hashpw(password, BCrypt.gensalt());
    }

    public boolean verifyPassword(String password) {
        return BCrypt.checkpw(password, this.password);
    }
}
