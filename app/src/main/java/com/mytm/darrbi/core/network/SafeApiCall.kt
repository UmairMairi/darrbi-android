package com.mytm.darrbi.core.network

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.AppError
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a network [block] and maps any thrown exception / HTTP error to a typed [ApiResult].
 * Cancellation is rethrown so coroutine cancellation still works.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: SocketTimeoutException) {
        ApiResult.Failure(AppError.Timeout(e.message))
    } catch (e: HttpException) {
        // House error body on a real HTTP 4xx/5xx: { statusCode, message, data:{ code, …details } }.
        // Surface the machine `code` (so callers can map e.g. BID_BELOW_FLOOR / PAYMENT_HOLD_FAILED),
        // falling back to the human message then the bare reason phrase.
        val (errCode, errMessage) = parseHouseError(runCatching { e.response()?.errorBody()?.string() }.getOrNull())
        when (val code = e.code()) {
            401 -> ApiResult.Failure(AppError.Unauthorized(errMessage ?: e.message()))
            in 500..599 -> ApiResult.Failure(AppError.Server(code, errMessage ?: e.message()))
            else -> ApiResult.Error(code, errCode ?: errMessage ?: e.message())
        }
    } catch (e: IOException) {
        ApiResult.Failure(AppError.Network(e.message))
    } catch (e: SerializationException) {
        ApiResult.Failure(AppError.Serialization(e.message))
    } catch (e: Exception) {
        ApiResult.Failure(AppError.Unknown(e.message))
    }

/** Extracts (machine `code` from `data.code`, human `message`) from a house error body, or (null, null). */
private fun parseHouseError(body: String?): Pair<String?, String?> {
    if (body.isNullOrBlank()) return null to null
    return runCatching {
        val json = org.json.JSONObject(body)
        val message = json.optString("message").takeIf { it.isNotBlank() }
        val code = json.optJSONObject("data")?.optString("code")?.takeIf { it.isNotBlank() }
        code to message
    }.getOrDefault(null to null)
}
