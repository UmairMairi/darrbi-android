package com.mytm.darrbi.data.remote.service

import com.mytm.darrbi.core.network.MainEnvelope
import com.mytm.darrbi.data.remote.dto.CabTypeData
import com.mytm.darrbi.data.remote.dto.CreateTripData
import com.mytm.darrbi.data.remote.dto.CreateTripRequest
import com.mytm.darrbi.data.remote.dto.PromoData
import com.mytm.darrbi.data.remote.dto.PromoValidateRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Rider ride-booking endpoints (Main API): cab list + fare, promo validation, trip creation. */
interface RideApi {

    @GET("master/cab-type/all")
    suspend fun getCabTypes(
        @Query("originAddressLat") originLat: String,
        @Query("originAddressLng") originLng: String,
        @Query("destinationAddressLat") destinationLat: String,
        @Query("destinationAddressLng") destinationLng: String,
    ): MainEnvelope<CabTypeData>

    @POST("promo-code/validate/")
    suspend fun validatePromo(@Body body: PromoValidateRequest): MainEnvelope<PromoData>

    @POST("trips")
    suspend fun createTrip(@Body body: CreateTripRequest): MainEnvelope<CreateTripData>

    @PUT("trips/cancel-trip-request/{tripId}")
    suspend fun cancelTripRequest(@Path("tripId") tripId: String): MainEnvelope<JsonElement>
}
