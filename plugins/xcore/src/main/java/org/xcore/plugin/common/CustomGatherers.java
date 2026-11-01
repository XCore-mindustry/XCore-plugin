package org.xcore.plugin.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Gatherer;

/**
 * Custom Stream Gatherers for common operations.
 * Requires Java 25+ with --enable-preview.
 */
public final class CustomGatherers {

    private CustomGatherers() {}

    /**
     * Returns a gatherer that groups elements into fixed-size pages
     * and returns only the requested page.
     *
     * @param pageSize number of elements per page
     * @param pageNumber 1-based page number
     * @return gatherer that produces a single List containing the page elements
     */
    public static <T> Gatherer<T, ?, List<T>> page(int pageSize, int pageNumber) {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be positive");
        if (pageNumber <= 0) throw new IllegalArgumentException("pageNumber must be positive (1-based)");

        int startIndex = (pageNumber - 1) * pageSize;
        int endIndex = startIndex + pageSize;

        return Gatherer.ofSequential(
                () -> new PageState<T>(startIndex, endIndex),
                (state, element, downstream) -> {
                    if (state.currentIndex >= startIndex && state.currentIndex < endIndex) {
                        state.elements.add(element);
                    }
                    state.currentIndex++;

                    return state.currentIndex < endIndex;
                },
                (state, downstream) -> {
                    if (!state.elements.isEmpty()) {
                        downstream.push(state.elements);
                    }
                }
        );
    }

    /**
     * Returns a gatherer that wraps each element with its 0-based index.
     */
    public static <T> Gatherer<T, ?, Indexed<T>> indexed() {
        return Gatherer.ofSequential(
                () -> new int[]{0},
                (state, element, downstream) -> {
                    downstream.push(new Indexed<>(state[0]++, element));
                    return true;
                }
        );
    }

    /**
     * Returns a gatherer that wraps each element with a 1-based index (for display).
     */
    public static <T> Gatherer<T, ?, Indexed<T>> indexed1Based() {
        return Gatherer.ofSequential(
                () -> new int[]{1},
                (state, element, downstream) -> {
                    downstream.push(new Indexed<>(state[0]++, element));
                    return true;
                }
        );
    }

    /**
     * Combines pagination with indexing - returns indexed elements for a specific page.
     * The index is relative to the page (starts from 1 for display purposes).
     *
     * @param pageSize number of elements per page
     * @param pageNumber 1-based page number
     * @return gatherer producing indexed elements
     */
    public static <T> Gatherer<T, ?, Indexed<T>> indexedPage(int pageSize, int pageNumber) {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be positive");
        if (pageNumber <= 0) throw new IllegalArgumentException("pageNumber must be positive (1-based)");

        int startIndex = (pageNumber - 1) * pageSize;
        int endIndex = startIndex + pageSize;

        return Gatherer.ofSequential(
                () -> new int[]{0},
                (state, element, downstream) -> {
                    int currentIndex = state[0]++;
                    if (currentIndex >= startIndex && currentIndex < endIndex) {
                        downstream.push(new Indexed<>(currentIndex + 1, element));
                    }
                    return currentIndex < endIndex - 1;
                }
        );
    }

    /**
     * Calculates pagination metadata without consuming elements.
     */
    public static <T> PaginationInfo calculatePagination(long totalElements, int pageSize) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return new PaginationInfo(totalElements, pageSize, totalPages);
    }

    private static class PageState<T> {
        final List<T> elements = new ArrayList<>();
        final int startIndex;
        final int endIndex;
        int currentIndex = 0;

        PageState(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    /**
     * Wrapper for an element with its index.
     */
    public record Indexed<T>(int index, T value) {}

    /**
     * Pagination metadata.
     */
    public record PaginationInfo(long totalElements, int pageSize, int totalPages) {
        public boolean isValidPage(int page) {
            return page >= 1 && page <= totalPages;
        }

        public int clampPage(int page) {
            if (page < 1) return 1;
            if (page > totalPages) return totalPages;
            return page;
        }
    }
}
