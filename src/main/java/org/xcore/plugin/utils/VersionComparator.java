package org.xcore.plugin.utils;

import arc.util.Strings;

import java.util.Comparator;

public class VersionComparator implements Comparator<String> {
    @Override
    public int compare(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int part1 = (i < parts1.length) ? Strings.parseInt(parts1[i], 0) : 0;
            int part2 = (i < parts2.length) ? Strings.parseInt(parts2[i], 0) : 0;

            if (part1 < part2) {
                return -1;
            } else if (part1 > part2) {
                return 1;
            }
        }

        return 0;
    }
}