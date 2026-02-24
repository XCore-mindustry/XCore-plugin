package org.xcore.plugin.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class CustomGatherersTest {

    @Test
    @DisplayName("page returns requested page slice")
    void pageReturnsSlice() {
        var values = IntStream.rangeClosed(1, 10).boxed().toList();

        var page = values.stream()
                .gather(CustomGatherers.page(3, 2))
                .toList();

        assertThat(page).containsExactly(List.of(4, 5, 6));
    }

    @Test
    @DisplayName("page validates arguments")
    void pageValidatesArguments() {
        assertThatThrownBy(() -> CustomGatherers.page(0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize");
        assertThatThrownBy(() -> CustomGatherers.page(10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageNumber");
    }

    @Test
    @DisplayName("indexed and indexed1Based produce expected indexes")
    void indexedProducesExpectedIndexes() {
        var values = List.of("a", "b", "c");

        var zeroBased = values.stream()
                .gather(CustomGatherers.indexed())
                .toList();
        var oneBased = values.stream()
                .gather(CustomGatherers.indexed1Based())
                .toList();

        assertThat(zeroBased)
                .extracting(CustomGatherers.Indexed::index, CustomGatherers.Indexed::value)
                .containsExactly(tuple(0, "a"), tuple(1, "b"), tuple(2, "c"));
        assertThat(oneBased)
                .extracting(CustomGatherers.Indexed::index, CustomGatherers.Indexed::value)
                .containsExactly(tuple(1, "a"), tuple(2, "b"), tuple(3, "c"));
    }

    @Test
    @DisplayName("indexedPage returns indexed items for selected page")
    void indexedPageReturnsItemsForPage() {
        var values = IntStream.rangeClosed(1, 8).boxed().toList();

        var page = values.stream()
                .gather(CustomGatherers.indexedPage(3, 2))
                .toList();

        assertThat(page)
                .extracting(CustomGatherers.Indexed::index, CustomGatherers.Indexed::value)
                .containsExactly(tuple(4, 4), tuple(5, 5), tuple(6, 6));
    }

    @Test
    @DisplayName("calculatePagination returns expected metadata")
    void calculatePaginationMetadata() {
        var info = CustomGatherers.calculatePagination(23, 10);

        assertThat(info.totalPages()).isEqualTo(3);
        assertThat(info.isValidPage(2)).isTrue();
        assertThat(info.isValidPage(4)).isFalse();
        assertThat(info.clampPage(0)).isEqualTo(1);
        assertThat(info.clampPage(5)).isEqualTo(3);
    }
}
