package org.xcore.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.MapDiscordUploadV1;

/** Latest Discord submission, separate from the author embedded in the map file. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapDiscordUpload {
    public String uploadedByDiscordId;
    public String uploadedByName;
    public long uploadedAt;
    public String creatorDiscordId;
    public String creatorName;

    public static MapDiscordUpload fromProtocol(MapDiscordUploadV1 upload) {
        if (upload == null) return null;
        return new MapDiscordUpload(upload.uploadedBy().discordId(), upload.uploadedBy().discordUsername(),
                upload.uploadedAt(), upload.creator() == null ? null : upload.creator().discordId(),
                upload.creator() == null ? null : upload.creator().discordUsername());
    }

    public MapDiscordUploadV1 toProtocol() {
        return new MapDiscordUploadV1(new DiscordIdentityRefV1(uploadedByDiscordId, uploadedByName),
                uploadedAt, creatorDiscordId == null ? null : new DiscordIdentityRefV1(creatorDiscordId, creatorName));
    }
}
