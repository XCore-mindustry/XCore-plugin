package io.github.xcore.plugin.database

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.Flow
import org.bson.conversions.Bson
import kotlin.reflect.KClass

class MongoDocumentDatabase(private val database: MongoDatabase) : DocumentDatabase {
    override fun <T : Any> getCollection(name: String, type: KClass<T>): DocumentCollection<T> =
        MongoDocumentCollection(database.getCollection(name, type.java))
}

class MongoDocumentCollection<T : Any>(private val collection: MongoCollection<T>) : DocumentCollection<T> {

    override suspend fun select(filter: DocumentFilter): Flow<T> =
        collection.find(filter.toBson())

    override suspend fun insert(document: T) {
        collection.insertOne(document)
    }

    override suspend fun upsert(filter: DocumentFilter, document: T) {
        collection.replaceOne(filter.toBson(), document, ReplaceOptions().upsert(true))
    }

    override suspend fun delete(filter: DocumentFilter) =
        collection.deleteOne(filter.toBson()).deletedCount.toInt()

    private fun DocumentFilter.toBson(): Bson = when (this) {
        is DocumentFilter.Eq -> Filters.eq(key, value)
    }
}