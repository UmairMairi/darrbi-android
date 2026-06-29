package com.mytm.darrbi

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient

@HiltAndroidApp
class DarrbiApplication : Application(), ImageLoaderFactory {

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

    /**
     * App-wide Coil [ImageLoader] used by every `AsyncImage`. It sends an explicit `User-Agent`
     * because some image hosts reject OkHttp's default `okhttp/x.y` agent with HTTP 403 — notably
     * `upload.wikimedia.org` (used in RAC seed data), which made car photos silently fail to load.
     *
     * Uses its OWN OkHttpClient — never the API client — so the auth bearer token and language
     * headers are not leaked to third-party image hosts.
     */
    override fun newImageLoader(): ImageLoader {
        val imageClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", IMAGE_USER_AGENT)
                        .build(),
                )
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .crossfade(true)
            .build()
    }

    private companion object {
        /** Descriptive UA accepted by UA-gating CDNs like Wikimedia (the default `okhttp/…` is blocked). */
        const val IMAGE_USER_AGENT = "Darrbi/1.0 (Android; +https://barq.com)"
    }
}
