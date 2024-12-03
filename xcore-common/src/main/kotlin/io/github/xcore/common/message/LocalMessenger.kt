package io.github.xcore.common.message

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass
import kotlin.time.Duration

class LocalMessenger : Messenger {

    private val subscriptions = ConcurrentHashMap<KClass<*>, MutableList<Messenger.Listener<*, *>>>()

    override fun <M : Message<R>, R : Any> subscribe(
        type: KClass<M>,
        listener: Messenger.Listener<M, R>
    ): Messenger.Subscription {
        subscriptions.getOrPut(type, ::CopyOnWriteArrayList).add(listener)
        return object : Messenger.Subscription {
            override fun unsubscribe() {
                subscriptions[type]?.remove(listener)
            }
        }
    }

    override fun <R : Any> publish(message: Message<R>, timeout: Duration) = flow {
        withTimeout(timeout) {
            for (listener in (subscriptions[message::class] ?: emptyList())) {
                @Suppress("UNCHECKED_CAST")
                emit((listener as Messenger.Listener<Message<R>, R>).onMessage(message))
            }
        }
    }
}