package org.xcore.plugin.service.map.inspect;

/** The saved rules needed by checks; an empty spawns array remains empty in this metadata. */
public record MapRules(String gameMode, int defaultTeam, int waveTeam, boolean waves, boolean waveTimer,
                       int spawnGroupCount, double dropZoneRadius) {}
