package com.mytm.darrbi.core.network

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.AppError
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class SafeApiCallTest {

    @Test
    fun `returns Success on normal completion`() = runTest {
        assertEquals(ApiResult.Success(42), safeApiCall { 42 })
    }

    @Test
    fun `maps IOException to Network failure`() = runTest {
        val result = safeApiCall<Int> { throw IOException("boom") }
        assertTrue(result is ApiResult.Failure && result.error is AppError.Network)
    }

    @Test
    fun `maps SocketTimeoutException to Timeout failure`() = runTest {
        val result = safeApiCall<Int> { throw SocketTimeoutException("slow") }
        assertTrue(result is ApiResult.Failure && result.error is AppError.Timeout)
    }

    @Test
    fun `maps HTTP 401 to Unauthorized failure`() = runTest {
        val result = safeApiCall<Int> { throw httpException(401) }
        assertTrue(result is ApiResult.Failure && result.error is AppError.Unauthorized)
    }

    @Test
    fun `maps HTTP 500 to Server failure`() = runTest {
        val result = safeApiCall<Int> { throw httpException(500) }
        assertTrue(result is ApiResult.Failure && result.error is AppError.Server)
    }

    @Test
    fun `maps other HTTP errors to Error with code`() = runTest {
        val result = safeApiCall<Int> { throw httpException(422) }
        assertTrue(result is ApiResult.Error && result.code == 422)
    }

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Int>(code, "".toResponseBody("application/json".toMediaType())))
}
