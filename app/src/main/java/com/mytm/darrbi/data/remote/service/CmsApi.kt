package com.mytm.darrbi.data.remote.service

import com.mytm.darrbi.core.network.CmsEnvelope
import com.mytm.darrbi.data.remote.dto.ReportGroupDto
import com.mytm.darrbi.data.remote.dto.StoreRatingRequest
import com.mytm.darrbi.data.remote.dto.UserReportsRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.POST

/** CMS API (support tickets / reports), success-based [CmsEnvelope] responses. */
interface CmsApi {
    @POST("api/v1/ticket/by-customer")
    suspend fun getUserReports(@Body body: UserReportsRequest): CmsEnvelope<List<ReportGroupDto>>

    @POST("api/v1/ticket/store-ratings")
    suspend fun storeRating(@Body body: StoreRatingRequest): CmsEnvelope<JsonElement>
}
