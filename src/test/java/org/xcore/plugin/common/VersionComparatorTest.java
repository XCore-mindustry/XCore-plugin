package org.xcore.plugin.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionComparatorTest {

    @Test
    @DisplayName("compare handles null values")
    void compareNullValues() {
        assertThat(VersionComparator.compareVersions(null, null)).isZero();
        assertThat(VersionComparator.compareVersions(null, "1.0")).isNegative();
        assertThat(VersionComparator.compareVersions("1.0", null)).isPositive();
    }

    @Test
    @DisplayName("compare treats missing parts as zero")
    void compareMissingPartsAsZero() {
        assertThat(VersionComparator.compareVersions("1.2", "1.2.0")).isZero();
    }

    @Test
    @DisplayName("compare detects greater and lower versions")
    void compareOrder() {
        assertThat(VersionComparator.compareVersions("1.2.10", "1.2.2")).isPositive();
        assertThat(VersionComparator.compareVersions("2.0.0", "2.1.0")).isNegative();
    }

    @Test
    @DisplayName("compare parses non numeric parts as zero")
    void compareNonNumericParts() {
        assertThat(VersionComparator.compareVersions("1.a.3", "1.0.3")).isZero();
    }
}
