package org.xcore.plugin.service.network;

import mindustry.maps.Map;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveResponseV1;
import org.xcore.plugin.model.MapData;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListResponseV1;
import org.xcore.protocol.generated.shared.MapEntryV1;

import java.util.List;
import org.xcore.plugin.service.map.inspect.MapInspectionResult;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsInspectResponseV1;
import org.xcore.protocol.generated.messages.maps.MapsInspectResponseV1Status;
import org.xcore.protocol.generated.shared.MapInspectInfoV1;
import org.xcore.protocol.generated.shared.MapInspectFindingV1;
import org.xcore.protocol.generated.shared.MapInspectFindingV1Severity;

public final class MapsProtocolMapper {
    // Upper bounds of MapInspectInfoV1; .msav metadata strings can be up to 64 KB each.
    static final int MAX_NAME = 256, MAX_DESCRIPTION = 1024, MAX_MODS = 32;

    private MapsProtocolMapper() {
    }

    public static MapsListResponseV1 toMapsListResponse(String server, List<MapEntryV1> maps) {
        return new MapsListResponseV1(server, maps);
    }

    public static MapsRemoveResponseV1 toMapsRemoveResponse(String server, String result) {
        return new MapsRemoveResponseV1(server, result);
    }

    public static MapsInspectResponseV1 toInspectResponse(String server, MapInspectionResult result) {
        MapInspectInfoV1 info = null;
        if (result.snapshot() != null) {
            var snapshot = result.snapshot();
            var meta = snapshot.metadata();
            var rules = meta.rules();
            java.util.Map<String, Object> cores = new java.util.TreeMap<>();
            snapshot.coresByTeam().forEach(cores::put);
            var mods = meta.mods() == null ? null
                    : meta.mods().stream().limit(MAX_MODS).map(mod -> cap(mod, MAX_NAME)).toList();
            info = new MapInspectInfoV1(cap(meta.name(), MAX_NAME), cap(meta.author(), MAX_NAME),
                    cap(meta.description(), MAX_DESCRIPTION), meta.width(), meta.height(),
                    meta.saveVersion(), meta.build(), rules.gameMode(), result.profile(),
                    snapshot.tilesAvailable() ? snapshot.spawnCount() : null, snapshot.tilesAvailable() ? cores : null,
                    rules.waves(), rules.waveTimer(), rules.spawnGroupCount(), rules.dropZoneRadius(), mods,
                    meta.sha256(), meta.sizeBytes());
        }
        var findings = result.findings().stream().map(finding -> new MapInspectFindingV1(finding.code(),
                MapInspectFindingV1Severity.valueOf(finding.severity().name()), finding.detail(),
                finding.x(), finding.y(), finding.count())).toList();
        return new MapsInspectResponseV1(server, MapsInspectResponseV1Status.valueOf(result.status().name()),
                result.detail(), info, findings);
    }

    static String cap(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max);
    }

    public static MapEntryV1 toMapEntry(Map map, String currentGameMode, MapData persistedMap,
                                      org.xcore.plugin.model.MapDiscordUpload upload) {
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
                persistedMap == null ? currentGameMode : persistedMap.gameMode,
                safeUpload(upload)
        );
    }

    private static org.xcore.protocol.generated.shared.MapDiscordUploadV1 safeUpload(org.xcore.plugin.model.MapDiscordUpload upload) {
        if (upload == null) return null;
        try { return upload.toProtocol(); }
        catch (RuntimeException corrupt) {
            org.xcore.plugin.common.PLog.warn("Ignoring corrupt map attribution: @", corrupt.toString());
            return null;
        }
    }

    private static Integer toFileSizeBytes(Long fileSizeBytes) {
        if (fileSizeBytes == null || fileSizeBytes < 0L || fileSizeBytes > Integer.MAX_VALUE) {
            return null;
        }
        return fileSizeBytes.intValue();
    }
}
