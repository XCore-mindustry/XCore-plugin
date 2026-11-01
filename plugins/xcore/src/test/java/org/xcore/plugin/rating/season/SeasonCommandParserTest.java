package org.xcore.plugin.rating.season;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonCommandParserTest {

    @Test
    @DisplayName("a place is a number or a range")
    void places() {
        assertThat(SeasonCommandParser.places("1")).containsExactly(1, 1);
        assertThat(SeasonCommandParser.places(" 2-5 ")).containsExactly(2, 5);
        assertThat(SeasonCommandParser.places("3-3")).containsExactly(3, 3);
    }

    @Test
    @DisplayName("anything else is refused with a hint")
    void places_refusesNonsense() {
        for (String text : new String[]{"", "0", "-1", "3-1", "a", "1-b", "1-2-3", null}) {
            assertThatThrownBy(() -> SeasonCommandParser.places(text))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expected 1 or 1-3");
        }
    }
}
