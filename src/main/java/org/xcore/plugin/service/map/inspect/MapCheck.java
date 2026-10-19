package org.xcore.plugin.service.map.inspect;

import java.util.List;

/** Checks receive detached values only and have no engine, I/O, or transport dependencies. */
@FunctionalInterface
public interface MapCheck {
    List<MapFinding> check(MapSnapshot snapshot, String profile, InspectionSettings settings);
}
