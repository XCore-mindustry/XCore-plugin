package io.github.xcore.common.command

import io.github.xcore.common.function.XResult
import io.github.xcore.common.function.failure
import io.github.xcore.common.function.success
import io.github.xcore.common.translation.Translatable
import java.util.Queue
import kotlin.reflect.KAnnotatedElement

interface ArgumentResolver<A : Actor, T : Any> {
    fun resolve(actor: A, input: Queue<String>): XResult<T, Translatable>

    val size: Int get() = 1

    interface Factory<A : Actor, T : Any> {
        fun create(annotations: KAnnotatedElement): ArgumentResolver<A, T>
    }
}

class StaticResolver<A : Actor, T : Any>(private val value: T) : ArgumentResolver<A, T> {
    override val size get() = 0
    override fun resolve(actor: A, input: Queue<String>): XResult<T, Translatable> = success(value)
}

class ActorResolver<A : Actor> : ArgumentResolver<A, A> {
    override val size get() = 0
    override fun resolve(actor: A, input: Queue<String>): XResult<A, Translatable> = success(actor)
}

class StringResolver(private val greedy: Boolean) : ArgumentResolver<Actor, String> {
    override val size get() = if (greedy) -1 else 1
    override fun resolve(actor: Actor, input: Queue<String>): XResult<String, Translatable> =
        if (greedy) {
            success(input.joinToString(" ").also { input.clear() })
        } else {
            success(input.remove())
        }
}

object IntResolver : ArgumentResolver<Actor, Int> {
    override fun resolve(actor: Actor, input: Queue<String>): XResult<Int, Translatable> =
        input.remove().toIntOrNull()?.let(::success) ?: failure(Translatable("error-argparse-int"))
}
