package io.github.xcore.common.database

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

interface DocumentDatabase {

    fun <T : Any> getCollection(name: String, type: KClass<T>): DocumentCollection<T>
}

interface DocumentCollection<T : Any> {

    suspend fun select(filter: DocumentFilter): Flow<T>

    suspend fun insert(document: T)

    suspend fun upsert(filter: DocumentFilter, document: T)

    suspend fun delete(filter: DocumentFilter): Int
}
