package com.mytm.darrbi.core.common

/** Synchronous read of the current session id for the auth interceptor (backed by a @Volatile cache). */
interface SessionProvider {
    val sessionId: String?
    val userId: String?
}

/** Current user id (exposed separately so feature code can depend on just this). */
interface UserIdProvider {
    val userId: String?
}

/** Synchronous read of the current language tag ("en"/"ar") for the language interceptor. */
interface LanguageProvider {
    val language: String
}

/** Stable device identifier used by auth endpoints (e.g. the verify-otp `deviceId` header). */
interface DeviceInfoProvider {
    val deviceId: String

    /** FCM push token to register with the server; empty when push isn't configured yet. */
    suspend fun fcmToken(): String
}
