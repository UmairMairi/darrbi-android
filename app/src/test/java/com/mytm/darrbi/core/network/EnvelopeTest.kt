package com.mytm.darrbi.core.network

import com.mytm.darrbi.core.common.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeTest {

    @Test
    fun `main envelope succeeds when statusCode at or under 300`() {
        assertEquals(ApiResult.Success("ok"), MainEnvelope(statusCode = 200, data = "ok").unwrap())
    }

    @Test
    fun `main envelope errors when statusCode over 300`() {
        val result = MainEnvelope<String>(statusCode = 400, message = "bad").unwrap()
        assertTrue(result is ApiResult.Error && result.code == 400 && result.message == "bad")
    }

    @Test
    fun `cms envelope keys off success flag`() {
        assertEquals(ApiResult.Success("x"), CmsEnvelope(success = true, data = "x").unwrap())
        assertTrue(CmsEnvelope<String>(success = false, message = "no").unwrap() is ApiResult.Error)
    }

    @Test
    fun `rental envelope keys off status flag`() {
        assertEquals(ApiResult.Success("y"), RentalEnvelope(status = true, data = "y").unwrap())
        assertTrue(RentalEnvelope<String>(status = false).unwrap() is ApiResult.Error)
    }

    @Test
    fun `unwrapMain flattens a Success-wrapped envelope`() {
        val wrapped: ApiResult<MainEnvelope<String>> =
            ApiResult.Success(MainEnvelope(statusCode = 201, data = "z"))
        assertEquals(ApiResult.Success("z"), wrapped.unwrapMain())
    }
}
