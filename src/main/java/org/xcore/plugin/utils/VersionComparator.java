package org.xcore.plugin.utils;

import arc.util.Strings;

import java.util.Comparator;

public final class VersionComparator implements Comparator<String> {

    public static final VersionComparator INSTANCE = new VersionComparator();

    private VersionComparator() {
    }

    @Override
    public int compare(String version1, String version2) {
        if (version1 == null && version2 == null) return 0;
        if (version1 == null) return -1;
        if (version2 == null) return 1;

        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int part1 = (i < parts1.length) ? Strings.parseInt(parts1[i], 0) : 0;
            int part2 = (i < parts2.length) ? Strings.parseInt(parts2[i], 0) : 0;

            if (part1 < part2) return -1;
            if (part1 > part2) return 1;
        }
        return 0;
    }

    public static int compareVersions(String version1, String version2) {
        return INSTANCE.compare(version1, version2);
    }
}
