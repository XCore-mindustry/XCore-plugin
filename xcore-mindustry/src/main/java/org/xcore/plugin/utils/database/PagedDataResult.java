package org.xcore.plugin.utils.database;

public record PagedDataResult<I>(int total, int pages, Iterable<I> results) {
}