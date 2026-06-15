package com.mytm.darrbi

import android.app.Application
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DarrbiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The persisted session is kept across launches (auto-login); it's cleared only on logout.

        // Google Places SDK for location autocomplete via the NEW Places API (the legacy one is sunset).
        // REQUIRES "Places API (New)" enabled on the key's Cloud project (963092244705); otherwise
        // findAutocompletePredictions fails with 9011 (API not enabled).
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.MAPS_API_KEY)
        }
    }
}
