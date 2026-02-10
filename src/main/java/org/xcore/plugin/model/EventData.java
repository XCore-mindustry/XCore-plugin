package org.xcore.plugin.model;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.bson.types.ObjectId;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class EventData extends ModelData { ;

    @Builder.Default public String name = "Unknown";
    @Builder.Default public String description = "Unknown";

    public ObjectId author;
    public ObjectId map;

    @Builder.Default public int like = 0;
    @Builder.Default public int dislike = 0;

    @Builder.Default public boolean isMajor = false;
    @Builder.Default public boolean isConducted = false;
    @Builder.Default public boolean isActive = false;
    @Builder.Default public boolean isTemporary = false;

    @Builder.Default public long plannedStartTime = 0;
    @Builder.Default public long plannedEndTime = 0;

    @Builder.Default public long startTime = 0;
    @Builder.Default public long endTime = 0;

    public EventData(String name, ObjectId author, ObjectId map) {
        super();
        this.name = name;
        this.author = author;
        this.map = map;
    }
}
