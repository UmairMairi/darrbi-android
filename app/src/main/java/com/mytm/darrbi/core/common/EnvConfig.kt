package com.mytm.darrbi.core.common

/**
 * Resolved per-flavor environment configuration. Provided from `BuildConfig` in the DI layer so the
 * network layer never reads `BuildConfig` directly. Base URLs come from product flavors
 * (dev/uat/preprod/production); secrets come from gitignored `local.properties`.
 */
data class EnvConfig(
    val mainUrl: String,
    val cmsUrl: String,
    val dashboardUrl: String,
    val rentalUrl: String,
    val rentalToken: String,
    val secretKey: String,
    val isDebug: Boolean,
)
