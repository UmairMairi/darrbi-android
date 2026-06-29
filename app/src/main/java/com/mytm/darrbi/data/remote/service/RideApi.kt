package com.mytm.darrbi.data.remote.service

import com.mytm.darrbi.core.network.MainEnvelope
import com.mytm.darrbi.data.remote.dto.CabCategoryDto
import com.mytm.darrbi.data.remote.dto.CabTypeData
import com.mytm.darrbi.data.remote.dto.ChangeDestinationRequest
import com.mytm.darrbi.data.remote.dto.CompleteTripRequest
import com.mytm.darrbi.data.remote.dto.CreateTripData
import com.mytm.darrbi.data.remote.dto.DeclineTripRequest
import com.mytm.darrbi.data.remote.dto.CreateTripRequest
import com.mytm.darrbi.data.remote.dto.OngoingTripData
import com.mytm.darrbi.data.remote.dto.PromoData
import com.mytm.darrbi.data.remote.dto.PromoValidateRequest
import com.mytm.darrbi.data.remote.dto.RecentAddressesData
import com.mytm.darrbi.data.remote.dto.RejectedReasonDto
import com.mytm.darrbi.data.remote.dto.ReviewRequest
import com.mytm.darrbi.data.remote.dto.StartTripRequest
import com.mytm.darrbi.data.remote.dto.TripExistsData
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Rider ride-booking endpoints (Main API): cab list + fare, promo validation, trip creation. */
interface RideApi {

    /**
     * Rider home service categories (Darrbi Taxi, Car Rental, Cargo, …). Each carries its display name +
     * icon URL; the home grid renders them with the last odd tile spanning full width.
     */
    @GET("captains/cab-type-category/all")
    suspend fun getCabCategories(): MainEnvelope<List<CabCategoryDto>>

    /** Rider's recent trip addresses (the list is nested under `data.recentAddresses`). */
    @GET("trips/rider-recent-addresses")
    suspend fun getRecentAddresses(): MainEnvelope<RecentAddressesData>

    @GET("master/cab-type/all")
    suspend fun getCabTypes(
        @Query("originAddressLat") originLat: String,
        @Query("originAddressLng") originLng: String,
        @Query("destinationAddressLat") destinationLat: String,
        @Query("destinationAddressLng") destinationLng: String,
        /** Service category chosen on the home screen; omitted when null (returns all cab types). */
        @Query("categoryId") categoryId: String? = null,
    ): MainEnvelope<CabTypeData>

    @POST("promo-code/validate/")
    suspend fun validatePromo(@Body body: PromoValidateRequest): MainEnvelope<PromoData>

    @POST("trips")
    suspend fun createTrip(@Body body: CreateTripRequest): MainEnvelope<CreateTripData>

    @PUT("trips/cancel-trip-request/{tripId}")
    suspend fun cancelTripRequest(@Path("tripId") tripId: String): MainEnvelope<JsonElement>

    /** Commits a new drop-off for an active trip. */
    @PATCH("trips/change-destination/{tripId}")
    suspend fun changeDestination(
        @Path("tripId") tripId: String,
        @Body body: ChangeDestinationRequest,
    ): MainEnvelope<JsonElement>

    /** Submits the rider's star rating for the captain after a completed trip. */
    @POST("reviews/rider")
    suspend fun rateDriver(@Body body: ReviewRequest): MainEnvelope<JsonElement>

    /** Submits the captain's star rating for the rider after a completed trip (`POST reviews/driver`). */
    @POST("reviews/driver")
    suspend fun rateRider(@Body body: ReviewRequest): MainEnvelope<JsonElement>

    /** CAPTAIN accepts an incoming ride request. */
    @PATCH("trips/driver-accepted/{tripId}")
    suspend fun driverAcceptTrip(@Path("tripId") tripId: String): MainEnvelope<JsonElement>

    /** CAPTAIN declines an incoming ride request. */
    @PATCH("trips/driver-rejected/{tripId}")
    suspend fun driverRejectTrip(@Path("tripId") tripId: String, @Body body: DeclineTripRequest): MainEnvelope<JsonElement>

    /** CAPTAIN reached the pickup point. */
    @PATCH("trips/driver-reached-at-pickup-point/{tripId}")
    suspend fun driverReachedPickup(@Path("tripId") tripId: String): MainEnvelope<JsonElement>

    /** CAPTAIN starts the trip after verifying the rider's OTP (`PATCH trips/started/{tripId}`). */
    @PATCH("trips/started/{tripId}")
    suspend fun startTrip(@Path("tripId") tripId: String, @Body body: StartTripRequest): MainEnvelope<JsonElement>

    /** CAPTAIN completes the trip at the drop-off (`PATCH trips/completed/{tripId}`). */
    @PATCH("trips/completed/{tripId}")
    suspend fun completeTrip(@Path("tripId") tripId: String, @Body body: CompleteTripRequest): MainEnvelope<JsonElement>

    /** CAPTAIN cancels an accepted trip (before/while heading to pickup). */
    @PATCH("trips/driver-cancelled/{tripId}")
    suspend fun driverCancelTrip(@Path("tripId") tripId: String, @Body body: DeclineTripRequest): MainEnvelope<JsonElement>

    /** RIDER cancels an accepted trip with a reason. */
    @PATCH("trips/rider-cancelled/{tripId}")
    suspend fun riderCancelTrip(@Path("tripId") tripId: String, @Body body: DeclineTripRequest): MainEnvelope<JsonElement>

    /** Cancellation reasons for the picker — `reasonType` 2 = captain, 3 = rider. */
    @GET("master/rejected-reason/type/{reasonType}")
    suspend fun getCancelReasons(@Path("reasonType") reasonType: Int): MainEnvelope<List<RejectedReasonDto>>

    /** Returns the active trip id when a ride is in progress (used to restore the screen on dashboard entry). */
    @GET("trips/exists")
    suspend fun checkTripExists(): MainEnvelope<TripExistsData>

    /** Live trip snapshot for [tripId] (same shape as the `trip-detail` socket push). */
    @GET("trips/socket/{tripId}")
    suspend fun getOngoingTripDetail(@Path("tripId") tripId: String): MainEnvelope<OngoingTripData>

    /**
     * Uploads the rendered trip static-map image (multipart) — mirrors ride-android's
     * `PATCH /trips/upload-photo/{trip_id}`. Submitted by the rider + captain on each trip step;
     * [type] is the trip-status code ([com.mytm.darrbi.domain.model.TripImageType]).
     */
    @Multipart
    @PATCH("trips/upload-photo/{tripId}")
    suspend fun uploadTripStaticMapImage(
        @Path("tripId") tripId: String,
        @Part riderPhoto: MultipartBody.Part,
        @Part driverPhoto: MultipartBody.Part,
        @Part("type") type: RequestBody,
    ): MainEnvelope<JsonElement>
}
