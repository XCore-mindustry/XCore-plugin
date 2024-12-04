package io.github.xcore.common.command.annotation

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE_PARAMETER)
annotation class Named(val value: String)
