package org.xcore.plugin.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

@Data
@SuperBuilder
@NoArgsConstructor
@Accessors(chain = true)
public abstract class ModelData {
    @BsonId public ObjectId id;

    @BsonProperty("created_at")
    @Builder.Default public long createdModelTime = System.currentTimeMillis();

    @BsonProperty("updated_at")
    @Builder.Default public long editModelTime = System.currentTimeMillis();

    @BsonProperty("deleted_at")
    @Builder.Default public long deleteModelTime = System.currentTimeMillis();

    @BsonProperty("is_visible")
    @Builder.Default public boolean isVisible =  true;

    @BsonProperty("version")
    @Builder.Default public long versionModel = 1;
}
