package org.xcore.plugin.utils.models;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import static org.xcore.plugin.PluginVars.database;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapData {
    @BsonId
    public ObjectId id;


    public String name = "Unknown";
    public String author = "Unknown";
    public String gameMode = "Unknown";

    public int reputation = 0;
    public double popularity = 0;
    public double interest = 0;
    public int playedTimesYear = 0;

    public long playedTimes = 0;
    public long lastPlayedTime = 0;

    public long minimumGameTime = 0;
    public long averageGameTime = 0;
    public long maximumGameTime = 0;


    public MapData(String name) {
        this.name = name;
        this.lastPlayedTime = System.currentTimeMillis();
    }

    public MapData(String mapName, String author, String gameMode) {
        this.name = mapName;
        this.author = author;
        this.gameMode = gameMode;
        this.lastPlayedTime = System.currentTimeMillis();
    }

    public void save() {
        database.mapDatas.save(this);
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
            if (duration < this.minimumGameTime) this.minimumGameTime = duration;
            if (duration > this.maximumGameTime) this.maximumGameTime = duration;
        }

        this.playedTimes++;
        this.playedTimesYear++;
        this.lastPlayedTime = System.currentTimeMillis();

        this.popularity += (isWin ? 2.0 : 0.5);
        this.interest -= (isWin ? 2 : 0.5);
    }

    public void addReputation() {
        this.reputation++;
        this.popularity += 2.0;
    }

    public void removeReputation() {
        this.reputation--;
        this.popularity -= 2.0;
    }

    public void onSkip() {
        this.popularity -= 1.0;
        this.interest -= 0.5;
    }
}
