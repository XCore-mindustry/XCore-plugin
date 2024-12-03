package io.github.xcore.common.lifecycle

interface LifecycleListener {
    fun onInit() = Unit
    fun onExit() = Unit
}
