package com.mytm.darrbi.data.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.DeviceInfoProvider
import com.mytm.darrbi.data.remote.service.AuthApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

class AuthRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            explicitNulls = false
        }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
        repository = AuthRepositoryImpl(
            authApi = api,
            deviceInfoProvider = object : DeviceInfoProvider { override val deviceId = "test-device" },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `requestOtp returns Success with transaction id`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"statusCode":200,"data":{"tId":"TX-1","SmsApiResponse":{"message":"sent"}}}""",
            ),
        )
        val result = repository.requestOtp("0500000000")
        assertTrue(result is ApiResult.Success)
        assertEquals("TX-1", (result as ApiResult.Success).data.transactionId)
    }

    @Test
    fun `requestOtp returns Error when statusCode over 300`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"statusCode":400,"message":"Invalid number"}"""),
        )
        val result = repository.requestOtp("0500000000")
        assertTrue(result is ApiResult.Error)
        assertEquals(400, (result as ApiResult.Error).code)
    }

    @Test
    fun `verifyOtp maps token and userId`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"statusCode":200,"data":{"token":"JWT","details":{"id":"u1"}}}""",
            ),
        )
        val result = repository.verifyOtp("0500000000", "TX-1", "1234", "en")
        assertTrue(result is ApiResult.Success)
        assertEquals("JWT", (result as ApiResult.Success).data.token)
        assertEquals("u1", result.data.userId)
    }

    @Test
    fun `requestOtp returns Failure when server is unreachable`() = runTest {
        server.shutdown() // force a connection failure
        val result = repository.requestOtp("0500000000")
        assertTrue(result is ApiResult.Failure)
    }
}
