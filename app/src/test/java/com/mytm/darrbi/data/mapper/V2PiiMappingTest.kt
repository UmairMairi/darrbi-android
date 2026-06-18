package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.BidDto
import com.mytm.darrbi.data.remote.dto.OpenTripDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the V2 PII profile blocks (guide rev 2026-06-18) deserialize and map correctly: every
 * driver-facing open-trip item carries a nested `rider` block, and every rider-facing bid carries a
 * nested `driver` block (incl. `vehicle`). Uses the guide's exact sample payloads.
 */
class V2PiiMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `open-trip rider PII block maps to the driver-facing card`() {
        // Sample from guide §5.1 (open-trips-list item).
        val payload = """
            {
              "tripId": "1bf9ea24-uuid",
              "cabId": "db8db63e",
              "pickup":  { "latitude": 31.4595, "longitude": 74.2765 },
              "dropoff": { "latitude": 31.5200, "longitude": 74.3500 },
              "tripDistanceKm": 12.51, "pickupDistanceKm": 0.02, "etaToPickupSec": 2,
              "fareRange": { "currency": "SAR", "recommended": 59.16, "min": 47.33, "max": 118.32, "riderOfferedFare": 60 },
              "riderOfferedFare": 60,
              "rider": {
                "riderId": "966110000074",
                "name": "Dalal Al-Harbi",
                "arabicName": "دلال الحربي",
                "profileImage": "https://cdn.example.com/u/dalal.jpg",
                "rating": 4.8,
                "totalReviews": 36
              }
            }
        """.trimIndent()

        val trip = json.decodeFromString(OpenTripDto.serializer(), payload).toDomain()!!
        assertEquals("966110000074", trip.riderId)
        assertEquals("Dalal Al-Harbi", trip.riderName)
        assertEquals("دلال الحربي", trip.riderArabicName)
        assertEquals("https://cdn.example.com/u/dalal.jpg", trip.riderImageUrl)
        assertEquals(4.8, trip.riderRating!!, 0.0001)
        assertEquals(36, trip.riderTotalReviews)
    }

    @Test
    fun `bid driver PII block incl vehicle maps to the rider-facing card`() {
        // Sample from guide §6.2 (first bid).
        val payload = """
            {
              "bidId": "b-1", "driverId": "966220000022", "bidType": 1, "bidFare": 60,
              "currency": "SAR", "status": 1, "etaToPickupSec": 240, "pickupDistanceKm": 1.6, "message": null,
              "driver": {
                "driverId": "966220000022",
                "name": "Nasser Al-Otaibi",
                "arabicName": "ناصر العتيبي",
                "profileImage": "https://cdn.example.com/u/nasser.jpg",
                "mobile": "966553179200",
                "rating": 5,
                "totalReviews": 1,
                "vehicle": { "plateNo": "5848-طنس", "sequenceNo": "963258741", "model": "كامري", "color": "رمادي" }
              }
            }
        """.trimIndent()

        val bid = json.decodeFromString(BidDto.serializer(), payload).toDomain()!!
        assertEquals("Nasser Al-Otaibi", bid.driverName)
        assertEquals("ناصر العتيبي", bid.driverArabicName)
        assertEquals("966553179200", bid.driverMobile)
        assertEquals("https://cdn.example.com/u/nasser.jpg", bid.driverImageUrl)
        assertEquals(5.0, bid.driverRating!!, 0.0001)
        assertEquals(1, bid.driverTotalReviews)
        assertEquals("كامري · رمادي", bid.driverCar)
        assertEquals("5848-طنس", bid.driverPlateNo)
    }

    @Test
    fun `new rider with zero rating shows no rating and null vehicle is tolerated`() {
        // rating/totalReviews 0 for a brand-new rider → treated as "no rating".
        val newRider = """
            { "tripId": "t", "cabId": "c",
              "pickup": { "latitude": 1.0, "longitude": 1.0 }, "dropoff": { "latitude": 2.0, "longitude": 2.0 },
              "rider": { "riderId": "r", "name": "New User", "rating": 0, "totalReviews": 0 } }
        """.trimIndent()
        val trip = json.decodeFromString(OpenTripDto.serializer(), newRider).toDomain()!!
        assertEquals("New User", trip.riderName)
        assertNull(trip.riderRating)

        // vehicle absent → driverCar null, no crash.
        val noVehicle = """
            { "bidId": "b", "driverId": "d", "bidType": 1, "bidFare": 50, "status": 1,
              "driver": { "driverId": "d", "name": "Omar", "profileImage": "", "rating": 4.6, "totalReviews": 210 } }
        """.trimIndent()
        val bid = json.decodeFromString(BidDto.serializer(), noVehicle).toDomain()!!
        assertEquals("Omar", bid.driverName)
        assertNull(bid.driverCar)
        assertNull(bid.driverImageUrl)
        assertEquals(210, bid.driverTotalReviews)
    }
}
