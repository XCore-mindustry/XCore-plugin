package org.xcore.plugin.model;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonProperty;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MapData extends ModelData {

    @Builder.Default public String name = "Unknown";
    @Builder.Default public String fileName = "Unknown";
    @Builder.Default public String author = "Unknown";
    @BsonProperty("game_mode")
    @Builder.Default public String gameMode = "Unknown";

    @Builder.Default public int like = 0;
    @Builder.Default public int dislike = 0;

    @Builder.Default public int reputation = 0;
    @Builder.Default public double popularity = 0;
    @Builder.Default public double interest = 0;
    @BsonProperty("played_times_year")
    @Builder.Default public int playedTimesYear = 0;

    @BsonProperty("play_count")
    @Builder.Default public long playedTimes = 0;
    @BsonProperty("last_played_at")
    @Builder.Default public long lastPlayedTime = 0;

    @BsonProperty("minimum_duration")
    @Builder.Default public long minimumGameTime = 0;
    @BsonProperty("average_duration")
    @Builder.Default public long averageGameTime = 0;
    @BsonProperty("maximum_duration")
    @Builder.Default public long maximumGameTime = 0;

    public MapData(String name, String fileName, String author, String gameMode) {
        super();
        this.name = name;
        this.fileName = fileName;
        this.author = author;
        this.gameMode = gameMode;
        this.lastPlayedTime = System.currentTimeMillis();
    }

    public void registerGame(long duration, boolean isWin, String currentMode, String currentAuthor) {
        this.gameMode = currentMode;
        this.author = currentAuthor;

        if (playedTimes == 0) {
            this.averageGameTime = duration;
            this.minimumGameTime = duration;
            this.maximumGameTime = duration;
        } else {
            this.averageGameTime = ((this.averageGameTime * this.playedTimes) + duration) / (this.playedTimes + 1);
            this.minimumGameTime = Math.min(this.minimumGameTime, duration);
            this.maximumGameTime = Math.max(this.maximumGameTime, duration);
        }

        this.playedTimes++;
        this.playedTimesYear++;
        this.lastPlayedTime = System.currentTimeMillis();

        this.popularity += (isWin ? 2.0 : 0.5);
        this.interest -= (isWin ? 2 : 0.5);
    }

    public void onSkip() {
        this.popularity -= 1.0;
        this.interest -= 0.5;
    }
}
