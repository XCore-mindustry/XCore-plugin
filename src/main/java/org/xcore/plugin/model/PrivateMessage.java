package org.xcore.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
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
public class PrivateMessage extends ModelData {

    @BsonProperty("from_uuid")
    @Builder.Default public String fromUuid = "";
    @BsonProperty("from_pid")
    @Builder.Default public int fromPid = -1;
    @BsonProperty("from_name")
    @Builder.Default public String fromName = "Unknown";

    @BsonProperty("to_uuid")
    @Builder.Default public String toUuid = "";
    @BsonProperty("to_pid")
    @Builder.Default public int toPid = -1;

    @Builder.Default public String message = "";

    @BsonProperty("read_at")
    @Builder.Default public long readAt = 0L;
    @BsonProperty("delivered_at")
    @Builder.Default public long deliveredAt = 0L;
    @BsonProperty("recipient_deleted")
    @Builder.Default public boolean recipientDeleted = false;
}
