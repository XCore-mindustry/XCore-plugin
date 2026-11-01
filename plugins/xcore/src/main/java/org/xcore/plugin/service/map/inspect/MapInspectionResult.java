package org.xcore.plugin.service.map.inspect;

import java.util.List;

public record MapInspectionResult(Status status, String detail, MapSnapshot snapshot, String profile, List<MapFinding> findings) {
    public enum Status { OK, DOWNLOAD_FAILED, INVALID_MAP }
    public MapInspectionResult { detail = MapFinding.safe(detail); findings = List.copyOf(findings); }
    public static MapInspectionResult failed(Status status, String detail) {
        return new MapInspectionResult(status, detail, null, null, List.of());
    }
}
