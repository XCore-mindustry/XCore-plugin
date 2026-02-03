package org.xcore.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventData {
    @BsonId
    public ObjectId id;

    public String name = "Unknown";
    public String description = "Unknown";
    public ObjectId author;
    public ObjectId map;

    public int like = 0;
    public int dislike = 0;

    public boolean isMajor = false;
    public boolean isConducted = false;
    public boolean isActive = false;
    public boolean isTemporary = false;

    public long plannedStartTime = 0;
    public long plannedEndTime = 0;

    public long startTime = 0;
    public long endTime = 0;

    public long createdEventTime = 0;

    public long createdTime = 0;

    public EventData(String name, ObjectId author, ObjectId map) {
        this.name = name;
        this.author = author;
        this.map = map;
        this.createdEventTime = System.currentTimeMillis();
        this.createdTime = System.currentTimeMillis();
    }
}
