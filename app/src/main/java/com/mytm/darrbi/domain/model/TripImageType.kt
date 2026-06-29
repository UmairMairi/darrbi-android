package com.mytm.darrbi.domain.model

/**
 * Trip-status code sent as the `type` part when uploading the trip static-map image, mirroring
 * ride-android's `Constants` values (`PATCH /trips/upload-photo/{tripId}`).
 */
object TripImageType {
    const val CREATED = 1
    const val STARTED = 2
    const val DESTINATION_CHANGED = 3
    const val CANCELLED_BY_RIDER = 4
    const val CANCELLED_BY_DRIVER = 5
    const val COMPLETED = 6
}
