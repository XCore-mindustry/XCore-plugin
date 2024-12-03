package io.github.xcore.common.database

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.github.xcore.common.lifecycle.LifecycleListener
import io.github.xcore.common.lifecycle.LifecycleManager
import kotlinx.coroutines.flow.Flow
import org.bson.conversions.Bson
import kotlin.reflect.KClass

internal class MongoDocumentDatabase(
    lifecycle: LifecycleManager,
    private val config: DocumentDatabaseConfig.MongoDB
) : DocumentDatabase, LifecycleListener {

    private lateinit var client: MongoClient

    init {
        lifecycle.addInitListener {
            client = MongoClient.create(config.uri)
        }

        lifecycle.addExitListener {
            client.close()
        }
    }

    override fun <T : Any> getCollection(name: String, type: KClass<T>): DocumentCollection<T> =
        MongoDocumentCollection(client.getDatabase(config.database).getCollection(name, type.java))
}

internal class MongoDocumentCollection<T : Any>(private val collection: MongoCollection<T>) : DocumentCollection<T> {

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