package org.xcore.plugin.service.network;

import mindustry.maps.Map;
import org.xcore.plugin.model.MapData;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListResponseV1;
import org.xcore.protocol.generated.shared.MapEntryV1;

import java.util.List;

public final class MapsProtocolMapper {
    private MapsProtocolMapper() {
    }

    public static MapsListResponseV1 toMapsListResponse(String server, List<MapEntryV1> maps) {
        return new MapsListResponseV1(server, maps);
    }

    public static MapEntryV1 toMapEntry(Map map, String currentGameMode, MapData persistedMap) {
        String fileName = map.file == null || map.file.name() == null || map.file.name().isBlank()
                ? map.plainName() + ".msav"
                : map.file.name();
        String rawAuthor = map.author();
        String author = rawAuthor == null || rawAuthor.isBlank() ? "Unknown" : rawAuthor;

        return new MapEntryV1(
                map.plainName(),
                fileName,
                author,
                map.width,
                map.height,
                toFileSizeBytes(map.file == null ? null : map.file.length()),
                persistedMap == null ? null : persistedMap.like,
                persistedMap == null ? null : persistedMap.dislike,
                persistedMap == null ? null : persistedMap.reputation,
                persistedMap == null ? null : persistedMap.popularity,
                persistedMap == null ? null : persistedMap.interest,
                persistedMap == null ? currentGameMode : persistedMap.gameMode
        );
    }

    private static Integer toFileSizeBytes(Long fileSizeBytes) {
        if (fileSizeBytes == null || fileSizeBytes < 0L || fileSizeBytes > Integer.MAX_VALUE) {
            return null;
        }
        return fileSizeBytes.intValue();
    }
}
