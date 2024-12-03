package io.github.xcore.common.message

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface Messenger {
    fun <M : Message<R>, R : Any> subscribe(type: KClass<M>, listener: Listener<M, R>): Subscription
    fun <R : Any> publish(message: Message<R>, timeout: Duration = 5.seconds): Flow<R>

    fun interface Listener<M : Message<R>, R : Any> {
        fun onMessage(message: M): R
    }

    interface Subscription {
        fun unsubscribe()
    }
}
