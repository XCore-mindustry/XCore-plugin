package org.xcore.plugin.vote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoteChoiceTest {

    @Test
    @DisplayName("parse resolves YES variants")
    void parseYesVariants() {
        assertThat(VoteChoice.parse("yes")).isEqualTo(VoteChoice.YES);
        assertThat(VoteChoice.parse(" y ")).isEqualTo(VoteChoice.YES);
    }

    @Test
    @DisplayName("parse resolves NO variants")
    void parseNoVariants() {
        assertThat(VoteChoice.parse("no")).isEqualTo(VoteChoice.NO);
        assertThat(VoteChoice.parse(" n ")).isEqualTo(VoteChoice.NO);
    }

    @Test
    @DisplayName("parse returns ABSTAIN for blank and unknown values")
    void parseAbstain() {
        assertThat(VoteChoice.parse("")).isEqualTo(VoteChoice.ABSTAIN);
        assertThat(VoteChoice.parse("   ")).isEqualTo(VoteChoice.ABSTAIN);
        assertThat(VoteChoice.parse("maybe")).isEqualTo(VoteChoice.ABSTAIN);
        assertThat(VoteChoice.parse("1")).isEqualTo(VoteChoice.ABSTAIN);
        assertThat(VoteChoice.parse("0")).isEqualTo(VoteChoice.ABSTAIN);
        assertThat(VoteChoice.parse("-1")).isEqualTo(VoteChoice.ABSTAIN);
        assertThat(VoteChoice.parse("+")).isEqualTo(VoteChoice.ABSTAIN);
        assertThat(VoteChoice.parse("-")).isEqualTo(VoteChoice.ABSTAIN);
    }

    @Test
    @DisplayName("parse throws on null input")
    void parseNullThrows() {
        assertThatThrownBy(() -> VoteChoice.parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("isValid returns false only for ABSTAIN")
    void isValidWorks() {
        assertThat(VoteChoice.YES.isValid()).isTrue();
        assertThat(VoteChoice.NO.isValid()).isTrue();
        assertThat(VoteChoice.ABSTAIN.isValid()).isFalse();
    }
}
