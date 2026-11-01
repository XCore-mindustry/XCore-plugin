package org.xcore.plugin.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

/** Latest provenance of one physical file on one server, independent of map statistics. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapUploadRecord {
    @BsonId public String id;
    public String server;
    public String fileName;
    public String contentHash;
    public MapDiscordUpload upload;
    public long recordedAt;
}
