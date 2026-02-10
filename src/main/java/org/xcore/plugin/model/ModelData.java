package org.xcore.plugin.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

@Data
@SuperBuilder
@NoArgsConstructor
@Accessors(chain = true)
public abstract class ModelData {
    @BsonId public ObjectId id;

    @Builder.Default public long createdModelTime = System.currentTimeMillis();

    @Builder.Default public long editModelTime = System.currentTimeMillis();

    @Builder.Default public long versionModel = 1;
}
