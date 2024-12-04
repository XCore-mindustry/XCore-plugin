package io.github.xcore.common.function

interface XResult<R : Any, E : Any> {
    val success: R
    val isSuccess: Boolean
    val failure: E
    val isFailure: Boolean

    data class Success<R : Any, E : Any>(override val success: R) : XResult<R, E> {
        override val isSuccess = true
        override val isFailure = false
        override val failure: E get() = throw UnsupportedOperationException()
    }

    data class Failure<R : Any, E : Any>(override val failure: E) : XResult<R, E> {
        override val isSuccess = false
        override val isFailure = true
        override val success: R get() = throw UnsupportedOperationException()
    }
}

fun <R : Any, E : Any> success(value: R): XResult<R, E> = XResult.Success(value)

fun <R : Any, E : Any> failure(value: E): XResult<R, E> = XResult.Failure(value)
