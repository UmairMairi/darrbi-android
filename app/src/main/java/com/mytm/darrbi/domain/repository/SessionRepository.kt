package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.domain.model.CaptainDetails
import com.mytm.darrbi.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** Single source of truth for the authenticated session (replaces ride-android's `Singleton`/`PrefManager`). */
interface SessionRepository {
    val isLoggedIn: Flow<Boolean>

    /** Profile of the signed-in user (from verify-OTP); null before login. */
    val user: UserProfile?

    /** Captain details for the current session (set after login for a captain); null otherwise. */
    val captain: CaptainDetails?

    suspend fun saveSession(token: String, userId: String?)

    /** Store the verify-OTP user profile in the current session. */
    fun saveUser(profile: UserProfile)

    /** Store the captain details fetched from `GET /captains` in the current session. */
    fun saveCaptain(details: CaptainDetails)

    suspend fun clear()
}
