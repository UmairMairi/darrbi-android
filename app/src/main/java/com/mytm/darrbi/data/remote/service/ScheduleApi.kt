package com.mytm.darrbi.data.remote.service

import com.mytm.darrbi.core.network.MainEnvelope
import com.mytm.darrbi.data.remote.dto.C2cCityDto
import com.mytm.darrbi.data.remote.dto.C2cOpenInRouteData
import com.mytm.darrbi.data.remote.dto.C2cQuoteData
import com.mytm.darrbi.data.remote.dto.CancelScheduleData
import com.mytm.darrbi.data.remote.dto.CancelScheduleRequest
import com.mytm.darrbi.data.remote.dto.CreateScheduleData
import com.mytm.darrbi.data.remote.dto.CreateScheduleRequest
import com.mytm.darrbi.data.remote.dto.UpcomingScheduleDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Scheduled City-to-City REST (api-gateway `/v2/schedule`, Main host — see
 * `V2_CITY_TO_CITY_MOBILE_INTEGRATION_GUIDE.md` §12). The bid/select lifecycle reuses the immediate
 * bidding endpoints on [V2RideApi] (`/v2/trips/:id/bids*`). `sessionid` header is added by the auth
 * interceptor.
 */
interface ScheduleApi {

    /** Selectable cities for the origin/destination pickers (alphabetical, active only). */
    @GET("v2/schedule/cities")
    suspend fun getCities(): MainEnvelope<List<C2cCityDto>>

    /** Fare band for a (origin, destination, cab, seats) tuple before creating. */
    @GET("v2/schedule/quote")
    suspend fun getQuote(
        @Query("originCityId") originCityId: String,
        @Query("destinationCityId") destinationCityId: String,
        @Query("cabId") cabId: String,
        @Query("seats") seats: Int,
    ): MainEnvelope<C2cQuoteData>

    /** Rider creates an open scheduled C2C request → opens for bids immediately (`scheduledState 1`). */
    @POST("v2/schedule/city-to-city")
    suspend fun createCityToCity(@Body body: CreateScheduleRequest): MainEnvelope<CreateScheduleData>

    /** Rider/driver: matched + open future C2C trips, ordered by departure. */
    @GET("v2/schedule/my-upcoming")
    suspend fun getMyUpcoming(): MainEnvelope<List<UpcomingScheduleDto>>

    /** Driver: open (awaiting-bids) C2C requests on a route, ordered by departure (guide §7.2). */
    @GET("v2/schedule/open-in-route")
    suspend fun getOpenInRoute(
        @Query("originCityId") originCityId: String? = null,
        @Query("destinationCityId") destinationCityId: String? = null,
    ): MainEnvelope<C2cOpenInRouteData>

    /** Rider cancels a scheduled trip (window/fee aware). */
    @PATCH("v2/schedule/{tripId}/cancel")
    suspend fun cancel(
        @Path("tripId") tripId: String,
        @Body body: CancelScheduleRequest,
    ): MainEnvelope<CancelScheduleData>
}
