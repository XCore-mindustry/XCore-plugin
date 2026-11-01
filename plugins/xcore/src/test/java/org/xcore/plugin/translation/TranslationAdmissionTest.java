package org.xcore.plugin.translation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationAdmissionTest {

    @Test
    @DisplayName("an author's budget empties after the burst and refills over time")
    void authorBudget_refillsOverTime() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.translation.limits.authorBurst = 2;
        config.translation.limits.authorRefillPerMinute = 6;
        AtomicLong now = new AtomicLong();
        TranslationAdmission admission = new TranslationAdmission(config, now::get);

        assertThat(admission.tryAcquireAuthor("a")).isTrue();
        assertThat(admission.tryAcquireAuthor("a")).isTrue();
        assertThat(admission.tryAcquireAuthor("a")).isFalse();
        assertThat(admission.tryAcquireAuthor("b")).isTrue();

        now.addAndGet(TimeUnit.SECONDS.toNanos(10));
        assertThat(admission.tryAcquireAuthor("a")).isTrue();
        assertThat(admission.tryAcquireAuthor("a")).isFalse();
    }

    @Test
    @DisplayName("provider slots are capped and released")
    void slots_areCapped() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.translation.limits.maxConcurrentRequests = 1;
        TranslationAdmission admission = new TranslationAdmission(config);

        assertThat(admission.tryAcquireSlot()).isTrue();
        assertThat(admission.tryAcquireSlot()).isFalse();
        admission.releaseSlot();
        assertThat(admission.tryAcquireSlot()).isTrue();
    }
}
