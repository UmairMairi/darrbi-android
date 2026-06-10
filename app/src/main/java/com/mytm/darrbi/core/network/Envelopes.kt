package com.mytm.darrbi.core.network

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.AppError
import kotlinx.serialization.Serializable

/**
 * The three response envelopes ride-android used, distinguished by different success discriminators.
 * Each [unwrap] converts the wire envelope to a typed [ApiResult].
 */

/** Main/REST + Dashboard APIs: success when `statusCode <= 300`. */
@Serializable
data class MainEnvelope<T>(
    val statusCode: Int? = null,
    val message: String? = null,
    val data: T? = null,
)

/** CMS API: success when `success == true`. */
@Serializable
data class CmsEnvelope<T>(
    val success: Boolean = false,
    val message: String? = null,
    val data: T? = null,
)

/** RideARide (Rental) API: success when `status == true`. */
@Serializable
data class RentalEnvelope<T>(
    val status: Boolean = false,
    val message: String? = null,
    val data: T? = null,
)

fun <T> MainEnvelope<T>.unwrap(): ApiResult<T> =
    if ((statusCode ?: 200) <= 300) data.toResult() else ApiResult.Error(statusCode ?: -1, message)

fun <T> CmsEnvelope<T>.unwrap(): ApiResult<T> =
    if (success) data.toResult() else ApiResult.Error(-1, message)

fun <T> RentalEnvelope<T>.unwrap(): ApiResult<T> =
    if (status) data.toResult() else ApiResult.Error(-1, message)

private fun <T> T?.toResult(): ApiResult<T> =
    this?.let { ApiResult.Success(it) } ?: ApiResult.Failure(AppError.Serialization("Empty response body"))

fun <T> ApiResult<MainEnvelope<T>>.unwrapMain(): ApiResult<T> = flatMap { it.unwrap() }

/** For endpoints whose body we don't need — success when `statusCode <= 300`. */
fun ApiResult<out MainEnvelope<*>>.unwrapMainUnit(): ApiResult<Unit> = when (this) {
    is ApiResult.Success -> if ((data.statusCode ?: 200) <= 300) {
        ApiResult.Success(Unit)
    } else {
        ApiResult.Error(data.statusCode ?: -1, data.message)
    }
    is ApiResult.Error -> this
    is ApiResult.Failure -> this
}
fun <T> ApiResult<CmsEnvelope<T>>.unwrapCms(): ApiResult<T> = flatMap { it.unwrap() }
fun <T> ApiResult<RentalEnvelope<T>>.unwrapRental(): ApiResult<T> = flatMap { it.unwrap() }

private inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(data)
    is ApiResult.Error -> this
    is ApiResult.Failure -> this
}
