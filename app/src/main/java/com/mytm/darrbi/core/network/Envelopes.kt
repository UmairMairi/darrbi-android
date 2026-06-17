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

/** V2 dispatch/bidding REST envelope: success when `ok == true`; failures carry a coded [V2Error]. */
@Serializable
data class V2Envelope<T>(
    val ok: Boolean = false,
    val data: T? = null,
    val error: V2Error? = null,
)

@Serializable
data class V2Error(
    val code: String? = null,
    val message: String? = null,
    /** For `DRIVER_INELIGIBLE` — which online/approval gates failed. */
    val failedConditions: List<String>? = null,
)

fun <T> MainEnvelope<T>.unwrap(): ApiResult<T> =
    if ((statusCode ?: 200) <= 300) data.toResult() else ApiResult.Error(statusCode ?: -1, message)

fun <T> CmsEnvelope<T>.unwrap(): ApiResult<T> =
    if (success) data.toResult() else ApiResult.Error(-1, message)

fun <T> RentalEnvelope<T>.unwrap(): ApiResult<T> =
    if (status) data.toResult() else ApiResult.Error(-1, message)

/** Success when `ok`; on failure the coded [V2Error.code] is carried in [ApiResult.Error.message]. */
fun <T> V2Envelope<T>.unwrap(): ApiResult<T> =
    if (ok) data.toResult() else ApiResult.Error(-1, error?.code ?: error?.message)

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
fun <T> ApiResult<V2Envelope<T>>.unwrapV2(): ApiResult<T> = flatMap { it.unwrap() }

/** For V2 endpoints whose body we don't need — success when `ok`. */
fun ApiResult<out V2Envelope<*>>.unwrapV2Unit(): ApiResult<Unit> = when (this) {
    is ApiResult.Success -> if (data.ok) ApiResult.Success(Unit) else ApiResult.Error(-1, data.error?.code ?: data.error?.message)
    is ApiResult.Error -> this
    is ApiResult.Failure -> this
}

private inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(data)
    is ApiResult.Error -> this
    is ApiResult.Failure -> this
}
