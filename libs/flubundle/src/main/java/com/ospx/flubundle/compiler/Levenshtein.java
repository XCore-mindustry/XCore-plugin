package com.ospx.flubundle.compiler;

import java.util.Collection;
import java.util.Optional;

final class Levenshtein {

    private Levenshtein() {
    }

    public static int distance(String s1, String s2) {
        if (s1.equalsIgnoreCase(s2)) {
            return 0;
        }
        int len1 = s1.length();
        int len2 = s2.length();
        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = Character.toLowerCase(s1.charAt(i - 1)) == Character.toLowerCase(s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, len2 + 1);
        }

        return prev[len2];
    }

    public static Optional<String> findClosest(String target, Collection<String> candidates, int maxDistance) {
        String best = null;
        int bestDist = Integer.MAX_VALUE;

        for (String candidate : candidates) {
            int dist = distance(target, candidate);
            if (dist < bestDist && dist <= maxDistance) {
                bestDist = dist;
                best = candidate;
            }
        }

        return Optional.ofNullable(best);
    }
}
