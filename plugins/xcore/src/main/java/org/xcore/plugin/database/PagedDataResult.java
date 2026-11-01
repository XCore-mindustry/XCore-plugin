package org.xcore.plugin.database;

public record PagedDataResult<I>(int total, int pages, Iterable<I> results) {
}