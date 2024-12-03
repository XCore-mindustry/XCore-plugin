package io.github.xcore.common.database

sealed interface DocumentFilter {
    data class Eq(val key: String, val value: Any) : DocumentFilter
}

fun eq(key: String, value: Any): DocumentFilter = DocumentFilter.Eq(key, value)