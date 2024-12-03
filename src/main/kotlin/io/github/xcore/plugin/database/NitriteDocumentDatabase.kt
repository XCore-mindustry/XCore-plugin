package io.github.xcore.plugin.database


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.UpdateOptions
import org.dizitart.no2.filters.Filter
import org.dizitart.no2.filters.FluentFilter
import org.dizitart.no2.repository.ObjectRepository
import kotlin.reflect.KClass

internal class NitriteDocumentDatabase(private val database: Nitrite) : DocumentDatabase {
    override fun <T : Any> getCollection(name: String, type: KClass<T>): DocumentCollection<T> =
        NitriteDocumentCollection(database.getRepository(type.java))
}

internal class NitriteDocumentCollection<T : Any>(private val collection: ObjectRepository<T>) : DocumentCollection<T> {

    override suspend fun select(filter: DocumentFilter): Flow<T> = withContext(Dispatchers.IO) {
        collection.find(filter.toNitriteFilter()).asFlow()
    }

    override suspend fun insert(document: T): Unit = withContext(Dispatchers.IO) {
        collection.insert(document)
    }

    override suspend fun upsert(filter: DocumentFilter, document: T): Unit = withContext(Dispatchers.IO) {
        collection.update(filter.toNitriteFilter(), document, UpdateOptions.updateOptions(true))
    }

    override suspend fun delete(filter: DocumentFilter): Int = withContext(Dispatchers.IO) {
        collection.remove(filter.toNitriteFilter()).affectedCount
    }

    private fun DocumentFilter.toNitriteFilter(): Filter = when (this) {
        is DocumentFilter.Eq -> FluentFilter.where(key).eq(value)
    }
}