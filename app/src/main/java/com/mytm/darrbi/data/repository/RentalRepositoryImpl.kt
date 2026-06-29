package com.mytm.darrbi.data.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.map
import com.mytm.darrbi.core.network.safeApiCall
import com.mytm.darrbi.core.network.unwrapMain
import com.mytm.darrbi.core.network.unwrapMainUnit
import com.mytm.darrbi.data.mapper.toDomain
import com.mytm.darrbi.data.remote.dto.RacBookingRequest
import com.mytm.darrbi.data.remote.dto.RacCancelRequest
import com.mytm.darrbi.data.remote.dto.RacExtendRequest
import com.mytm.darrbi.data.remote.dto.RacNafathRequest
import com.mytm.darrbi.data.remote.dto.RacQuoteRequest
import com.mytm.darrbi.data.remote.dto.RacRateRequest
import com.mytm.darrbi.data.remote.service.RacApi
import com.mytm.darrbi.domain.model.ExtensionResult
import com.mytm.darrbi.domain.model.NafathResult
import com.mytm.darrbi.domain.model.RentalBooking
import com.mytm.darrbi.domain.model.RentalBookingDetail
import com.mytm.darrbi.domain.model.RentalCancelResult
import com.mytm.darrbi.domain.model.RentalFilters
import com.mytm.darrbi.domain.model.RentalQuote
import com.mytm.darrbi.domain.model.RentalReview
import com.mytm.darrbi.domain.model.RentalSearchResult
import com.mytm.darrbi.domain.model.RentalVehicleDetail
import com.mytm.darrbi.domain.repository.RentalRepository
import javax.inject.Inject

class RentalRepositoryImpl @Inject constructor(
    private val api: RacApi,
) : RentalRepository {

    override suspend fun getFilters(): ApiResult<RentalFilters> =
        safeApiCall { api.getFilters() }.unwrapMain().map { it.toDomain() }

    override suspend fun searchVehicles(
        city: String?,
        category: String?,
        pickupAtIso: String?,
        returnAtIso: String?,
        seats: Int?,
        transmission: String?,
        sortBy: String?,
        page: Int?,
        limit: Int?,
    ): ApiResult<RentalSearchResult> =
        safeApiCall {
            api.searchVehicles(
                city = city?.takeIf { it.isNotBlank() },
                category = category?.takeIf { it.isNotBlank() },
                pickupAt = pickupAtIso?.takeIf { it.isNotBlank() },
                returnAt = returnAtIso?.takeIf { it.isNotBlank() },
                seats = seats,
                transmission = transmission?.takeIf { it.isNotBlank() },
                sortBy = sortBy?.takeIf { it.isNotBlank() },
                page = page,
                limit = limit,
            )
        }.unwrapMain().map { data ->
            val items = data.items.orEmpty().mapNotNull { it.toDomain() }
            RentalSearchResult(items = items, total = data.total ?: items.size)
        }

    override suspend fun getVehicle(vehicleId: String): ApiResult<RentalVehicleDetail> =
        safeApiCall { api.getVehicle(vehicleId) }.unwrapMain().flatMapToDomain { it.toDomain() }

    override suspend fun getQuote(
        vehicleId: String,
        pickupAtIso: String,
        returnAtIso: String,
        cdwTier: String,
        addonIds: List<String>,
        couponCode: String?,
    ): ApiResult<RentalQuote> =
        safeApiCall {
            api.getQuote(
                RacQuoteRequest(
                    vehicleId = vehicleId,
                    pickupAt = pickupAtIso,
                    returnAt = returnAtIso,
                    cdwTier = cdwTier,
                    addonIds = addonIds,
                    couponCode = couponCode?.takeIf { it.isNotBlank() },
                ),
            )
        }.unwrapMain().map { it.toDomain() }

    override suspend fun getCompanyReviews(companyId: String, limit: Int?): ApiResult<List<RentalReview>> =
        safeApiCall { api.getCompanyReviews(companyId, limit) }.unwrapMain().map { list -> list.map { it.toDomain() } }

    override suspend fun verifyNafath(nationalId: String): ApiResult<NafathResult> =
        safeApiCall { api.verifyNafath(RacNafathRequest(nationalId)) }.unwrapMain().map { it.toDomain() }

    override suspend fun createBooking(
        vehicleId: String,
        pickupAtIso: String,
        returnAtIso: String,
        cdwTier: String,
        addonIds: List<String>,
        couponCode: String?,
        nafathVerified: Boolean,
        renterNationalId: String?,
        renterLicenseNo: String?,
    ): ApiResult<RentalBooking> =
        safeApiCall {
            api.createBooking(
                RacBookingRequest(
                    vehicleId = vehicleId,
                    pickupAt = pickupAtIso,
                    returnAt = returnAtIso,
                    cdwTier = cdwTier,
                    addonIds = addonIds,
                    couponCode = couponCode?.takeIf { it.isNotBlank() },
                    nafathVerified = nafathVerified,
                    renterNationalId = renterNationalId?.takeIf { it.isNotBlank() },
                    renterLicenseNo = renterLicenseNo?.takeIf { it.isNotBlank() },
                ),
            )
        }.unwrapMain().flatMapToDomain { it.toDomain() }

    override suspend fun getBookings(status: String?): ApiResult<List<RentalBooking>> =
        safeApiCall { api.getBookings(status?.takeIf { it.isNotBlank() }) }
            .unwrapMain().map { data -> data.items.orEmpty().mapNotNull { it.toDomain() } }

    override suspend fun getBooking(bookingId: String): ApiResult<RentalBookingDetail> =
        safeApiCall { api.getBooking(bookingId) }.unwrapMain().flatMapToDomain { it.toDomain() }

    override suspend fun cancelBooking(bookingId: String, reason: String?): ApiResult<RentalCancelResult> =
        safeApiCall { api.cancelBooking(bookingId, RacCancelRequest(reason?.takeIf { it.isNotBlank() })) }
            .unwrapMain().map { it.toDomain() }

    override suspend fun extendBooking(bookingId: String, additionalHours: Int): ApiResult<ExtensionResult> =
        safeApiCall { api.extendBooking(bookingId, RacExtendRequest(additionalHours)) }
            .unwrapMain().flatMapToDomain { it.toDomain() }

    override suspend fun rateBooking(bookingId: String, rating: Int, text: String?): ApiResult<Unit> =
        safeApiCall { api.rateBooking(bookingId, RacRateRequest(rating, text?.takeIf { it.isNotBlank() })) }
            .unwrapMainUnit()

    /** Like [map] but the transform may return null (e.g. a row missing its id) → a serialization Failure. */
    private inline fun <T, R> ApiResult<T>.flatMapToDomain(transform: (T) -> R?): ApiResult<R> = when (this) {
        is ApiResult.Success -> transform(data)?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(com.mytm.darrbi.core.common.AppError.Serialization("Malformed RAC response"))
        is ApiResult.Error -> this
        is ApiResult.Failure -> this
    }
}
