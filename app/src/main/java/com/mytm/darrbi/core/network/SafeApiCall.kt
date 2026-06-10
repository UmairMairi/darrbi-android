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
        when (val code = e.code()) {
            401 -> ApiResult.Failure(AppError.Unauthorized(e.message()))
            in 500..599 -> ApiResult.Failure(AppError.Server(code, e.message()))
            else -> ApiResult.Error(code, e.message())
        }
    } catch (e: IOException) {
        ApiResult.Failure(AppError.Network(e.message))
    } catch (e: SerializationException) {
        ApiResult.Failure(AppError.Serialization(e.message))
    } catch (e: Exception) {
        ApiResult.Failure(AppError.Unknown(e.message))
    }
