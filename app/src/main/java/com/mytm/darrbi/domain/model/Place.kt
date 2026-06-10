package com.mytm.darrbi.domain.model

/** An autocomplete prediction from the Places SDK. */
data class PlaceSuggestion(
    val id: String,
    val primaryText: String,
    val secondaryText: String,
)

/** A resolved place with coordinates (from place details or current location). */
data class PlaceLocation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)
