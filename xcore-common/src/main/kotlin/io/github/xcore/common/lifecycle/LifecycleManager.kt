package io.github.xcore.common.lifecycle

interface LifecycleManager {
    fun register(listener: LifecycleListener)

    fun addInitListener(listener: Runnable)

    fun addExitListener(listener: Runnable)
}