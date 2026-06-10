package com.mytm.darrbi.core.common

/**
 * Typed result of a network call. Replaces ride-android's callback `ResponseListener` + `JSONObject`.
 *
 * - [Success] — request succeeded and the body unwrapped to [data].
 * - [Error]   — server reported a business/HTTP error with a [code] and optional [message].
 * - [Failure] — the call could not complete (network/timeout/serialization), described by [AppError].
 */
sealed interface ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>
    data class Error(val code: Int, val message: String?) : ApiResult<Nothing>
    data class Failure(val error: AppError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Error -> this
    is ApiResult.Failure -> this
}

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(data)
    return this
}

inline fun <T> ApiResult<T>.onError(action: (code: Int, message: String?) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) action(code, message)
    return this
}

inline fun <T> ApiResult<T>.onFailure(action: (AppError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) action(error)
    return this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data
