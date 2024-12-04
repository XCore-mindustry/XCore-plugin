package io.github.xcore.common.command

import io.github.xcore.common.command.annotation.Command
import io.github.xcore.common.command.annotation.Named
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.*

abstract class CommandManager<A : Actor> {

    private val commands = mutableMapOf<String, AnnotationCommand<A>>()
    private val parsers = mutableMapOf<KClass<*>, ArgumentResolver.Factory<A, *>>()

    fun parse(container: Any) {
        for (function in container::class.declaredMemberFunctions) {
            val annotation = function.findAnnotation<Command>() ?: continue
            val duplicate = commands[annotation.value]
            if (duplicate != null) {
                error("Duplicate command for ${annotation.value}: ${duplicate.function.pretty()}, ${function.pretty()}")
            }
            var wasOptional = false
            val parameters = function.parameters.map { parameter ->
                if (parameter.index == 0) {
                    return@map CommandArgument(parameter, "__container", StaticResolver<A, Any>(container))
                }
                val name = parameter.findAnnotation<Named>()?.value
                    ?: parameter.name
                    ?: error("Parameter ${parameter.index} of ${function.pretty()} must be annotated with @Named, or jvm parameter names must be enabled")
                val clazz = (parameter.type.classifier as? KClass<*>)
                    ?: error("Parameter $name of ${function.pretty()} must be a simple type: got ${parameter.type} instead")
                val factory = findResolverFactory(clazz)
                    ?: error("Parameter $name of ${function.pretty()} has an unknown type ${clazz.simpleName}")
                CommandArgument(parameter, name, factory.create(parameter)).also {
                    if (!it.optional && wasOptional) {
                        error("Required parameter $name of ${function.pretty()} is after an optional parameter")
                    }
                    wasOptional = it.optional
                }
            }
            val command = AnnotationCommand(function, annotation.value, parameters)
            commands[command.name] = command
            onCommandRegistered(command)
        }
    }

    protected abstract fun onCommandRegistered(command: AnnotationCommand<A>)

    private fun <T : Any> findResolverFactory(clazz: KClass<T>): ArgumentResolver.Factory<A, out T>? {
        val visited = mutableSetOf<KClass<*>>()
        val queue = ArrayDeque(clazz.superclasses)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            @Suppress("UNCHECKED_CAST")
            if (parsers.containsKey(current)) return parsers[current] as ArgumentResolver.Factory<A, out T>
            visited.add(current)
            queue.addAll(current.superclasses.filterNot { it in visited })
        }
        return null
    }

    private fun KFunction<*>.pretty() = "${this.instanceParameter!!::class.simpleName}#${this.name}"

    protected data class AnnotationCommand<A : Actor>(
        val function: KFunction<*>,
        val name: String,
        val arguments: List<CommandArgument<A, *>>
    )

    protected data class CommandArgument<A : Actor, T : Any>(
        val parameter: KParameter,
        val name: String,
        val resolver: ArgumentResolver<A, T>,
    ) {
        val optional: Boolean get() = parameter.isOptional || parameter.type.isMarkedNullable
    }
}